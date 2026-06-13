"""Agent 状态对象。

第一版用 dict 风格状态，后续接入 LangGraph 时可以直接迁移为 TypedDict。
"""

from typing import Any, Optional, TypedDict


class AgentState(TypedDict, total=False):
    """贯穿 Router -> Rewrite -> Retriever -> Answer -> Verifier 的状态。"""

    tenant_id: int
    user_id: int
    session_id: int
    message_id: Optional[int]
    kb_id: Optional[int]
    question: str
    trace_id: str
    route_type: str
    rewritten_query: str
    filters: dict[str, Any]
    entities: list[dict[str, Any]]
    evidence_candidates: list[dict[str, Any]]
    top_evidences: list[dict[str, Any]]
    draft_answer: str
    final_answer: str
    risk_flags: list[str]
