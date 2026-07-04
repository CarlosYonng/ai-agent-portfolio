"""AI 服务内置 embedding 工具。

这里统一使用 OpenAI-compatible embedding 接口，DashScope 百炼等兼容服务用于真实语义检索。
"""

from __future__ import annotations

from typing import Any

import httpx

from app.core.settings import settings


async def embed_text(text: str) -> list[float]:
    """根据配置生成查询向量。

    dashscope/openai_compatible 时调用真实模型。
    索引侧和查询侧必须使用同一个模型与维度，否则 Qdrant 检索会失真。
    """

    provider = settings.embedding_provider.lower()
    if provider in {"dashscope", "openai_compatible", "openai"}:
        return await _openai_compatible_embedding(text)
    raise ValueError(f"Unsupported EMBEDDING_PROVIDER: {settings.embedding_provider}，请配置真实 embedding 服务。")


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
