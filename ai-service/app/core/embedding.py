"""AI 服务内置 embedding 工具。

这里提供 hash 与 OpenAI-compatible 两类实现。hash 便于无外部依赖演示；
DashScope 百炼等兼容接口用于真实语义检索。
"""

from __future__ import annotations

import hashlib
import math
import re
from typing import Any

import httpx

from app.core.settings import settings


DEFAULT_VECTOR_SIZE = 128


def tokenize(text: str) -> list[str]:
    """轻量中英文 token 切分。"""

    lowered = text.lower()
    words = re.findall(r"[a-z0-9_]+|[\u4e00-\u9fff]", lowered)
    return words or [lowered[:32]]


def stable_hash_embedding(text: str, size: int = DEFAULT_VECTOR_SIZE) -> list[float]:
    """使用 hashing trick 生成固定维度向量。"""

    vector = [0.0] * size
    for token in tokenize(text):
        digest = hashlib.sha256(token.encode("utf-8")).digest()
        bucket = int.from_bytes(digest[:4], "big") % size
        sign = 1.0 if digest[4] % 2 == 0 else -1.0
        vector[bucket] += sign

    norm = math.sqrt(sum(value * value for value in vector)) or 1.0
    return [value / norm for value in vector]


async def embed_text(text: str) -> list[float]:
    """根据配置生成查询向量。

    EMBEDDING_PROVIDER=hash 时走本地兜底；dashscope/openai_compatible 时调用真实模型。
    索引侧和查询侧必须使用同一个模型与维度，否则 Qdrant 检索会失真。
    """

    provider = settings.embedding_provider.lower()
    if provider in {"hash", "mock", "local_hash"}:
        return stable_hash_embedding(text, settings.embedding_dimensions or DEFAULT_VECTOR_SIZE)
    if provider in {"dashscope", "openai_compatible", "openai"}:
        return await _openai_compatible_embedding(text)
    raise ValueError(f"Unsupported EMBEDDING_PROVIDER: {settings.embedding_provider}")


async def _openai_compatible_embedding(text: str) -> list[float]:
    """调用 OpenAI-compatible embeddings API。

    百炼兼容模式复用 /embeddings 协议，后续切换 OpenAI、硅基流动或自建网关时只改环境变量。
    """

    embedding_token = settings.embedding_token.get_secret_value() or settings.dashscope_token.get_secret_value()
    if not embedding_token:
        raise RuntimeError("EMBEDDING_TOKEN 或 DASHSCOPE_TOKEN 未配置，无法调用真实 embedding 模型。")
    if not settings.embedding_base_url or not settings.embedding_model:
        raise RuntimeError("EMBEDDING_BASE_URL 和 EMBEDDING_MODEL 未配置，无法调用真实 embedding 模型。")

    payload: dict[str, Any] = {
        "model": settings.embedding_model,
        "input": text,
    }
    if settings.embedding_dimensions:
        payload["dimensions"] = settings.embedding_dimensions

    headers = {"Authorization": f"Bearer {embedding_token}"}
    async with httpx.AsyncClient(timeout=30) as client:
        response = await client.post(
            f"{settings.embedding_base_url.rstrip('/')}/embeddings",
            headers=headers,
            json=payload,
        )
        response.raise_for_status()
        data = response.json()
        return [float(value) for value in data["data"][0]["embedding"]]
