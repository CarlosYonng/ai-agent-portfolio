"""Agent 状态对象。

第一版用 dict 风格状态，后续接入 LangGraph 时可以直接迁移为 TypedDict。
"""

from typing import Any, Optional, TypedDict


class AgentState(TypedDict, total=False):
    """贯穿 Agent ReAct 循环的状态。"""

    customer_id: int
    user_id: int
    session_id: int
    message_id: Optional[int]
    kb_id: Optional[int]
    question: str
    trace_id: str
    message_history: list[dict[str, Any]]
    """历史消息，每项 {role, content}"""
    summary_blocks: list[str]
    """分块摘要，每块浓缩 5 轮对话。

    由 build_messages 按需生成，Java 后端负责在请求间持久化透传。
    第 6 轮生成块 0 (R1~R5)，第 11 轮生成块 1 (R6~R10)，以此类推。
    """
    filters: dict[str, Any]
    evidence_candidates: list[dict[str, Any]]
    top_evidences: list[dict[str, Any]]
    final_answer: str
    risk_flags: list[str]
