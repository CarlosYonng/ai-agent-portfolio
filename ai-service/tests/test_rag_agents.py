"""RAG Agent 单元测试。

测试 ReAct 循环、消息构建、证据格式化等核心逻辑。
"""

from __future__ import annotations

import asyncio

from app.agents import rag_agents


class FakeRetriever:
    async def retrieve(self, query, filters=None):
        return [
            {"chunk_id": "c1", "doc_id": "d1", "title": "test",
             "score": 0.9, "text": f"query={query}"},
        ]


class FakeEmptyRetriever:
    async def retrieve(self, query, filters=None):
        return []


class FakeModelClient:
    def __init__(self, responses: list):
        self.responses = responses
        self.call_count = 0

    async def generate(self, system_prompt: str, user_prompt: str) -> str:
        return "mock"

    async def chat(self, messages, tools=None):
        from app.core.model_client import ChatResponse
        if self.call_count < len(self.responses):
            resp = self.responses[self.call_count]
            self.call_count += 1
            return resp
        return ChatResponse(content="fallback")


def test_build_messages_includes_system_prompt():
    msgs = asyncio.run(rag_agents.build_messages({
        "message_history": [],
        "question": "什么是知识库？",
    }))
    assert msgs[0]["role"] == "system"
    assert "企业知识库问答助手" in msgs[0]["content"]


def test_build_messages_includes_current_question():
    msgs = asyncio.run(rag_agents.build_messages({
        "message_history": [],
        "question": "什么是知识库？",
    }))
    assert msgs[-1]["role"] == "user"
    assert msgs[-1]["content"] == "什么是知识库？"


def test_build_messages_includes_history():
    msgs = asyncio.run(rag_agents.build_messages({
        "message_history": [
            {"role": "user", "content": "第一轮"},
            {"role": "assistant", "content": "回答一"},
        ],
        "question": "第二轮",
    }))
    assert len(msgs) == 4
    assert msgs[1]["content"] == "第一轮"
    assert msgs[2]["content"] == "回答一"


# ===================================================================
# build_messages 分块摘要测试
# ===================================================================


async def _fake_summarize_history(history, max_chars=600):
    """模拟摘要：用条数和字符数替代真实 LLM 调用。"""
    if not history:
        return None
    user_msgs = [m for m in history if m.get("role") == "user"]
    topics = ", ".join(m["content"][:20] for m in user_msgs[:3])
    return f"用户询问了关于 {topics} 等问题（共 {len(history)} 条消息）"


def _make_history(rounds: int) -> list[dict]:
    """构造 rounds 轮对话历史。"""
    history = []
    for i in range(1, rounds + 1):
        history.append({"role": "user", "content": f"问题{i}"})
        history.append({"role": "assistant", "content": f"答案{i}"})
    return history


def test_build_messages_less_than_5_rounds_no_summary():
    """少于 5 轮 → 无摘要块，全部保留为完整消息。"""
    history = _make_history(4)
    msgs = asyncio.run(rag_agents.build_messages({
        "message_history": history,
        "question": "问题5",
    }))
    # [system] + [8条消息(4轮)] + [question]
    assert len(msgs) == 1 + 8 + 1
    # 不存在摘要块
    for m in msgs:
        assert "摘要" not in m.get("content", "")


def test_build_messages_creates_block_at_round_6(monkeypatch):
    """6 轮对话 → 生成第 1 个摘要块（R1~R5），保留 R6 完整。"""
    monkeypatch.setattr(rag_agents, "summarize_history", _fake_summarize_history)
    history = _make_history(6)

    msgs = asyncio.run(rag_agents.build_messages({
        "message_history": history,
        "question": "问题7",
    }))

    # [system] + [摘要块] + [R2~R6 (10条)] + [question]
    assert len(msgs) == 1 + 1 + 10 + 1
    assert "第 1~5 轮对话摘要" in msgs[1]["content"]


