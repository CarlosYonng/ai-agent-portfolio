"""AI 服务内置 Neo4j HTTP Cypher 客户端。

用于问答时的图谱召回，不依赖 neo4j-driver，减少镜像复杂度。
"""

from __future__ import annotations

import base64
import json
import time
import urllib.request
from typing import Any

from app.core import metrics


class Neo4jHttpClient:
    """极简 Neo4j HTTP 客户端。"""

    def __init__(self, base_url: str, user: str, password: str, database: str = "neo4j", timeout: int = 5) -> None:
        self.base_url = base_url.rstrip("/")
        self.database = database
        self.timeout = timeout
        token = base64.b64encode(f"{user}:{password}".encode("utf-8")).decode("ascii")
        self.headers = {
            "Content-Type": "application/json",
            "Authorization": f"Basic {token}",
        }

    def query(self, cypher: str, parameters: dict[str, Any] | None = None) -> list[dict[str, Any]]:
        """执行 Cypher 查询，并返回扁平化 row。"""

        started_at = time.perf_counter()
        operation = "graphrag_fallback"
        payload = {
            "statements": [
                {
                    "statement": cypher,
                    "parameters": parameters or {},
                }
            ]
        }
        data = json.dumps(payload).encode("utf-8")
        request = urllib.request.Request(
            f"{self.base_url}/db/{self.database}/tx/commit",
            data=data,
            method="POST",
            headers=self.headers,
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                result = json.loads(response.read().decode("utf-8"))
        except OSError:
            metrics.NEO4J_QUERY_TOTAL.labels(operation, "error", "GraphRagFallbackFailure").inc()
            metrics.NEO4J_QUERY_DURATION.labels(operation).observe(time.perf_counter() - started_at)
            return []

        if result.get("errors"):
            metrics.NEO4J_QUERY_TOTAL.labels(operation, "error", "GraphRagFallbackFailure").inc()
            metrics.NEO4J_QUERY_DURATION.labels(operation).observe(time.perf_counter() - started_at)
            return []
        statements = result.get("results", [])
        if not statements:
            metrics.NEO4J_QUERY_TOTAL.labels(operation, "success", "none").inc()
            metrics.NEO4J_QUERY_DURATION.labels(operation).observe(time.perf_counter() - started_at)
            return []

        columns = statements[0].get("columns", [])
        rows = []
        for row in statements[0].get("data", []):
            values = row.get("row", [])
            rows.append(dict(zip(columns, values, strict=False)))
        metrics.NEO4J_QUERY_TOTAL.labels(operation, "success", "none").inc()
        metrics.NEO4J_QUERY_DURATION.labels(operation).observe(time.perf_counter() - started_at)
        return rows
