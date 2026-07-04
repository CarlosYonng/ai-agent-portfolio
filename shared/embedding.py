"""共享 embedding 工具。

导入脚本和 AI 查询侧必须使用同一个 embedding 模型与维度。
生产/演示环境统一使用 DashScope 百炼等 OpenAI-compatible embeddings API。
"""

from __future__ import annotations

import json
import os
import urllib.error
import urllib.request
from typing import Any

try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover - 脚本缺少 python-dotenv 时仍可读取进程环境变量。
    load_dotenv = None

if load_dotenv is not None:
    load_dotenv()


def embed_text(text: str) -> list[float]:
    """根据环境变量生成文档向量。

    这里用于离线导入脚本；它和 ai-service/app/core/embedding.py 保持同一套配置语义。
    """

    provider = os.getenv("EMBEDDING_PROVIDER", "").lower()
    if provider in {"dashscope", "openai_compatible", "openai"}:
        return _openai_compatible_embedding(text)
    raise ValueError(f"Unsupported EMBEDDING_PROVIDER: {provider or '<empty>'}，请配置真实 embedding 服务。")


def vector_size() -> int:
    """返回当前配置的向量维度。"""

    value = os.getenv("EMBEDDING_DIMENSIONS")
    if value:
        return int(value)
    raise RuntimeError("EMBEDDING_DIMENSIONS 未配置，无法为真实 embedding 模型创建 Qdrant collection。")


def _openai_compatible_embedding(text: str) -> list[float]:
    """调用 OpenAI-compatible embeddings API 生成真实语义向量。"""

    embedding_token = os.getenv("EMBEDDING_TOKEN") or os.getenv("DASHSCOPE_TOKEN")
    if not embedding_token:
        raise RuntimeError("EMBEDDING_TOKEN 或 DASHSCOPE_TOKEN 未配置，无法调用真实 embedding 模型。")
    model = os.getenv("EMBEDDING_MODEL")
    base_url = os.getenv("EMBEDDING_BASE_URL")
    if not model or not base_url:
        raise RuntimeError("EMBEDDING_MODEL 和 EMBEDDING_BASE_URL 未配置，无法调用真实 embedding 模型。")

    payload: dict[str, Any] = {
        "model": model,
        "input": text,
    }
    dimensions = vector_size()
    if dimensions:
        payload["dimensions"] = dimensions

    request = urllib.request.Request(
        f"{base_url.rstrip('/')}/embeddings",
        data=json.dumps(payload).encode("utf-8"),
        method="POST",
        headers={
            "Authorization": f"Bearer {embedding_token}",
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            data = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        error_body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Embedding API 调用失败：HTTP {exc.code} {error_body}") from exc
    return [float(value) for value in data["data"][0]["embedding"]]
