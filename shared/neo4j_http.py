"""Neo4j HTTP Cypher 客户端。

不用额外安装 neo4j-driver，直接调用 Neo4j HTTP transaction endpoint。
这让脚本在本地和 Docker 中都更容易跑。
"""

from __future__ import annotations

import base64
import json
import urllib.request
from typing import Any


class Neo4jHttpClient:
    """极简 Neo4j HTTP 客户端。"""

    def __init__(self, base_url: str, user: str, password: str, database: str = "neo4j", timeout: int = 5) -> None:
        self.base_url = base_url.rstrip("/")
        self.database = database
        self.timeout = timeout
        token = base64.b64encode(f"{user}:{password}".encode("utf-8")).decode("ascii")
        self.headers = {
            "Content-Type": "application/json",
            "Authorization": f"Basic {token}",
        }

    def run(self, cypher: str, parameters: dict[str, Any] | None = None) -> bool:
        """执行 Cypher，失败返回 False。"""

        payload = {
            "statements": [
                {
                    "statement": cypher,
                    "parameters": parameters or {},
                }
            ]
        }
        data = json.dumps(payload).encode("utf-8")
        request = urllib.request.Request(
            f"{self.base_url}/db/{self.database}/tx/commit",
            data=data,
            method="POST",
            headers=self.headers,
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                result = json.loads(response.read().decode("utf-8"))
                return not result.get("errors")
        except OSError:
            return False

    def ensure_constraints(self) -> bool:
        """创建基础唯一约束（含客户隔离）。"""

        ok_entity = self.run(
            "CREATE CONSTRAINT entity_name_customer IF NOT EXISTS "
            "FOR (e:Entity) REQUIRE (e.customer_id, e.name, e.type) IS UNIQUE"
        )
        ok_doc = self.run(
            "CREATE CONSTRAINT document_customer IF NOT EXISTS "
            "FOR (d:Document) REQUIRE (d.customer_id, d.doc_id) IS UNIQUE"
        )
        ok_chunk = self.run(
            "CREATE CONSTRAINT chunk_customer IF NOT EXISTS "
            "FOR (c:Chunk) REQUIRE (c.customer_id, c.chunk_id) IS UNIQUE"
        )
        return ok_entity and ok_doc and ok_chunk

    def upsert_document_graph(
        self,
        customer_id: int,
        kb_id: int,
        doc_id: str,
        title: str,
        chunk_id: str,
        chunk_preview: str,
        entities: list[dict[str, Any]],
    ) -> bool:
        """写入 Document-Chunk-Entity 图谱关系。

        学习版图谱只保留实体抽取最核心的信息：实体名、类型、来源、置信度、
        出现次数和原文 mention。这样更容易理解 GraphRAG 的基本闭环。
        """

        cypher = """
        MERGE (d:Document {doc_id: $doc_id, customer_id: $customer_id})
          SET d.kb_id = $kb_id, d.title = $title
        MERGE (c:Chunk {chunk_id: $chunk_id, customer_id: $customer_id})
          SET c.kb_id = $kb_id, c.preview = $chunk_preview
        MERGE (d)-[:HAS_CHUNK]->(c)
        WITH c
        UNWIND $entities AS entity
        MERGE (e:Entity {customer_id: $customer_id, name: entity.name, type: entity.type})
          SET e.source = coalesce(entity.source, 'unknown'),
              e.confidence = coalesce(entity.confidence, 0.0),
              e.updated_at = datetime()
        MERGE (c)-[m:MENTIONS]->(e)
          SET m.surfaces = [mention IN coalesce(entity.mentions, []) | mention.text],
              m.occurrence_count = coalesce(entity.occurrence_count, size(coalesce(entity.mentions, []))),
              m.confidence = coalesce(entity.confidence, 0.0),
              m.source = coalesce(entity.source, 'unknown')
        """
        return self.run(
            cypher,
            {
                "customer_id": customer_id,
                "kb_id": kb_id,
                "doc_id": doc_id,
                "title": title,
                "chunk_id": chunk_id,
                "chunk_preview": chunk_preview,
                "entities": entities,
            },
        )
