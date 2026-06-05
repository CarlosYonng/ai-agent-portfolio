"""Hybrid search 工具模块：embedding、Qdrant、合并排序。"""

from __future__ import annotations

import os
from pathlib import Path
from urllib.parse import urlparse

import httpx
import pymysql
from dotenv import load_dotenv

# 确保 .env 在 os.getenv 之前加载，兼容直接被 import 的场景
load_dotenv(Path(__file__).resolve().parents[2] / ".env")

# ---------- 配置读取 ----------

_EMBEDDING_BASE_URL = os.getenv(
    "EMBEDDING_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1"
)
_EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "text-embedding-v4")
_EMBEDDING_TOKEN = os.getenv("EMBEDDING_TOKEN", "")
_EMBEDDING_DIMS = int(os.getenv("EMBEDDING_DIMENSIONS", "1024"))
_QDRANT_URL = os.getenv("QDRANT_URL", "http://localhost:6333")
_MYSQL_DSN = os.getenv(
    "MYSQL_DSN", "mysql://agent:agent123@localhost:3306/agentdb"
)


# ===================================================================
# 1. Embedding
# ===================================================================


async def embed_text(text: str) -> list[float]:
    """调用 OpenAI-compatible embedding API 生成查询向量。

    复用 .env 中配置的 DashScope / 百炼等兼容接口，和 ai-service 同一个模型。
    """

    if not _EMBEDDING_TOKEN:
        raise RuntimeError("EMBEDDING_TOKEN 未配置，无法生成向量。")

    payload: dict = {
        "model": _EMBEDDING_MODEL,
        "input": text,
        "dimensions": _EMBEDDING_DIMS,
    }
    headers = {"Authorization": f"Bearer {_EMBEDDING_TOKEN}"}

    async with httpx.AsyncClient(timeout=30) as client:
        resp = await client.post(
            f"{_EMBEDDING_BASE_URL.rstrip('/')}/embeddings",
            headers=headers,
            json=payload,
        )
        resp.raise_for_status()
        data = resp.json()
        return [float(v) for v in data["data"][0]["embedding"]]


# ===================================================================
# 2. Qdrant (via REST API，无需 qdrant-client)
# ===================================================================


async def qdrant_ensure_collection(name: str) -> None:
    """确保 Qdrant 集合存在，不存在则创建。"""

    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.get(f"{_QDRANT_URL}/collections/{name}")
        if resp.status_code == 200:
            # 确保 _table 字段有索引
            await _ensure_payload_index(client, name, "_table")
            return

        # 创建集合
        resp = await client.put(
            f"{_QDRANT_URL}/collections/{name}",
            json={
                "vectors": {
                    "size": _EMBEDDING_DIMS,
                    "distance": "Cosine",
                }
            },
        )
        resp.raise_for_status()
        # 新建集合后立即创建 _table 索引
        await _ensure_payload_index(client, name, "_table")


async def _ensure_payload_index(
    client: httpx.AsyncClient, collection: str, field: str
) -> None:
    """在指定字段上创建 payload 索引，加速过滤查询。幂等——已存在时 Qdrant 返回 200。"""

    try:
        await client.put(
            f"{_QDRANT_URL}/collections/{collection}/index",
            json={"field_name": field, "field_type": "keyword"},
        )
    except Exception:
        pass


async def qdrant_search(
    collection: str,
    vector: list[float],
    *,
    limit: int = 10,
    filter_condition: dict | None = None,
) -> list[dict]:
    """向量搜索，返回 [{id, score, payload}]. 失败时返回空列表。

    filter_condition: Qdrant 过滤条件，如 {"must": [{"key": "_table", "match": {"value": "code_symbol"}}]}
    """

    body: dict = {
        "vector": vector,
        "limit": limit,
        "with_payload": True,
    }
    if filter_condition:
        body["filter"] = filter_condition

    try:
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.post(
                f"{_QDRANT_URL}/collections/{collection}/points/search",
                json=body,
            )
            resp.raise_for_status()
            data = resp.json()
            return [
                {
                    "id": p["id"],
                    "score": p["score"],
                    "payload": p.get("payload", {}),
                }
                for p in data["result"]
            ]
    except Exception:
        return []


