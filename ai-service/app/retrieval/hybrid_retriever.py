"""混合检索器。

采用 Pattern A 设计：
- 主检索走 Qdrant，payload 中存完整 chunk 文本，检索后直接喂给 LLM。
- Neo4j 图谱检索作为辅助路径，从实体查找关联 chunk。
- MySQL 不再参与检索链路，只负责页面管理和查询过滤。

真实召回为空时交给 Agent 明确告知未命中。
"""

from __future__ import annotations

from typing import Any

from app.core.embedding import embed_text
from app.core.entity_extractor import extract_entities
from app.core.neo4j_http import Neo4jHttpClient
from app.core.qdrant_http import QdrantHttpClient
from app.core.settings import settings

COLLECTION_NAME = "kb_chunks"


class HybridRetriever:
    """企业知识库混合检索入口。

    当前召回策略（v1，无 reranker）：
      1. Qdrant bi-encoder 余弦距离召回 top 8
      2. 有结果直接返回（不级联）
      3. Qdrant 无结果 → Neo4j 图谱精确匹配
      4. 都没有 → 空列表

    后续可优化方向：
      - Qdrant limit 提升到 20~50 + 接入 cross-encoder reranker（推荐 LLM 打分方式，
        无需引入新模型依赖），由 reranker 重排后取 top 4
      - 或接入 Cohere/Jina Rerank API，精度更高但需额外 API key
    """

    def __init__(self) -> None:
        self.qdrant = QdrantHttpClient(settings.qdrant_url)
        self.neo4j = Neo4jHttpClient(settings.neo4j_http_url, settings.neo4j_user, settings.neo4j_password)

    async def retrieve(self, query: str, filters: dict[str, Any]) -> list[dict[str, Any]]:
        """执行检索。

        检索策略：
        1. 根据配置生成 query vector，走 Qdrant 按 customer_id 过滤检索。
        2. 如果 Qdrant 命中，直接返回（payload 含完整 chunk 文本）。
        3. 如果 Qdrant 无结果，尝试 Neo4j 图谱召回。
        4. 都没有则返回空列表，由 Agent 生成未命中回答。
        """

        customer_id = filters.get("customer_id")
        kb_id = filters.get("kb_id")
        vector = await embed_text(query)
        vector_filters = {}
        if customer_id and customer_id > 0:
            vector_filters["customer_id"] = customer_id
        if kb_id is not None:
            vector_filters["kb_id"] = kb_id
        # limit=8: 无 reranker 时，Qdrant 的排序就是最终排序。
        # 前 8 个已经是余弦距离最接近的，超过 8 个取 top-k 也轮不到它们。
        # 如果将来上了 reranker，再把 limit 提到 20 以上——reranker 会重新排序，
        # 靠后的候选有可能被捞回来。
        qdrant_results = self.qdrant.search(
            COLLECTION_NAME,
            vector=vector,
            limit=8,
            filters=vector_filters,
        )
        if qdrant_results:
            return [self._from_qdrant(point) for point in qdrant_results]

        graph_customer_id = int(customer_id) if customer_id and customer_id > 0 else None
        graph_results = self._graph_search(query, graph_customer_id, int(kb_id) if kb_id is not None else None)
        if graph_results:
            return graph_results

        return []

    def _from_qdrant(self, point: dict[str, Any]) -> dict[str, Any]:
        """把 Qdrant point 转成 Agent 统一证据格式。"""

        payload = point.get("payload", {})
        return {
            "chunk_id": str(payload.get("chunk_id", point.get("id"))),
            "doc_id": str(payload.get("doc_id", "")),
            "title": str(payload.get("title", "Untitled")),
            "score": max(0.0, 1.0 - float(point.get("score", 0.0))),
            "text": str(payload.get("text", "")),
            "source": "qdrant",
        }

    def _graph_search(self, query: str, customer_id: int | None, kb_id: int | None) -> list[dict[str, Any]]:
        """使用 Neo4j 根据实体关系召回 chunk。

        这就是 GraphRAG 的最小闭环：问题 -> 实体 -> 图谱邻居 -> 文档片段。

        Args:
            query: 用户问题
            customer_id: 客户 ID，None 表示管理员模式（不按客户过滤）
            kb_id: 知识库 ID，None 表示不按知识库过滤
        """

        entities = extract_entities(query)
        if not entities:
            return []

        entity_names = self._entity_names(entities)
        if not entity_names:
            return []
        cypher = """
        MATCH (e:Entity)<-[:MENTIONS]-(c:Chunk)<-[:HAS_CHUNK]-(d:Document)
        WHERE e.name IN $entity_names
          AND ($customer_id IS NULL OR e.customer_id = $customer_id)
          AND ($customer_id IS NULL OR c.customer_id = $customer_id)
          AND ($customer_id IS NULL OR d.customer_id = $customer_id)
          AND ($kb_id IS NULL OR d.kb_id = $kb_id)
        RETURN c.chunk_id AS chunk_id,
               d.doc_id AS doc_id,
               d.title AS title,
               c.preview AS text,
               collect(distinct e.name) AS matched_entities
        LIMIT 8
        """
        rows = self.neo4j.query(cypher, {"customer_id": customer_id, "kb_id": kb_id, "entity_names": entity_names})
        return [
            {
                "chunk_id": str(row.get("chunk_id", "")),
                "doc_id": str(row.get("doc_id", "")),
                "title": str(row.get("title", "Graph Evidence")),
                "text": str(row.get("text", "")),
                "source": "neo4j",
                "matched_entities": row.get("matched_entities", []),
            }
            for row in rows
        ]

    def _entity_names(self, entities: list[dict[str, Any]]) -> list[str]:
        """生成图谱召回用实体名集合。"""

        names: list[str] = []
        seen: set[str] = set()
        for entity in entities:
            name = str(entity.get("name") or "").strip()
            key = name.casefold()
            if name and key not in seen:
                names.append(name)
                seen.add(key)
        return names


hybrid_retriever = HybridRetriever()
