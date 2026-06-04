"""混合检索器。

当前实现优先走 Qdrant HTTP 检索，失败时返回 mock 证据。
这样既能在 Docker 环境中跑真实链路，也能在没有基础设施时完成接口演示。
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
    """企业知识库混合检索入口。"""

    def __init__(self) -> None:
        self.qdrant = QdrantHttpClient(settings.qdrant_url)
        self.neo4j = Neo4jHttpClient(settings.neo4j_http_url, settings.neo4j_user, settings.neo4j_password)

    async def retrieve(self, query: str, filters: dict[str, Any]) -> list[dict[str, Any]]:
        """执行检索。

        检索策略：
        1. 根据配置生成 query vector，演示环境可切到 DashScope 百炼真实 embedding。
        2. Qdrant 按 tenant_id 过滤检索。
        3. 如果 query 中能抽到实体，尝试 Neo4j 图谱召回相关 chunk。
        4. 如果 Qdrant/Neo4j 都没有结果，尝试 MySQL 全文/LIKE 检索。
        5. 如果数据库也不可用，返回 mock 证据。
        """

        tenant_id = filters.get("tenant_id", 1)
        vector = await embed_text(query)
        qdrant_results = self.qdrant.search(
            COLLECTION_NAME,
            vector=vector,
            limit=8,
            filters={"tenant_id": tenant_id},
        )
        if qdrant_results:
            return [self._from_qdrant(point) for point in qdrant_results]

        graph_results = self._graph_search(query, int(tenant_id))
        if graph_results:
            return graph_results

        mysql_results = self._mysql_search(query, int(tenant_id))
        if mysql_results:
            return mysql_results

        return [
            {
                "chunk_id": "mock_chunk_001",
                "doc_id": "mock_doc_001",
                "title": "企业知识库 RAG 设计说明",
                "score": 0.82,
                "preview": f"租户 {tenant_id} 的检索问题为：{query}。这里未来会替换为 Qdrant + BM25 + Neo4j 的真实召回结果。",
                "source": "mock",
            }
        ]

    def _from_qdrant(self, point: dict[str, Any]) -> dict[str, Any]:
        """把 Qdrant point 转成 Agent 统一证据格式。"""

        payload = point.get("payload", {})
        return {
            "chunk_id": str(payload.get("chunk_id", point.get("id"))),
            "doc_id": str(payload.get("doc_id", "")),
            "title": str(payload.get("title", "Untitled")),
            "score": float(point.get("score", 0.0)),
            "preview": str(payload.get("preview", "")),
            "source": "qdrant",
        }

    def _graph_search(self, query: str, tenant_id: int) -> list[dict[str, Any]]:
        """使用 Neo4j 根据实体关系召回 chunk。

        这就是 GraphRAG 的最小闭环：问题 -> 实体 -> 图谱邻居 -> 文档片段。
        """

        entities = extract_entities(query)
        if not entities:
            return []

        entity_names = [entity["name"] for entity in entities]
        cypher = """
        MATCH (e:Entity)<-[:MENTIONS]-(c:Chunk)<-[:HAS_CHUNK]-(d:Document)
        WHERE e.tenant_id = $tenant_id AND e.name IN $entity_names
        RETURN c.chunk_id AS chunk_id,
               d.doc_id AS doc_id,
               d.title AS title,
               c.preview AS preview,
               collect(distinct e.name) AS matched_entities
        LIMIT 8
        """
        rows = self.neo4j.query(cypher, {"tenant_id": tenant_id, "entity_names": entity_names})
        return [
            {
                "chunk_id": str(row.get("chunk_id", "")),
                "doc_id": str(row.get("doc_id", "")),
                "title": str(row.get("title", "Graph Evidence")),
                "score": 0.70,
                "preview": str(row.get("preview", "")),
                "source": "neo4j",
                "matched_entities": row.get("matched_entities", []),
            }
            for row in rows
        ]

    def _mysql_search(self, query: str, tenant_id: int) -> list[dict[str, Any]]:
        """使用 MySQL FULLTEXT/LIKE 做关键词检索。

        这里是 Qdrant 的兜底路径，也能体现混合检索思路。
        """

        try:
            from app.core.mysql import connect_mysql
        except ImportError:
            return []

        sql = """
            select
              c.id as chunk_id,
              c.doc_id,
              d.title,
              c.content,
              case
                when match(c.title_path, c.content) against (%s in natural language mode) > 0
                then match(c.title_path, c.content) against (%s in natural language mode)
                else 0.1
              end as score
            from kb_doc_chunk c
            join kb_document d on d.id = c.doc_id
            where c.tenant_id = %s
              and (
                match(c.title_path, c.content) against (%s in natural language mode)
                or c.content like %s
                or d.title like %s
              )
            order by score desc
            limit 8
        """
        try:
            conn = connect_mysql(dict_cursor=True)
            with conn.cursor() as cur:
                like_query = f"%{query}%"
                cur.execute(sql, (query, query, tenant_id, query, like_query, like_query))
                rows = cur.fetchall()
            conn.close()
        except Exception:
            return []

        return [
            {
                "chunk_id": str(row["chunk_id"]),
                "doc_id": str(row["doc_id"]),
                "title": str(row["title"]),
                "score": float(row["score"]),
                "preview": str(row["content"])[:240],
                "source": "mysql",
            }
            for row in rows
        ]


hybrid_retriever = HybridRetriever()
