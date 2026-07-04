"""AI Service 入口。

这个服务负责真正的 Agent/RAG 编排，Java 后端只需要调用它。
"""

from __future__ import annotations

import logging
import asyncio
import time
import uuid

from fastapi import Request, FastAPI, Response
from fastapi.middleware.cors import CORSMiddleware
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest

from app import __version__ as app_version
from app.agents.rag_agents import run_rag_agent
from app.core.logging_config import configure_logging
from app.core import metrics
from app.core.settings import settings
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
    status_code = 500
    try:
        response = await call_next(request)
        status_code = response.status_code
        response.headers["X-Trace-Id"] = trace_id
        return response
    finally:
        duration_ms = int((time.time() - started_at) * 1000)
        logger.info(
            "http_request_completed traceId=%s method=%s path=%s status=%s durationMs=%s",
            trace_id,
            request.method,
            request.url.path,
            status_code,
            duration_ms,
        )
        endpoint = normalize_endpoint(request.url.path)
        metrics.AI_HTTP_REQUESTS.labels(endpoint, request.method, str(status_code)).inc()
        metrics.AI_HTTP_DURATION.labels(endpoint, request.method).observe(max(0.0, time.time() - started_at))


@app.get("/api/health")
async def health() -> dict[str, str]:
    """健康检查接口。"""

    return {"status": "UP", "service": "ai-service", "version": app_version}


@app.get("/metrics")
async def prometheus_metrics() -> Response:
    """Prometheus 抓取入口。"""

    return Response(content=generate_latest(), media_type=CONTENT_TYPE_LATEST)


@app.post("/api/agent/ask", response_model=AgentAskResponse)
async def ask(request: AgentAskRequest) -> AgentAskResponse:
    """运行企业知识库 RAG Agent。"""

    started_at = metrics.now()
    if settings.portfolio_demo_faults_enabled and "__demo_ai_sleep__" in request.question:
        # 仅本地演示启用：让 Java -> AI service 调用超过 read timeout，验证真实超时告警链路。
        await asyncio.sleep(max(0, settings.portfolio_demo_ai_sleep_ms) / 1000)
    try:
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
    except Exception:
        metrics.AGENT_ASK_TOTAL.labels("error").inc()
        metrics.AGENT_ASK_DURATION.observe(metrics.elapsed_seconds(started_at))
        raise

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

    try:
        response = AgentAskResponse(
            session_id=request.session_id,
            trace_id=final_state["trace_id"],
            answer=final_state["final_answer"],
            citations=citations,
            summary_blocks=final_state.get("summary_blocks", []),
        )
        metrics.AGENT_ASK_TOTAL.labels("success").inc()
        metrics.RAG_ANSWER_CITATION_COUNT.observe(len(citations))
        return response
    finally:
        metrics.AGENT_ASK_DURATION.observe(metrics.elapsed_seconds(started_at))


def normalize_endpoint(path: str) -> str:
    """把 HTTP 路径规整成低基数 endpoint label。"""

    if path == "/api/agent/ask":
        return "/api/agent/ask"
    if path == "/api/health":
        return "/api/health"
    if path == "/metrics":
        return "/metrics"
    return path
