"""AI 服务内置 Qdrant HTTP 客户端。

不用额外 qdrant-client 依赖，便于最小化镜像。
"""

from __future__ import annotations

import json
import urllib.error
import urllib.request
from typing import Any


class QdrantHttpClient:
    """极简 Qdrant HTTP 客户端。"""

    def __init__(self, base_url: str, timeout: int = 5) -> None:
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

    def search(self, collection: str, vector: list[float], limit: int, filters: dict[str, Any]) -> list[dict[str, Any]]:
        """执行向量检索。"""

        must_conditions = []
        for key, value in filters.items():
            if value is None:
                continue
            must_conditions.append({"key": key, "match": {"value": value}})

        payload: dict[str, Any] = {
            "vector": vector,
            "limit": limit,
            "with_payload": True,
            "with_vector": False,
        }
        if must_conditions:
            payload["filter"] = {"must": must_conditions}

        result = self._request("POST", f"/collections/{collection}/points/search", payload)
        if not result:
            return []
        return result.get("result", [])

    def _request(self, method: str, path: str, payload: dict[str, Any] | None) -> dict[str, Any] | None:
        """发送 HTTP 请求，连接失败时返回 None。"""

        data = None if payload is None else json.dumps(payload).encode("utf-8")
        request = urllib.request.Request(
            f"{self.base_url}{path}",
            data=data,
            method=method,
            headers={"Content-Type": "application/json"},
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError:
            return None
        except OSError:
            return None

