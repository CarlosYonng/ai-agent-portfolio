"""LLM 驱动的 RAG Agent（ReAct 模式）。

工作方式不再是固定的 Router → Rewrite → Retrieve → Answer → Verify 流水线，
而是让 LLM 在循环中自主决策：

  系统提示（含可用工具定义）→ LLM 决定调用工具还是输出最终答案
  → 调工具则把结果追加到上下文，继续循环
  → 输出答案则结束

每一轮 LLM 交互都通过 trace_store 记录为 AgentTrace 节点。
"""

from __future__ import annotations

import time
import uuid
from typing import Any

from app.agents.state import AgentState
from app.core.model_client import model_client
from app.core.trace_store import log_trace
from app.prompts.prompts import load_prompt
from app.retrieval.hybrid_retriever import hybrid_retriever

# ===================================================================
# 工具定义（OpenAI-compatible function calling 格式）
# ===================================================================

KNOWLEDGE_RETRIEVAL_TOOL = {
    "type": "function",
    "function": {
        "name": "retrieve_knowledge",
        "description": "从企业知识库中检索与问题相关的文档片段。当需要基于文档内容回答时调用此工具。",
        "parameters": {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "检索关键词或自然语言查询，应与用户问题高度相关",
                }
            },
            "required": ["query"],
        },
    },
}

AVAILABLE_TOOLS = [KNOWLEDGE_RETRIEVAL_TOOL]

# ===================================================================
# 系统提示
# ===================================================================

DEFAULT_SYSTEM_PROMPT = """你是企业知识库问答助手。你的工作流程：

1. 收到用户问题后，先检查对话历史是否已经包含足够的信息来回答。
2. 如果问题涉及历史对话内容，直接根据对话历史回答，不需要检索知识库。
3. 如果问题需要企业知识库中的信息，调用 `retrieve_knowledge` 工具进行检索。
4. 阅读检索证据后，**用自己的话重新组织**，以自然、清晰的口语化中文回答。
5. 如果检索结果不足以回答问题，如实告知用户"知识库中没有相关文档"。

## 回答格式（硬性要求）

你必须以**纯文本、通顺的自然语言**输出。证据原文中的 markdown 格式符号**严禁出现在答案里**：

原文里的符号 → 你输出时应该：
- `## 标题` → 直接说"关于 X"或"X 方面"
- `- 项目` 或 `* 项目` → 直接说"X 是……"、"X 为……"
- `**加粗**` → 不加任何符号，直接说内容
- `| 表格 |` → 用文字叙述："X 是 A，Y 是 B"
- `[链接](url)` → 直接说链接文字，不输出链接格式

正确例子：
- 原文："## 按流量计费\n- 单价：0.24 元/GB\n- 适用场景：业务流量波动较大"
- 你应该回答："按流量计费单价为 0.24 元每 GB，适用于业务流量波动较大的场景。"
- 不应该回答："# 按流量计费\n- 单价：0.24 元/GB" ❌

正确例子 2：
- 原文："**加速服务范围**\n- 静态加速：图片\n- 动态加速：API 接口"
- 你应该回答："加速服务范围包括静态加速（图片）和动态加速（API 接口）。"

## 核心规则：防幻觉

- **只基于证据回答。** 你的回答必须严格限于检索到的证据文字。如果某个细节在证据中找不到，不能使用你自己的知识来补充，只能如实说"知识库中没有提到"。
- **无法确认就说不知道。** 如果证据中部分回答了问题但仍有不清楚之处，只回答证据支持的部分，对不清楚的部分明确说明"未找到相关说明"。
- 引用证据时简明提及来源文档标题即可，不需要逐条罗列证据编号。
- 多轮对话中，根据历史消息理解用户的指代和上下文。回答历史相关问题时不要重复对话内容。"""

SYSTEM_PROMPT = load_prompt("rag_system", DEFAULT_SYSTEM_PROMPT)


MAX_FULL_ROUNDS = 5       # 正常保留 5 轮完整
MAX_FULL_CHARS = 6000      # 完整消息总字符上限（约 3000 tokens）
SUMMARY_CHARS = 600        # 每块摘要字符上限
SUMMARY_BLOCK_ROUNDS = 5  # 每个摘要块覆盖的对话轮数

# 证据筛选参数
# 策略说明：
#   当前链路：Qdrant top 8 → 阈值过滤 → top 4 → LLM
#   阈值 0.15 用于挡住余弦相似度 < 0.15 的噪音（约等于向量夹角 > 82°，基本正交无关）
#   top_k=4 是 LLM 上下文窗口和证据充分性的平衡，前端也展示 4 条
# 后续优化：
#   加入 reranker 后，Qdrant limit 提到 20+，阈值可以提高到 0.3~0.5，
#   因为 reranker 的 cross-encoder 打分比 bi-encoder 余弦距离更可靠
EVIDENCE_TOP_K = 4         # 最终给 LLM 看的证据条数
MIN_EVIDENCE_SCORE = 0.15  # 证据最低相关度阈值（余弦相似度），低于此值不喂给 LLM

