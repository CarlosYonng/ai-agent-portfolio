"""Qdrant HTTP 工具。

不用 qdrant-client 依赖，直接通过 HTTP API 调用，减少安装成本。
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

    def ensure_collection(self, collection: str, vector_size: int) -> bool:
        """确保 collection 存在。

        如果 Qdrant 没启动，返回 False，让正式入库链路标记失败。
        """

        exists = self._request("GET", f"/collections/{collection}", None, raise_on_404=False)
        if exists is not None and "result" in exists:
            return True

        payload = {
            "vectors": {
                "size": vector_size,
                "distance": "Cosine",
            }
        }
        created = self._request("PUT", f"/collections/{collection}", payload, raise_on_404=False)
        return created is not None

    def upsert_points(self, collection: str, points: list[dict[str, Any]]) -> bool:
        """批量 upsert points。"""

        payload = {"points": points}
        result = self._request("PUT", f"/collections/{collection}/points?wait=true", payload, raise_on_404=False)
        return result is not None

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

        result = self._request("POST", f"/collections/{collection}/points/search", payload, raise_on_404=False)
        if not result:
            return []
        return result.get("result", [])

    def _request(self, method: str, path: str, payload: dict[str, Any] | None, raise_on_404: bool = True) -> dict[str, Any] | None:
        """发送 HTTP 请求，并把连接失败转成 None。"""

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
        except urllib.error.HTTPError as exc:
            if exc.code == 404 and not raise_on_404:
                return None
            raise
        except OSError:
            return None
