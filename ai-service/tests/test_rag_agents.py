"""RAG Agent 单元测试。

这些测试不依赖 MySQL、Qdrant、Neo4j 或真实大模型，重点验证 Agent 编排逻辑。
"""

from __future__ import annotations

import asyncio

from app.agents import rag_agents


class FakeRetriever:
    """测试用检索器，固定返回一条证据。"""

    async def retrieve(self, query: str, filters: dict[str, object]) -> list[dict[str, object]]:
        return [
            {
                "chunk_id": "chunk_001",
                "doc_id": "doc_001",
                "title": "支付回调说明",
                "score": 0.91,
                "preview": f"query={query}, tenant={filters['tenant_id']}",
            }
        ]


class FakeModelClient:
    """测试用模型客户端，避免调用外部大模型。"""

    async def generate(self, system_prompt: str, user_prompt: str) -> str:
        return "PAY_5001 表示支付回调签名校验失败。"


def test_run_rag_agent_returns_answer_and_evidence(monkeypatch):
    """完整链路应输出答案、traceId 和证据。"""

    monkeypatch.setattr(rag_agents, "hybrid_retriever", FakeRetriever())
    monkeypatch.setattr(rag_agents, "model_client", FakeModelClient())
    monkeypatch.setattr(rag_agents, "log_trace", lambda *args, **kwargs: None)

    state = asyncio.run(
        rag_agents.run_rag_agent(
            {
                "tenant_id": 1,
                "user_id": 1,
                "session_id": 1,
                "message_id": 10,
                "question": "PAY_5001 是什么意思？",
            }
        )
    )

    assert state["trace_id"].startswith("tr_")
    assert state["route_type"] == "knowledge_qa"
    assert state["final_answer"] == "PAY_5001 表示支付回调签名校验失败。"
    assert state["top_evidences"][0]["chunk_id"] == "chunk_001"


def test_verifier_rejects_answer_without_evidence(monkeypatch):
    """没有证据时 Verifier 必须拒答，避免幻觉。"""

    monkeypatch.setattr(rag_agents, "log_trace", lambda *args, **kwargs: None)
    state = asyncio.run(
        rag_agents.verifier_agent(
            {
                "trace_id": "tr_test",
                "tenant_id": 1,
                "message_id": 1,
                "top_evidences": [],
                "risk_flags": [],
            }
        )
    )

    assert "没有检索到足够证据" in state["final_answer"]
    assert "NO_EVIDENCE" in state["risk_flags"]
