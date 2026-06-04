"""MCP 风格工具服务。

这个服务把日志、代码、工单、报告能力包装成工具接口。
真实 MCP Server 后续可以在这些工具函数外层适配 MCP 协议。
"""

from __future__ import annotations

import os
from datetime import datetime
from pathlib import Path
from typing import Optional
from urllib.parse import urlparse

from fastapi import FastAPI
from dotenv import load_dotenv
from pydantic import BaseModel


load_dotenv(Path(__file__).resolve().parents[2] / ".env")

app = FastAPI(title="MCP Style Tool Server", version="0.1.0")


def query_mysql(sql: str, params: tuple[object, ...]) -> list[dict[str, object]]:
    """执行 MySQL 查询。

    数据库不可用时返回空列表，工具接口会自动降级到 mock 数据。
    """

    try:
        import pymysql
    except ImportError:
        return []

    dsn = os.getenv("MYSQL_DSN", "mysql://agent:agent123@localhost:3306/agentdb")
    parsed = urlparse(dsn)
    try:
        conn = pymysql.connect(
            host=parsed.hostname or "localhost",
            port=parsed.port or 3306,
            user=parsed.username or "agent",
            password=parsed.password or "agent123",
            database=(parsed.path or "/agentdb").lstrip("/"),
            charset="utf8mb4",
            cursorclass=pymysql.cursors.DictCursor,
        )
        try:
            with conn.cursor() as cur:
                cur.execute(sql, params)
                return [dict(row) for row in cur.fetchall()]
        finally:
            conn.close()
    except Exception:
        return []


class SearchLogsRequest(BaseModel):
    """日志查询请求。"""

    service: str
    time_range: str = "last_1h"
    trace_id: Optional[str] = None
    level: Optional[str] = None


class SearchCodeRequest(BaseModel):
    """代码搜索请求。"""

    query: str
    service: str = "order"
    file_type: Optional[str] = None


class SearchTicketsRequest(BaseModel):
    """历史工单查询请求。"""

    symptom: str
    service: str = "order"


class ReportRequest(BaseModel):
    """故障报告生成请求。"""

    incident_id: str
    summary: str
    evidences: list[str]


@app.get("/api/health")
async def health() -> dict[str, str]:
    """健康检查接口。"""

    return {"status": "UP", "service": "mcp-server"}


@app.get("/api/tools")
async def list_tools() -> dict[str, list[dict[str, str]]]:
    """列出可用工具，便于 Agent 动态发现。"""

    return {
        "tools": [
            {"name": "search_logs", "description": "按 traceId、服务名和时间范围检索日志"},
            {"name": "search_code", "description": "按自然语言查询相关 Java 代码片段"},
            {"name": "search_tickets", "description": "检索相似历史故障工单"},
            {"name": "generate_report", "description": "根据证据生成故障复盘报告"},
        ]
    }


@app.post("/api/tools/search_logs")
async def search_logs(request: SearchLogsRequest) -> dict[str, object]:
    """检索日志。

    第一版返回 mock 数据，后续可以接 MySQL obs_log_event 或 Elasticsearch。
    """

    rows = query_mysql(
        """
        select trace_id, level, endpoint, exception_type, message, occurred_at
        from obs_log_event
        where trace_id = coalesce(%s, trace_id)
          and level = coalesce(%s, level)
        order by occurred_at desc
        limit 20
        """,
        (request.trace_id, request.level),
    )
    if rows:
        return {"tool": "search_logs", "query": request.model_dump(), "results": rows}

    return {
        "tool": "search_logs",
        "query": request.model_dump(),
        "results": [
            {
                "trace_id": request.trace_id or "demo-trace-001",
                "service": request.service,
                "level": request.level or "ERROR",
                "message": "NullPointerException at OrderCreateService.createOrder",
                "occurred_at": datetime.now().isoformat(),
            }
        ],
    }


@app.post("/api/tools/search_code")
async def search_code(request: SearchCodeRequest) -> dict[str, object]:
    """检索代码片段。

    第一版从本地样例目录做简单字符串提示，后续可接 code_symbols 向量集合。
    """

    rows = query_mysql(
        """
        select file_path, symbol_name as symbol, content, 0.60 as score
        from code_symbol
        where service_name = %s
          and (
            match(file_path, symbol_name, content) against (%s in natural language mode)
            or content like %s
            or file_path like %s
          )
        limit 10
        """,
        (request.service, request.query, f"%{request.query}%", f"%{request.query}%"),
    )
    if rows:
        # 代码内容可能较长，工具返回时只给预览，避免塞爆 Agent 上下文。
        for row in rows:
            row["preview"] = str(row.pop("content", ""))[:500]
        return {"tool": "search_code", "query": request.model_dump(), "results": rows}

    return {
        "tool": "search_code",
        "query": request.model_dump(),
        "results": [
            {
                "file_path": "OrderCreateService.java",
                "symbol": "createOrder",
                "preview": "createOrder 方法需要校验 couponId 是否为空，避免 NPE。",
                "score": 0.78,
            }
        ],
    }


@app.post("/api/tools/search_tickets")
async def search_tickets(request: SearchTicketsRequest) -> dict[str, object]:
    """检索相似历史工单。"""

    rows = query_mysql(
        """
        select id as ticket_id, symptom, root_cause, solution, severity, created_at
        from incident_ticket
        where service_name = %s
          and (symptom like %s or root_cause like %s or solution like %s)
        order by created_at desc
        limit 10
        """,
        (request.service, f"%{request.symptom}%", f"%{request.symptom}%", f"%{request.symptom}%"),
    )
    if rows:
        return {"tool": "search_tickets", "query": request.model_dump(), "results": rows}

    return {
        "tool": "search_tickets",
        "query": request.model_dump(),
        "results": [
            {
                "ticket_id": "INC-2026-001",
                "symptom": "订单创建接口 500，异常栈出现 NullPointerException。",
                "root_cause": "优惠券 ID 为空时没有做防御性校验。",
                "solution": "增加空值校验，并在无优惠券场景跳过优惠券服务调用。",
                "score": 0.81,
            }
        ],
    }


@app.post("/api/tools/generate_report")
async def generate_report(request: ReportRequest) -> dict[str, object]:
    """生成故障报告。

    报告先写到容器内 /tmp，真实项目可写入数据库或对象存储。
    """

    report = (
        f"# Incident Report {request.incident_id}\n\n"
        f"## Summary\n{request.summary}\n\n"
        f"## Evidences\n" + "\n".join(f"- {item}" for item in request.evidences)
    )
    path = Path("/tmp") / f"{request.incident_id}.md"
    path.write_text(report, encoding="utf-8")
    return {"tool": "generate_report", "path": str(path), "content": report}
