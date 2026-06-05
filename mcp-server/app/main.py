"""MCP 风格工具服务。

这个服务把日志、代码、工单、报告能力包装成工具接口。
真实 MCP Server 后续可以在这些工具函数外层适配 MCP 协议。
"""

from __future__ import annotations

from pathlib import Path
from typing import Optional

from fastapi import FastAPI
from dotenv import load_dotenv
from pydantic import BaseModel

from app import utils


load_dotenv(Path(__file__).resolve().parents[2] / ".env")

app = FastAPI(title="MCP Style Tool Server", version="0.1.0")


@app.on_event("startup")
async def startup():
    """启动时确保 Qdrant 集合存在。"""

    try:
        await utils.qdrant_ensure_collection("knowledge_vectors")
    except Exception:
        pass


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


class RefreshVectorRequest(BaseModel):
    """向量刷新请求。

    不传/传空 = 全量刷新该表；传 ID 列表 = 只刷新指定行。
    """

    code_symbols: Optional[list[int]] = None
    incident_tickets: Optional[list[int]] = None


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

    rows = utils.query_mysql(
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

    return {"tool": "search_logs", "query": request.model_dump(), "results": []}


@app.post("/api/tools/search_code")
async def search_code(request: SearchCodeRequest) -> dict[str, object]:
    """检索代码（混合搜索：全文检索 + 向量语义搜索）。"""

    # ---------- 1. 全文检索 ----------
    ft_rows = utils.query_mysql(
        """
        select id, file_path, symbol_name as symbol, content,
               match(file_path, symbol_name, content) against (%s) as score
        from code_symbol
        where service_name = %s
          and match(file_path, symbol_name, content) against (%s)
        order by score desc
        limit 10
        """,
        (request.query, request.service, request.query),
    )

    # ---------- 2. 向量检索 ----------
    vec_rows: list[dict] = []
    try:
        vector = await utils.embed_text(
            f"{request.query} {request.service} java"
        )
        hits = await utils.qdrant_search(
            "knowledge_vectors", vector,
            filter_condition={"must": [{"key": "_table", "match": {"value": "code_symbol"}}]},
        )
        if hits:
            # Qdrant point ID = "code_symbol_1"，payload.id = MySQL 主键
            score_map = {h["payload"]["id"]: h["score"] for h in hits}
            ids = [h["payload"]["id"] for h in hits]
            vec_rows = utils.fetch_by_ids(
                "code_symbol", "id", ids, score_map
            )
    except Exception:
        pass  # 向量搜索失败时只用全文结果

    # ---------- 3. 混合排序 ----------
    merged = utils.merge_ranked(ft_rows, vec_rows, dedup_key="id")

    # content 太长时截取预览，并清理多余字段
    cleaned = []
    FIELD_MAP = {"file_path", "symbol", "preview", "score", "_match_type"}
    for row in merged:
        if "content" in row:
            row["preview"] = str(row.pop("content", ""))[:500]
        # 统一 symbol 字段名
        if "symbol_name" in row and "symbol" not in row:
            row["symbol"] = row.pop("symbol_name")
        cleaned.append({k: v for k, v in row.items() if k in FIELD_MAP})

    return {"tool": "search_code", "query": request.model_dump(), "results": cleaned}


@app.post("/api/tools/search_tickets")
async def search_tickets(request: SearchTicketsRequest) -> dict[str, object]:
    """检索相似历史工单（混合搜索：语义向量 + 关键词）。"""

    # ---------- 1. 向量检索（语义匹配） ----------
    vec_rows: list[dict] = []
    try:
        vector = await utils.embed_text(f"{request.symptom} {request.service}")
        hits = await utils.qdrant_search(
            "knowledge_vectors", vector,
            filter_condition={"must": [{"key": "_table", "match": {"value": "incident_ticket"}}]},
        )
        if hits:
            score_map = {h["payload"]["id"]: h["score"] for h in hits}
            ids = [h["payload"]["id"] for h in hits]
            vec_rows = utils.fetch_by_ids(
                "incident_ticket", "id", ids, score_map
            )
            # alias 字段名以保持返回格式一致
            for row in vec_rows:
                row["ticket_id"] = row.pop("id", None)
    except Exception:
        pass

    # ---------- 2. 关键词检索（LIKE 兜底） ----------
    ft_rows = utils.query_mysql(
        """
        select id as ticket_id, symptom, root_cause, solution, severity, created_at,
               0.3 as score
        from incident_ticket
        where service_name = %s
          and (symptom like %s or root_cause like %s or solution like %s)
        order by created_at desc
        limit 10
        """,
        (
            request.service,
            f"%{request.symptom}%",
            f"%{request.symptom}%",
            f"%{request.symptom}%",
        ),
    )

    # ---------- 3. 混合排序 ----------
    merged = utils.merge_ranked(ft_rows, vec_rows, dedup_key="ticket_id")

    # 清理多余字段
    FIELD_MAP = {"ticket_id", "symptom", "root_cause", "solution", "severity",
                 "created_at", "score", "_match_type"}
    cleaned = [{k: v for k, v in r.items() if k in FIELD_MAP} for r in merged]

    return {"tool": "search_tickets", "query": request.model_dump(), "results": cleaned}


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


@app.post("/api/admin/refresh_vectors")
async def refresh_vectors(
    request: RefreshVectorRequest = None,
) -> dict[str, object]:
    """从 MySQL 读取数据，embedding 后写入 Qdrant 向量库。

    增量用法 — 只刷新指定的行（推荐）：
      POST /api/admin/refresh_vectors
      {"code_symbols": [1, 3], "incident_tickets": [2]}

    全量用法 — 传空 body 或 {}
      POST /api/admin/refresh_vectors  {}
    会重新读取整个表，覆盖 Qdrant 中已有的向量。
    """

    if request is None:
        request = RefreshVectorRequest()

    # 先查询两表，分别构造点，再一次性写入 Qdrant
    # Qdrant point ID 必须是正整数或 UUID。用 table_id * 1000000 + row_id
    # 让两张表的 ID 落在不同区间，保证不冲突。
    TABLE_ID_OFFSET = {"code_symbol": 1, "incident_ticket": 2}
    all_points: list[dict] = []
    tables_config = [
        ("code_symbol", ["id", "file_path", "symbol_name", "content", "service_name"],
         ["file_path", "symbol_name", "content", "service_name"],
         [("id", "id"), ("file_path", "file_path"), ("symbol_name", "symbol")],
         request.code_symbols),
        ("incident_ticket", ["id", "symptom", "root_cause", "solution", "service_name", "severity"],
         ["symptom", "root_cause", "solution", "service_name"],
         [("id", "id"), ("id", "ticket_id"), ("severity", "severity")],
         request.incident_tickets),
    ]

    for table, select_cols, embed_fields, payload_fields, ids in tables_config:
        cols = ", ".join(select_cols)
        if ids:
            ph = ",".join(["%s"] * len(ids))
            rows = utils.query_mysql(
                f"select {cols} from {table} where id in ({ph})", tuple(ids)
            )
        else:
            rows = utils.query_mysql(f"select {cols} from {table}")
        for row in rows:
            text = " ".join(str(row[f]) for f in embed_fields if f in row)
            try:
                vector = await utils.embed_text(text)
            except Exception:
                continue
            payload = {alias: row[col] for col, alias in payload_fields if col in row}
            payload["_table"] = table  # 标记来源表，搜索时过滤用
            point_id = TABLE_ID_OFFSET.get(table, 0) * 1_000_000 + row["id"]
            all_points.append({"id": point_id, "vector": vector, "payload": payload})

    written = {"code_symbol": 0, "incident_ticket": 0}
    if all_points:
        try:
            await utils.qdrant_upsert("knowledge_vectors", all_points)
            for p in all_points:
                t = p["payload"].get("_table", "unknown")
                if t in written:
                    written[t] += 1
        except Exception:
            written = {"code_symbol": 0, "incident_ticket": 0}

    return {"tool": "refresh_vectors", "written": written}
