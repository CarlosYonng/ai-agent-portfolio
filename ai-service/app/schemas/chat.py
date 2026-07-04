"""聊天接口 schema。

Pydantic schema 负责约束输入输出，这样 Java 和 Python 两边的契约更清楚。
"""

from typing import Any, Optional

from pydantic import BaseModel, Field


class AgentAskRequest(BaseModel):
    """Java 后端发送给 AI 服务的请求。"""

    customer_id: int
    user_id: int
    session_id: int
    message_id: Optional[int] = None
    kb_id: Optional[int] = None
    question: str = Field(min_length=1)
    message_history: list[dict[str, Any]] = Field(default_factory=list)
    """历史消息列表，每项 {role, content}，用于多轮对话上下文。"""
    summary_blocks: list[str] = Field(default_factory=list)
    """分块摘要缓存，由 Java 后端在请求间持久化透传。

    每块浓缩 5 轮对话，build_messages 按需追加新块或读取已有块。
    """


class Citation(BaseModel):
    """答案引用的证据片段。"""

    chunk_id: str
    doc_id: str
    title: str
    score: Optional[float] = None
    text: str


class AgentAskResponse(BaseModel):
    """AI 服务返回给 Java 后端的响应。"""

    session_id: int
    trace_id: str
    answer: str
    citations: list[dict[str, Any]]
    summary_blocks: list[str] = Field(default_factory=list)
    """更新后的分块摘要，Java 后端应保存并在下次请求时传回。"""