SUMMARIZE_PROMPT = """请将以下多轮对话压缩为一段精炼的摘要，保留关键信息（用户关心的主题、已经确认的事实、未解决的问题）。

对话内容：
{history_text}

摘要要求：
- {max_chars} 字以内
- 只保留事实性信息
- 忽略礼貌用语和重复内容
- 用第三人称概括"""


async def summarize_history(history: list[dict[str, Any]], max_chars: int = SUMMARY_CHARS) -> str | None:
    if not history:
        return None
    lines = []
    for msg in history:
        role = "用户" if msg.get("role") == "user" else "助手"
        content = msg.get("content", "")
        lines.append(f"{role}: {content[:200]}")
    prompt = SUMMARIZE_PROMPT.format(history_text="\n".join(lines), max_chars=max_chars)
    try:
        summary = await model_client.generate("你是一个对话摘要助手。", prompt)
        s = summary.strip() if summary else ""
        return s[:max_chars * 2] if s else None
    except Exception:
        return None


def content_length(messages: list[dict[str, Any]]) -> int:
    """计算消息列表的总字符数，用于判断是否需要进一步压缩。"""
    return sum(len(m.get("content", "")) for m in messages)


async def build_messages(state: AgentState) -> list[dict[str, Any]]:
    """构造 LLM 消息列表。

    三级压缩策略（从最详细到最精简）：

    ① 分块摘要 + 5 轮完整          ← 正常
      每 5 轮触发一次摘要，将新 5 轮的原始消息浓缩为独立块。
      所有块按顺序放在 system 后、最近对话前。
      LLM 看到：[system] + [摘要块1(R1~R5)] + [摘要块2(R6~R10)] + … + [最近 N 轮完整]

    ② 分块摘要 + 3/1 轮完整        ← 单轮内容特别长
      收缩完整保留的窗口，摘要块不变。

    ③ 仅摘要（全量压缩至 300 字）   ← 极端情况
    """
    messages: list[dict[str, Any]] = []
    messages.append({"role": "system", "content": SYSTEM_PROMPT})

    history = state.get("message_history", [])
    summary_blocks: list[str] = state.get("summary_blocks", [])

    # ── 第 1 层：按需追加新的分块摘要 ──────────────────────────────
    # 轮数 = 消息数 / 2，每 5 轮出一个摘要块
    # 第 6 轮 → 1 块 (R1~R5)、第 11 轮 → 2 块 (R1~R5, R6~R10)……
    round_count = len(history) // 2
    expected_blocks = max(0, round_count - 1) // SUMMARY_BLOCK_ROUNDS

    while len(summary_blocks) < expected_blocks:
        block_idx = len(summary_blocks)  # 当前要生成的块索引（0 起）
        start = block_idx * SUMMARY_BLOCK_ROUNDS * 2
        end = start + SUMMARY_BLOCK_ROUNDS * 2
        to_summarize = history[start:end]
        summary = await summarize_history(to_summarize)
        summary_blocks.append(summary or "")

    # 将更新后的块持久化回 state（Java 后端在请求间透传）
    state["summary_blocks"] = summary_blocks

    # ── 放入所有摘要块 ──────────────────────────────────────────────
    for i, block in enumerate(summary_blocks):
        if block:
            start_round = i * SUMMARY_BLOCK_ROUNDS + 1
            end_round = (i + 1) * SUMMARY_BLOCK_ROUNDS
            messages.append({
                "role": "system",
                "content": f"以下为第 {start_round}~{end_round} 轮对话摘要：{block}",
            })

    # ── 第 2~3 层：放入最近 N 轮完整消息，超限则降级窗口 ────────────
    # 按需尝试保留的轮数：5 → 3 → 1 → 0
    for rounds in [MAX_FULL_ROUNDS, 3, 1, 0]:
        max_msgs = rounds * 2

        if len(history) <= max_msgs:
            # 历史不超过限制，直接用全部
            for msg in history:
                messages.append({"role": msg.get("role", "user"), "content": msg.get("content", "")})
            break

        if max_msgs == 0:
            # 仅摘要模式：所有历史全部压缩为一段 300 字摘要
            full_summary = await summarize_history(history, max_chars=300)
            if full_summary:
                messages.append({
                    "role": "system",
                    "content": f"以下为历史对话摘要：{full_summary}",
                })
            break

        # 取最近 N 轮
        recent = list(history[-max_msgs:])
        # 估算总大小 = 摘要块 + 最近完整轮
        summary_chars = sum(len(b) for b in summary_blocks if b)
        total_est = summary_chars + content_length(recent)
        if total_est < MAX_FULL_CHARS:
            for msg in recent:
                messages.append({"role": msg.get("role", "user"), "content": msg.get("content", "")})
            break
        # 超长了 → 继续循环，尝试更少轮数（摘要块不动）

    messages.append({"role": "user", "content": state["question"]})
    return messages


