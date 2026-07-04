"""AI Service 入口。

这个服务负责真正的 Agent/RAG 编排，Java 后端只需要调用它。
"""

from __future__ import annotations

import logging
import time
import uuid

from fastapi import Request, FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app import __version__ as app_version
from app.agents.rag_agents import run_rag_agent
from app.core.logging_config import configure_logging
from app.schemas.chat import AgentAskRequest, AgentAskResponse


configure_logging()
logger = logging.getLogger("ai-service")
app = FastAPI(title="AI Agent Service", version=app_version)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.middleware("http")
async def request_logging_middleware(request: Request, call_next):
    """记录 AI 服务入口请求，和 Java 侧通过 X-Trace-Id 串联。"""

    trace_id = request.headers.get("X-Trace-Id") or uuid.uuid4().hex[:16]
    started_at = time.time()
    response = await call_next(request)
    response.headers["X-Trace-Id"] = trace_id
    duration_ms = int((time.time() - started_at) * 1000)
    logger.info(
        "http_request_completed traceId=%s method=%s path=%s status=%s durationMs=%s",
        trace_id,
        request.method,
        request.url.path,
        response.status_code,
        duration_ms,
    )
    return response


@app.get("/api/health")
async def health() -> dict[str, str]:
    """健康检查接口。"""

    return {"status": "UP", "service": "ai-service", "version": app_version}


@app.post("/api/agent/ask", response_model=AgentAskResponse)
async def ask(request: AgentAskRequest) -> AgentAskResponse:
    """运行企业知识库 RAG Agent。"""

    final_state = await run_rag_agent(
        {
            "customer_id": request.customer_id,
            "user_id": request.user_id,
            "session_id": request.session_id,
            "message_id": request.message_id,
            "kb_id": request.kb_id,
            "question": request.question,
            "message_history": request.message_history,
            "summary_blocks": request.summary_blocks,
        }
    )

    citations = [
        {
            "chunk_id": item["chunk_id"],
            "doc_id": item["doc_id"],
            "title": item["title"],
            "score": item.get("score"),
            "text": item["text"],
        }
        for item in final_state.get("top_evidences", [])
    ]

    return AgentAskResponse(
        session_id=request.session_id,
        trace_id=final_state["trace_id"],
        answer=final_state["final_answer"],
        citations=citations,
        summary_blocks=final_state.get("summary_blocks", []),
    )