def test_build_messages_no_resummarize_between_6_and_10(monkeypatch):
    """6~10 轮间不触发新的摘要 —— 模拟跨请求的 state 传递。"""
    call_count = [0]

    async def counting_summarize(history, max_chars=600):
        call_count[0] += 1
        return await _fake_summarize_history(history, max_chars)

    monkeypatch.setattr(rag_agents, "summarize_history", counting_summarize)

    # 第 6 轮：应用 state，build_messages 会写入 state["summary_blocks"]
    state = {"message_history": _make_history(6), "summary_blocks": [], "question": "问题7"}
    asyncio.run(rag_agents.build_messages(state))
    assert call_count[0] == 1

    # 第 7 轮：复用同一个 state（summary_blocks 已被上一轮写入），不应重新摘要
    state["message_history"] = _make_history(7)
    state["question"] = "问题8"
    asyncio.run(rag_agents.build_messages(state))
    assert call_count[0] == 1  # 没有新调用

    # 第 10 轮：依然只需要 1 个块
    state["message_history"] = _make_history(10)
    state["question"] = "问题11"
    asyncio.run(rag_agents.build_messages(state))
    assert call_count[0] == 1  # 仍然没有新调用


def test_build_messages_creates_second_block_at_round_11(monkeypatch):
    """11 轮对话 → 生成第 2 个摘要块（R6~R10）。"""
    call_count = [0]

    async def counting_summarize(history, max_chars=600):
        val = call_count[0]
        call_count[0] += 1
        if val == 0:
            return "第一块摘要（R1~R5）"
        elif val == 1:
            return "第二块摘要（R6~R10）"
        return "后续摘要"

    monkeypatch.setattr(rag_agents, "summarize_history", counting_summarize)

    history = _make_history(11)
    msgs = asyncio.run(rag_agents.build_messages({
        "message_history": history,
        "question": "问题12",
    }))

    assert call_count[0] == 2  # 两个块各调一次
    # [system] + [块1] + [块2] + [最近 5 轮完整 (R7~R11 = 10 条)] + [question]
    assert len(msgs) == 1 + 2 + 10 + 1
    assert "第一块摘要" in msgs[1]["content"]
    assert "第二块摘要" in msgs[2]["content"]
    # 最近 5 轮的第一条是 R7 用户消息
    assert msgs[3]["content"] == "问题7"
    assert msgs[3]["role"] == "user"


def test_build_messages_uses_existing_blocks(monkeypatch):
    """已有 summary_blocks 时不应重新生成。"""
    call_count = [0]

    async def counting_summarize(history, max_chars=600):
        call_count[0] += 1
        return f"摘要: {len(history)} 条"

    monkeypatch.setattr(rag_agents, "summarize_history", counting_summarize)

    # 传入已有 1 个块 + 7 轮历史（应有 1 个块）
    history = _make_history(7)
    msgs = asyncio.run(rag_agents.build_messages({
        "message_history": history,
        "summary_blocks": ["已有的摘要（R1~R5）"],
        "question": "问题8",
    }))

    assert call_count[0] == 0  # 没有新调用 summarize_history
    # [system] + [已有摘要块] + [R3~R7 (10条)] + [question]
    assert len(msgs) == 1 + 1 + 10 + 1
    assert "已有的摘要" in msgs[1]["content"]


def test_build_messages_state_is_mutated(monkeypatch):
    """build_messages 应在 state 中写入新的 summary_blocks。"""
    monkeypatch.setattr(rag_agents, "summarize_history", _fake_summarize_history)

    state = {
        "message_history": _make_history(6),
        "summary_blocks": [],
        "question": "问题7",
    }
    asyncio.run(rag_agents.build_messages(state))

    assert "summary_blocks" in state
    assert len(state["summary_blocks"]) == 1
    assert "共 10 条消息" in state["summary_blocks"][0]


def test_build_messages_total_size_compression(monkeypatch):
    """超长消息应触发降级（5 轮 → 3 轮）。"""
    monkeypatch.setattr(rag_agents, "summarize_history", _fake_summarize_history)

    # 构造 6 轮，每轮超长（接近 MAX_FULL_CHARS / 块数）
    history = _make_history(6)
    long_content = "内容。" * 500  # 约 1500 字
    history[0]["content"] = long_content  # 第 1 轮用户消息超长
    history[1]["content"] = long_content  # 第 1 轮助手消息超长

    msgs = asyncio.run(rag_agents.build_messages({
        "message_history": history,
        "summary_blocks": [],
        "question": "问题7",
    }))

    # 摘要块存在
    summary_blocks = [m for m in msgs if "摘要" in m.get("content", "")]
    assert len(summary_blocks) == 1


