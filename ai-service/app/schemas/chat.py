"""聊天接口 schema。

Pydantic schema 负责约束输入输出，这样 Java 和 Python 两边的契约更清楚。
"""

from typing import Any, Optional

from pydantic import BaseModel, Field


class AgentAskRequest(BaseModel):
    """Java 后端发送给 AI 服务的请求。"""

    tenant_id: int
    user_id: int
    session_id: int
    message_id: Optional[int] = None
    kb_id: Optional[int] = None
    question: str = Field(min_length=1)


class Citation(BaseModel):
    """答案引用的证据片段。"""

    chunk_id: str
    doc_id: str
    title: str
    score: float
    preview: str


class AgentAskResponse(BaseModel):
    """AI 服务返回给 Java 后端的响应。"""

    session_id: int
    trace_id: str
    answer: str
    citations: list[dict[str, Any]]
