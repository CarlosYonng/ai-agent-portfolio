"""RAG 多 Agent 编排。

这个文件是项目 A 的核心。虽然第一版没有直接引入 LangGraph，
但每个函数已经按照 Agent 节点拆开，后续迁移到状态图会很自然。
"""

from __future__ import annotations

import time
import uuid

from app.agents.state import AgentState
from app.core.model_client import model_client
from app.core.trace_store import log_trace
from app.retrieval.hybrid_retriever import hybrid_retriever


async def router_agent(state: AgentState) -> AgentState:
    """判断用户问题类型。"""

    started_at = time.time()
    question = state["question"]
    # 路由节点只做轻量判断，避免一上来就消耗大模型 token。
    # 真实项目可以把这里升级为“规则 + 小模型分类 + 置信度阈值”的组合。
    if any(word in question.lower() for word in ["error", "异常", "报错", "trace"]):
        state["route_type"] = "incident_or_error"
    elif any(word in question for word in ["接口", "API", "错误码"]):
        state["route_type"] = "technical_doc"
    else:
        state["route_type"] = "knowledge_qa"
    state.setdefault("risk_flags", [])
    log_trace(
        state["trace_id"],
        state["tenant_id"],
        state.get("message_id"),
        "RouterAgent",
        question,
        state["route_type"],
        started_at,
    )
    return state


async def rewrite_agent(state: AgentState) -> AgentState:
    """把原始问题改写为适合检索的 query。"""

    started_at = time.time()
    question = state["question"].strip()
    # 第一版先做简单规范化；后续可以调用 LLM 输出 query/entities/filters。
    # 面试时可以说明：改写节点的价值是把口语问题转成检索友好的关键词和过滤条件。
    state["rewritten_query"] = question.replace("？", "").replace("?", "")
    state["filters"] = {
        "tenant_id": state["tenant_id"],
        "route_type": state["route_type"],
    }
    state["entities"] = []
    log_trace(
        state["trace_id"],
        state["tenant_id"],
        state.get("message_id"),
        "RewriteAgent",
        question,
        state["rewritten_query"],
        started_at,
        {"filters": state["filters"]},
    )
    return state


async def retriever_agent(state: AgentState) -> AgentState:
    """执行混合检索并写入候选证据。"""

    started_at = time.time()
    candidates = await hybrid_retriever.retrieve(state["rewritten_query"], state["filters"])
    state["evidence_candidates"] = candidates
    # 第一版没有 reranker，直接取 top evidences。
    # 后续可加入 bge-reranker 或 LLM rerank，将向量/图谱/关键词结果统一重排。
    state["top_evidences"] = candidates[:5]
    log_trace(
        state["trace_id"],
        state["tenant_id"],
        state.get("message_id"),
        "RetrieverAgent",
        state["rewritten_query"],
        f"hits={len(candidates)}",
        started_at,
        {"top_ids": [item.get("chunk_id") for item in state["top_evidences"]]},
    )
    return state


async def answer_agent(state: AgentState) -> AgentState:
    """基于证据生成答案草稿。"""

    started_at = time.time()
    evidence_text = "\n".join(
        f"[{idx + 1}] {item['title']}: {item['preview']}"
        for idx, item in enumerate(state.get("top_evidences", []))
    )
    # 这里把证据显式塞进 prompt，强制答案基于检索结果生成。
    # citations 会由 API 单独返回，前端可以把答案和引用分开展示。
    system_prompt = "你是企业知识库问答助手，必须基于证据回答，并在答案中提示引用来源。"
    user_prompt = f"问题：{state['question']}\n\n证据：\n{evidence_text}\n\n请给出简洁、可执行的回答。"
    state["draft_answer"] = await model_client.generate(system_prompt, user_prompt)
    log_trace(
        state["trace_id"],
        state["tenant_id"],
        state.get("message_id"),
        "AnswerAgent",
        f"evidence_count={len(state.get('top_evidences', []))}",
        state["draft_answer"][:200],
        started_at,
    )
    return state


async def verifier_agent(state: AgentState) -> AgentState:
    """校验答案是否有证据支撑。"""

    started_at = time.time()
    evidences = state.get("top_evidences", [])
    if not evidences:
        # Verifier 是防幻觉的最后一道门：没有证据时宁可拒答，也不要编答案。
        state["final_answer"] = "没有检索到足够证据，建议补充文档或换一种问法。"
        state.setdefault("risk_flags", []).append("NO_EVIDENCE")
        log_trace(
            state["trace_id"],
            state["tenant_id"],
            state.get("message_id"),
            "VerifierAgent",
            "evidence_count=0",
            "reject:NO_EVIDENCE",
            started_at,
        )
        return state

    state["final_answer"] = state["draft_answer"]
    log_trace(
        state["trace_id"],
        state["tenant_id"],
        state.get("message_id"),
        "VerifierAgent",
        f"evidence_count={len(evidences)}",
        "pass",
        started_at,
    )
    return state


async def run_rag_agent(initial_state: AgentState) -> AgentState:
    """按固定顺序运行 RAG Agent 链路。"""

    initial_state["trace_id"] = f"tr_{int(time.time())}_{uuid.uuid4().hex[:8]}"
    # 固定顺序适合第一版工程落地：可测试、可追踪、便于讲清楚每个节点职责。
    # 当业务分支变多时，可以把这些节点迁移到 LangGraph 状态图。
    state = await router_agent(initial_state)
    state = await rewrite_agent(state)
    state = await retriever_agent(state)
    state = await answer_agent(state)
    state = await verifier_agent(state)
    return state