# ===================================================================
# 快速通道（kb_id 已设置）测试
# ===================================================================


def test_run_rag_agent_fast_path(monkeypatch):
    """快速通道：有 kb_id → 检索后直接纯文本调 LLM，不走 ReAct 循环。"""
    from app.core.model_client import ChatResponse

    fake_client = FakeModelClient([
        ChatResponse(content="基于检索证据的回答。"),
    ])
    monkeypatch.setattr(rag_agents, "model_client", fake_client)
    monkeypatch.setattr(rag_agents, "hybrid_retriever", FakeRetriever())
    monkeypatch.setattr(rag_agents, "log_trace", lambda *a, **kw: None)

    state = asyncio.run(rag_agents.run_rag_agent({
        "customer_id": 1, "user_id": 1, "session_id": 1,
        "kb_id": 1,  # 触发快速通道
        "question": "什么是知识库？",
    }))

    assert "检索证据" in state["final_answer"]
    # 只调了 1 次 LLM（快速通道直接回答），不是 2 次（ReAct 先 tool_call 再回答）
    assert fake_client.call_count == 1


def test_run_rag_agent_fast_path_no_evidence(monkeypatch):
    """快速通道但检索为空 → 直接返回兜底，不走 LLM。"""
    monkeypatch.setattr(rag_agents, "hybrid_retriever", FakeEmptyRetriever())
    monkeypatch.setattr(rag_agents, "log_trace", lambda *a, **kw: None)

    state = asyncio.run(rag_agents.run_rag_agent({
        "customer_id": 1, "user_id": 1, "session_id": 1,
        "kb_id": 1,
        "question": "不存在的资料是什么？",
    }))

    assert state["top_evidences"] == []
    assert "未找到" in state["final_answer"]


def test_format_evidence():
    text = rag_agents.format_evidence([
        {"chunk_id": "c1", "title": "文档1", "text": "内容1", "score": 0.95},
    ])
    assert "文档1" in text


def test_format_evidence_empty():
    text = rag_agents.format_evidence([])
    assert "未检索到" in text


def test_run_rag_agent_with_tool_call(monkeypatch):
    from app.core.model_client import ChatResponse
    monkeypatch.setattr(rag_agents, "model_client", FakeModelClient([
        ChatResponse(tool_call=("retrieve_knowledge", {"query": "知识库"})),
        ChatResponse(content="知识库是用于存储知识的系统。"),
    ]))
    monkeypatch.setattr(rag_agents, "log_trace", lambda *a, **kw: None)
    monkeypatch.setattr(rag_agents, "hybrid_retriever", FakeRetriever())

    state = asyncio.run(rag_agents.run_rag_agent({
        "customer_id": 1, "user_id": 1, "session_id": 1,
        "question": "什么是知识库？",
    }))
    assert state["trace_id"].startswith("tr_")
    assert "知识库" in state["final_answer"]


def test_run_rag_agent_returns_no_evidence_answer(monkeypatch):
    from app.core.model_client import ChatResponse
    monkeypatch.setattr(rag_agents, "model_client", FakeModelClient([
        ChatResponse(tool_call=("retrieve_knowledge", {"query": "不存在的资料"})),
    ]))
    monkeypatch.setattr(rag_agents, "log_trace", lambda *a, **kw: None)
    monkeypatch.setattr(rag_agents, "hybrid_retriever", FakeEmptyRetriever())

    state = asyncio.run(rag_agents.run_rag_agent({
        "customer_id": 1, "user_id": 1, "session_id": 1,
        "question": "不存在的资料是什么？",
    }))

    assert state["top_evidences"] == []
    assert "未找到" in state["final_answer"]
    assert any(item["stage"] == "no_evidence" for item in state["status_events"])


def test_run_rag_agent_direct_answer(monkeypatch):
    from app.core.model_client import ChatResponse
    monkeypatch.setattr(rag_agents, "model_client", FakeModelClient([
        ChatResponse(content="直接回答。"),
    ]))
    monkeypatch.setattr(rag_agents, "log_trace", lambda *a, **kw: None)

    state = asyncio.run(rag_agents.run_rag_agent({
        "customer_id": 1, "user_id": 1, "session_id": 1,
        "question": "测试",
    }))
    assert state["final_answer"] == "直接回答。"