def format_evidence(results: list[dict[str, Any]]) -> str:
    """将检索结果格式化为 LLM 可读的上下文。"""
    if not results:
        return "未检索到相关文档。"

    parts = []
    for i, item in enumerate(results[:8], 1):
        title = item.get("title", "未知来源")
        text_content = item.get("text", "")
        parts.append(f"[{i}] 标题: {title}\n    内容: {text_content}")
    return "\n\n".join(parts)


def clean_answer(text: str) -> str:
    """清理 LLM 回答中的 markdown 格式符号，确保输出纯文本。

    作为 prompt 的安全网：即使 LLM 没完全遵守格式要求，后端也做最终清理。
    """
    import re
    # 去掉 markdown 标题符号
    text = re.sub(r'^#{1,6}\s+', '', text, flags=re.MULTILINE)
    # 去掉列表符号行首
    text = re.sub(r'^[\s]*[-*+]\s+', '', text, flags=re.MULTILINE)
    # 去掉数字列表行首
    text = re.sub(r'^\s*\d+[.、]\s+', '', text, flags=re.MULTILINE)
    # 去掉加粗/斜体
    text = re.sub(r'\*{1,3}([^*]+)\*{1,3}', r'\1', text)
    # 去掉行内代码
    text = re.sub(r'`([^`]+)`', r'\1', text)
    # 去掉链接格式，保留文字
    text = re.sub(r'\[([^\]]+)\]\([^)]+\)', r'\1', text)
    # 去掉表格分隔线
    text = re.sub(r'^[\s\|:-\|]+$', '', text, flags=re.MULTILINE)
    # 合并多余空行
    text = re.sub(r'\n{3,}', '\n\n', text)
    return text.strip()


