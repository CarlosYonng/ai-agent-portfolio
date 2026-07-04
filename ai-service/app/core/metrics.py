"""Prometheus 指标定义。

所有 label 都保持低基数：只放 endpoint、operation、status、error_type、provider、model。
traceId、用户问题、sessionId 等诊断字段保留在日志和 trace_store，不进入 Prometheus label。
"""

from __future__ import annotations

import time
from collections.abc import Iterator
from contextlib import contextmanager

from prometheus_client import Counter, Histogram


AI_HTTP_REQUESTS = Counter(
    "portfolio_ai_http_requests_total",
    "AI service HTTP 请求总数",
    ["endpoint", "method", "status"],
)
AI_HTTP_DURATION = Histogram(
    "portfolio_ai_http_request_duration_seconds",
    "AI service HTTP 请求耗时",
    ["endpoint", "method"],
)
AGENT_ASK_TOTAL = Counter("portfolio_agent_ask_total", "Agent ask 调用总数", ["status"])
AGENT_ASK_DURATION = Histogram("portfolio_agent_ask_duration_seconds", "Agent ask 处理耗时")

LLM_REQUESTS = Counter(
    "portfolio_llm_requests_total",
    "LLM provider 请求总数",
    ["operation", "model", "status", "error_type"],
)
LLM_DURATION = Histogram(
    "portfolio_llm_request_duration_seconds",
    "LLM provider 请求耗时",
    ["operation", "model"],
)
EMBEDDING_REQUESTS = Counter(
    "portfolio_embedding_requests_total",
    "Embedding provider 请求总数",
    ["operation", "provider", "status", "error_type"],
)
EMBEDDING_DURATION = Histogram(
    "portfolio_embedding_request_duration_seconds",
    "Embedding provider 请求耗时",
    ["operation", "provider"],
)

RAG_RETRIEVE_TOTAL = Counter("portfolio_rag_retrieve_total", "RAG 检索总数", ["status"])
RAG_RETRIEVE_DURATION = Histogram("portfolio_rag_retrieve_duration_seconds", "RAG 检索耗时")
RAG_RETRIEVED_CHUNKS = Histogram(
    "portfolio_rag_retrieved_chunks",
    "RAG 检索候选 chunk 数",
    buckets=(0, 1, 2, 4, 8, 16, 32),
)
RAG_RETRIEVAL_EMPTY = Counter("portfolio_rag_retrieval_empty_total", "RAG 空召回次数", ["reason"])
RAG_ANSWER_CITATION_COUNT = Histogram(
    "portfolio_rag_answer_citation_count",
    "RAG 最终答案引用数量",
    buckets=(0, 1, 2, 3, 4, 8),
)

QDRANT_SEARCH_TOTAL = Counter(
    "portfolio_qdrant_search_total",
    "Qdrant 搜索总数",
    ["status", "error_type"],
)
QDRANT_SEARCH_DURATION = Histogram("portfolio_qdrant_search_duration_seconds", "Qdrant 搜索耗时")
NEO4J_QUERY_TOTAL = Counter(
    "portfolio_neo4j_query_total",
    "Neo4j 查询总数",
    ["operation", "status", "error_type"],
)
NEO4J_QUERY_DURATION = Histogram("portfolio_neo4j_query_duration_seconds", "Neo4j 查询耗时", ["operation"])
GRAPHRAG_FALLBACK_TOTAL = Counter("portfolio_graphrag_fallback_total", "GraphRAG fallback 次数", ["status"])
GRAPH_CONTEXT_HIT_TOTAL = Counter("portfolio_graph_context_hit_total", "图谱上下文命中次数", ["status"])


def now() -> float:
    return time.perf_counter()


def elapsed_seconds(started_at: float) -> float:
    return max(0.0, time.perf_counter() - started_at)


@contextmanager
def observe_histogram(histogram, *labels: str) -> Iterator[None]:
    """用 context manager 记录一段调用耗时。"""

    started_at = now()
    try:
        yield
    finally:
        if labels:
            histogram.labels(*labels).observe(elapsed_seconds(started_at))
        else:
            histogram.observe(elapsed_seconds(started_at))


def error_type(error: BaseException) -> str:
    """把异常压缩成低基数字符串，供 Prometheus label 使用。"""

    name = error.__class__.__name__
    text = str(error).lower()
    if "timeout" in name.lower() or "timeout" in text or "timed out" in text:
        return "timeout"
    if "429" in text or "rate" in text:
        return "rate_limited"
    if "5xx" in text or "500" in text or "503" in text:
        return "server_error"
    return name