async def qdrant_upsert(
    collection: str,
    points: list[dict],
) -> None:
    """批量写入/更新向量点。

    points: [{id, vector, payload}]
    """

    async with httpx.AsyncClient(timeout=30) as client:
        resp = await client.put(
            f"{_QDRANT_URL}/collections/{collection}/points",
            json={"points": points},
        )
        resp.raise_for_status()


# ===================================================================
# 3. MySQL
# ===================================================================


def _get_mysql_conn():
    """获取 MySQL 连接（同步函数，仅在异步上下文外使用）。"""

    parsed = urlparse(_MYSQL_DSN)
    return pymysql.connect(
        host=parsed.hostname or "localhost",
        port=parsed.port or 3306,
        user=parsed.username or "agent",
        password=parsed.password or "agent123",
        database=(parsed.path or "/agentdb").lstrip("/"),
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
    )


def query_mysql(sql: str, params: tuple = ()) -> list[dict]:
    """执行 SQL 查询，异常时返回空列表。"""

    try:
        conn = _get_mysql_conn()
        try:
            with conn.cursor() as cur:
                cur.execute(sql, params)
                return [dict(r) for r in cur.fetchall()]
        finally:
            conn.close()
    except Exception:
        return []


def fetch_by_ids(
    table: str,
    id_column: str,
    ids: list[int],
    score_map: dict[int, float],
) -> list[dict]:
    """根据主键列表回表查询完整数据，并附加向量得分。"""

    if not ids:
        return []
    placeholders = ",".join(["%s"] * len(ids))
    rows = query_mysql(
        f"select * from {table} where {id_column} in ({placeholders})",
        tuple(ids),
    )
    for row in rows:
        row["score"] = score_map.get(row[id_column], 0)
    return rows


# ===================================================================
# 4. 合并排序
# ===================================================================


def merge_ranked(
    ft_rows: list[dict],
    vec_rows: list[dict],
    dedup_key: str,
    ft_weight: float = 0.4,
    vec_weight: float = 0.6,
) -> list[dict]:
    """混合搜索结果合并去重排序。

    策略：
    - 全文分数归一化到 [0,1] × ft_weight
    - 向量分数（余弦相似度在 [0,1]）× vec_weight
    - 两个来源都命中的条目 +0.1 提升
    - 按最终分数降序，取 top 10

    Parameters
    ----------
    ft_rows : MySQL 全文检索结果（含 score 字段）
    vec_rows : Qdrant 向量搜索结果（含 score 字段）
    dedup_key : 去重依据的字段名，如 "id"
    """

    # 归一化全文分数
    ft_scores = [max(r.get("score", 0), 0.1) for r in ft_rows]
    ft_max = max(ft_scores) if ft_scores else 1
    ft_map: dict = {}
    for r in ft_rows:
        key = r.get(dedup_key)
        if key is None:
            continue
        r["score"] = (r.get("score", 0) / ft_max) * ft_weight
        r["_match_type"] = "fulltext"
        ft_map[key] = r

    # 向量分数已在 [0,1]
    vec_map: dict = {}
    for r in vec_rows:
        key = r.get(dedup_key)
        if key is None:
            continue
        r["score"] = r.get("score", 0) * vec_weight
        r["_match_type"] = "vector"
        vec_map[key] = r

    # 合并
    all_keys = set(ft_map.keys()) | set(vec_map.keys())
    merged = []
    for key in all_keys:
        ft_r = ft_map.get(key)
        vec_r = vec_map.get(key)
        if ft_r and vec_r:
            # 两个来源都命中 → 加权和 + hybrid bonus
            row = {**ft_r, **vec_r}
            row["score"] = min(1.0, ft_r["score"] + vec_r["score"] + 0.1)
            row["_match_type"] = "hybrid"
            merged.append(row)
        elif ft_r:
            merged.append(ft_r)
        else:
            merged.append(vec_r)

    merged.sort(key=lambda x: -x["score"])
    return merged[:10]
