"""共享 embedding 工具。

导入脚本和 AI 查询侧必须使用同一个 embedding 模型与维度。
开发早期可以用确定性的 hash embedding：

1. 不需要网络。
2. 不需要模型凭证。
3. 能写入 Qdrant 并跑通端到端检索链路。

生产/演示环境可以切到 DashScope 百炼等 OpenAI-compatible embeddings API。
"""

from __future__ import annotations

import hashlib
import json
import math
import os
import re
import urllib.error
import urllib.request
from typing import Any

try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover - 脚本缺少 python-dotenv 时仍可读取进程环境变量。
    load_dotenv = None

if load_dotenv is not None:
    load_dotenv()


DEFAULT_VECTOR_SIZE = 128


def tokenize(text: str) -> list[str]:
    """做一个很轻量的中英文 token 切分。

    中文按单字和连续英文/数字混合处理，足够支持 demo 数据。
    """

    lowered = text.lower()
    words = re.findall(r"[a-z0-9_]+|[\u4e00-\u9fff]", lowered)
    return words or [lowered[:32]]


def stable_hash_embedding(text: str, size: int = DEFAULT_VECTOR_SIZE) -> list[float]:
    """把文本转为固定维度向量。

    算法是 hashing trick：把 token hash 到固定桶，并做 L2 归一化。
    """

    vector = [0.0] * size
    for token in tokenize(text):
        digest = hashlib.sha256(token.encode("utf-8")).digest()
        bucket = int.from_bytes(digest[:4], "big") % size
        sign = 1.0 if digest[4] % 2 == 0 else -1.0
        vector[bucket] += sign

    norm = math.sqrt(sum(value * value for value in vector)) or 1.0
    return [value / norm for value in vector]


def embed_text(text: str) -> list[float]:
    """根据环境变量生成文档向量。

    这里用于离线导入脚本；它和 ai-service/app/core/embedding.py 保持同一套配置语义。
    """

    provider = os.getenv("EMBEDDING_PROVIDER", "hash").lower()
    if provider in {"hash", "mock", "local_hash"}:
        return stable_hash_embedding(text, vector_size())
    if provider in {"dashscope", "openai_compatible", "openai"}:
        return _openai_compatible_embedding(text)
    raise ValueError(f"Unsupported EMBEDDING_PROVIDER: {provider}")


def vector_size() -> int:
    """返回当前配置的向量维度。"""

    value = os.getenv("EMBEDDING_DIMENSIONS")
    if value:
        return int(value)
    if os.getenv("EMBEDDING_PROVIDER", "hash").lower() in {"dashscope", "openai_compatible", "openai"}:
        raise RuntimeError("EMBEDDING_DIMENSIONS 未配置，无法为真实 embedding 模型创建 Qdrant collection。")

    return DEFAULT_VECTOR_SIZE


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
