"""故障诊断接口 schema。"""

from __future__ import annotations

from typing import Any, Optional

from pydantic import BaseModel, Field


class IncidentDiagnoseRequest(BaseModel):
    """故障诊断请求。"""

    tenant_id: int = 1
    user_id: int = 1
    service: str = "order"
    question: str = Field(min_length=1)
    trace_id: Optional[str] = None
    time_range: str = "last_1h"


class IncidentDiagnoseResponse(BaseModel):
    """故障诊断响应。"""

    trace_id: str
    summary: str
    root_causes: list[dict[str, Any]]
    actions: list[str]
    evidences: list[dict[str, Any]]
