"""AI Service 入口。

这个服务负责真正的 Agent/RAG 编排，Java 后端只需要调用它。
"""

from __future__ import annotations

from fastapi import FastAPI

from app import __version__ as app_version
from app.agents.incident_agents import run_incident_agent
from app.agents.rag_agents import run_rag_agent
from app.schemas.chat import AgentAskRequest, AgentAskResponse
from app.schemas.incident import IncidentDiagnoseRequest, IncidentDiagnoseResponse


app = FastAPI(title="AI Agent Service", version=app_version)


@app.get("/api/health")
async def health() -> dict[str, str]:
    """健康检查接口。"""

    return {"status": "UP", "service": "ai-service", "version": app_version}


@app.post("/api/agent/ask", response_model=AgentAskResponse)
async def ask(request: AgentAskRequest) -> AgentAskResponse:
    """运行企业知识库 RAG Agent。"""

    final_state = await run_rag_agent(
        {
            "tenant_id": request.tenant_id,
            "user_id": request.user_id,
            "session_id": request.session_id,
            "message_id": request.message_id,
            "kb_id": request.kb_id,
            "question": request.question,
        }
    )

    citations = [
        {
            "chunk_id": item["chunk_id"],
            "doc_id": item["doc_id"],
            "title": item["title"],
            "score": item["score"],
            "preview": item["preview"],
        }
        for item in final_state.get("top_evidences", [])
    ]

    return AgentAskResponse(
        session_id=request.session_id,
        trace_id=final_state["trace_id"],
        answer=final_state["final_answer"],
        citations=citations,
    )


@app.post("/api/incident/diagnose", response_model=IncidentDiagnoseResponse)
async def diagnose_incident(request: IncidentDiagnoseRequest) -> IncidentDiagnoseResponse:
    """运行 Java 微服务故障诊断 Agent。"""

    result = await run_incident_agent(request)
    return IncidentDiagnoseResponse(**result)