async def run_rag_agent(initial_state: AgentState) -> AgentState:
    """运行 LLM 驱动的 RAG Agent（ReAct 模式）。

    循环过程：
      1. 构造 messages（系统提示 + 历史 + 当前问题 + 工具结果上下文）
      2. 调用 LLM
      3. 如果 LLM 调用了 retrieve_knowledge → 执行混合检索 → 追加结果到上下文 → 回到 1
      4. 如果 LLM 输出答案 → 设置为 final_answer → 结束循环
    """

    question = (initial_state.get("question") or "").strip()
    initial_state["question"] = question
    initial_state["trace_id"] = f"tr_{int(time.time())}_{uuid.uuid4().hex[:8]}"
    state: AgentState = dict(initial_state)
    state["status_events"] = []

    def status(stage: str, message: str) -> None:
        state["status_events"].append({"stage": stage, "message": message, "at": time.time()})

    if not question:
        status("guard", "问题为空，已跳过 RAG 检索")
        state["final_answer"] = "请输入需要查询的问题。"
        state["top_evidences"] = []
        return state

    status("prepare", "正在整理对话上下文")
    prepare_started = time.time()
    messages = await build_messages(state)
    log_trace(state["trace_id"], state.get("customer_id"), state.get("message_id"),
              "PrepareAgent", f"kb_id={state.get('kb_id')} rounds={len(state.get('summary_blocks', []))}",
              f"history_rounds={len(state.get('message_history', [])) // 2}", prepare_started)

    # ── 快速通道（省掉一次 LLM 工具调用决策） ──────────────────────────
    # 知识库模式下（kb_id 已设置），直接检索后一轮 LLM 回答，
    # 不需要 LLM 先调用 retrieve_knowledge 再回答。
    if state.get("kb_id") is not None:
        started_at = time.time()
        status("retrieve", f"检索知识库：{question[:80]}")
        filters: dict[str, Any] = {"kb_id": state["kb_id"], "customer_id": state.get("customer_id")}
        candidates = await hybrid_retriever.retrieve(question, filters)
        state["evidence_candidates"] = candidates
        top = [
            c for c in candidates
            if c.get("source") == "neo4j" or c.get("score", 0) >= MIN_EVIDENCE_SCORE
        ][:EVIDENCE_TOP_K]
        state["top_evidences"] = top
        log_trace(state["trace_id"], state.get("customer_id"), state.get("message_id"),
                  "RetrieveAgent", question, f"hits={len(candidates)}", started_at,
                  {"top_citations": [
                      {"title": item.get("title"), "source": item.get("source"), "score": item.get("score")}
                      for item in top
                  ]})
        if not top:
            status("no_evidence", "知识库没有命中可引用证据")
            state["final_answer"] = f'在知识库中未找到与“{question}”相关的文档，无法基于已有文档给出回答。'
            log_trace(state["trace_id"], state.get("customer_id"), state.get("message_id"),
                      "AnswerAgent", "no_evidence", state["final_answer"][:200], time.time())
            return state
        # 把证据追加到最后一轮用户消息中
        evidence_text = format_evidence(top)
        messages[-1]["content"] += f"\n\n以下为知识库中检索到的相关参考内容：\n{evidence_text}"

        # 快速通道：证据已就位，LLM 直接回答，不传 tools，不走 ReAct 循环
        answer_started_at = time.time()
        status("reasoning", "基于检索证据生成回答")
        response = await model_client.chat(messages, tools=None)
        state["final_answer"] = clean_answer(response.content or "")
        status("answer", "已生成最终答案")
        log_trace(
            state["trace_id"],
            state.get("customer_id"),
            state.get("message_id"),
            "AnswerAgent",
            "fast_path",
            state["final_answer"][:200],
            answer_started_at,
        )
        return state

    max_rounds = 6  # 最多 6 轮 tool-use，防死循环
    round_num = 0

    while round_num < max_rounds:
        round_num += 1
        started_at = time.time()
        status("reasoning", f"第 {round_num} 轮 Agent 推理")

        response = await model_client.chat(messages, tools=AVAILABLE_TOOLS)

        if response.tool_call:
            tool_name, tool_args = response.tool_call

            if tool_name == "retrieve_knowledge":
                query = tool_args.get("query", state["question"])
                status("retrieve", f"检索知识库：{query[:80]}")
                filters: dict[str, Any] = {}
                if state.get("kb_id") is not None:
                    filters["kb_id"] = state["kb_id"]
                filters["customer_id"] = state.get("customer_id")

                candidates = await hybrid_retriever.retrieve(query, filters)
                state["evidence_candidates"] = candidates
                # 阈值过滤：低于 MIN_EVIDENCE_SCORE 的噪音不喂给 LLM；图谱精确匹配不适用阈值
                top = [
                    c for c in candidates
                    if c.get("source") == "neo4j" or c.get("score", 0) >= MIN_EVIDENCE_SCORE
                ][:EVIDENCE_TOP_K]
                state["top_evidences"] = top

                # Trace 记录
                log_trace(
                    state["trace_id"],
                    state.get("customer_id"),
                    state.get("message_id"),
                    f"RetrieveAgent",
                    query,
                    f"hits={len(candidates)}",
                    started_at,
                    {"top_citations": [
                        {"title": item.get("title"), "source": item.get("source"), "score": item.get("score")}
                        for item in top
                    ]},
                )

                if not top:
                    status("no_evidence", "知识库没有命中可引用证据")
                    if state.get("kb_id"):
                        state["final_answer"] = (
                            f'在知识库中未找到与"{query}"相关的文档，'
                            "无法基于已有文档给出回答。"
                        )
                    else:
                        state["final_answer"] = (
                            f'当前未指定具体知识库，系统在所有知识库中均未找到与"{query}"相关的文档。'
                        )
                    log_trace(
                        state["trace_id"],
                        state.get("customer_id"),
                        state.get("message_id"),
                        "AnswerAgent",
                        "no_evidence",
                        state["final_answer"][:200],
                        time.time(),
                        {"reason": "retrieval_empty", "query": query},
                    )
                    break

                # 把检索结果追加到上下文，让 LLM 在下一轮看到
                evidence_text = format_evidence(top)
                messages.append({
                    "role": "tool",
                    "content": evidence_text,
                    "tool_call_id": tool_name,
                })
            else:
                # 未知工具，告诉 LLM
                messages.append({
                    "role": "tool",
                    "content": f"未知工具: {tool_name}",
                    "tool_call_id": tool_name,
                })
        else:
            # LLM 输出最终答案（经 clean_answer 清理 markdown 符号）
            state["final_answer"] = clean_answer(response.content or "")
            status("answer", "已生成最终答案")
            log_trace(
                state["trace_id"],
                state.get("customer_id"),
                state.get("message_id"),
                f"AnswerAgent",
                f"rounds={round_num}",
                state["final_answer"][:200],
                started_at,
            )
            break

    # 防死循环兜底：循环用尽但 LLM 仍未输出答案
    if not state.get("final_answer"):
        state["final_answer"] = "抱歉，Agent 处理超时，请稍后重试。"
        status("fallback", "Agent 超过最大轮次，已返回兜底答案")

    return state

