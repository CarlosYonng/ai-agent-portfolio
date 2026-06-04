#!/usr/bin/env python3
"""导入企业知识库文档。

脚本会优先尝试写入 MySQL 和 Qdrant。
如果本地没启动数据库或没安装 PyMySQL，会自动降级为 dry-run 打印，避免开发被环境卡住。
"""

from __future__ import annotations

import argparse
import hashlib
import os
import sys
import uuid
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT_DIR))

from shared.embedding import embed_text, vector_size
from shared.entity_extractor import extract_entities
from shared.mysql_client import connect_mysql
from shared.neo4j_http import Neo4jHttpClient
from shared.qdrant_http import QdrantHttpClient


COLLECTION_NAME = "kb_chunks"


def connect_neo4j() -> Neo4jHttpClient | None:
    """连接 Neo4j HTTP 接口，失败时返回 None。"""

    base_url = os.environ.get("NEO4J_HTTP_URL", "http://localhost:7474")
    user = os.environ.get("NEO4J_USER", "neo4j")
    password = os.environ.get("NEO4J_PASSWORD", "agent123456")
    client = Neo4jHttpClient(base_url, user, password)
    if not client.ensure_constraints():
        print("[warn] Neo4j 不可用，跳过知识图谱写入。")
        return None
    return client


def chunk_text(text: str, max_chars: int = 600) -> list[str]:
    """按字符长度做最小可用切片，后续可替换为语义切分。"""

    chunks: list[str] = []
    current: list[str] = []
    current_len = 0
    for paragraph in text.splitlines():
        paragraph = paragraph.strip()
        if not paragraph:
            continue
        if current_len + len(paragraph) > max_chars and current:
            chunks.append("\n".join(current))
            current = []
            current_len = 0
        current.append(paragraph)
        current_len += len(paragraph)
    if current:
        chunks.append("\n".join(current))
    return chunks


def content_hash(text: str) -> str:
    """计算文档内容 hash，用于判断是否重复导入。"""

    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def insert_document(conn, tenant_id: int, kb_id: int, file: Path, text: str) -> int | None:
    """写入文档元数据，并返回 doc_id。"""

    if conn is None:
        return None

    with conn.cursor() as cur:
        cur.execute(
            """
            insert into kb_document (tenant_id, kb_id, title, source_type, source_uri, status, content_hash)
            values (%s, %s, %s, %s, %s, 'INDEXED', %s)
            """,
            (tenant_id, kb_id, file.name, file.suffix.lstrip(".") or "text", str(file), content_hash(text)),
        )
        doc_id = cur.lastrowid
    conn.commit()
    return int(doc_id)


def insert_chunk(conn, tenant_id: int, doc_id: int, chunk_no: int, chunk: str, vector_id: str) -> int | None:
    """写入 chunk 文本，并返回 chunk_id。"""

    if conn is None:
        return None

    with conn.cursor() as cur:
        cur.execute(
            """
            insert into kb_doc_chunk (tenant_id, doc_id, chunk_no, title_path, content, token_count, vector_id, metadata)
            values (%s, %s, %s, %s, %s, %s, %s, %s)
            """,
            (tenant_id, doc_id, chunk_no, "", chunk, len(chunk), vector_id, "{}"),
        )
        chunk_id = cur.lastrowid
    conn.commit()
    return int(chunk_id)


def main() -> None:
    """脚本入口。"""

    parser = argparse.ArgumentParser()
    parser.add_argument("--kb-id", required=True, help="知识库 ID")
    parser.add_argument("--source-dir", required=True, help="文档目录")
    parser.add_argument("--rebuild", action="store_true", help="是否重建索引")
    parser.add_argument("--tenant-id", type=int, default=1, help="租户 ID")
    args = parser.parse_args()

    source_dir = Path(args.source_dir)
    files = list(source_dir.glob("*.md")) + list(source_dir.glob("*.txt"))
    print(f"[ingest_docs] kb_id={args.kb_id} files={len(files)} rebuild={args.rebuild}")

    conn = connect_mysql()
    qdrant = QdrantHttpClient(os.environ.get("QDRANT_URL", "http://localhost:6333"))
    qdrant_ready = qdrant.ensure_collection(COLLECTION_NAME, vector_size())
    if not qdrant_ready:
        print("[warn] Qdrant 不可用，跳过向量写入，仅执行 dry-run。")
    neo4j = connect_neo4j()

    for file in files:
        text = file.read_text(encoding="utf-8")
        chunks = chunk_text(text)
        doc_id = insert_document(conn, args.tenant_id, int(args.kb_id), file, text)
        print(f"[document] title={file.name} chunks={len(chunks)}")
        points = []
        for idx, chunk in enumerate(chunks):
            vector_id = f"doc_{doc_id or file.stem}_{idx}"
            chunk_id = insert_chunk(conn, args.tenant_id, doc_id or 0, idx, chunk, vector_id) if doc_id else None
            vector = embed_text(chunk)
            point_id = str(uuid.uuid5(uuid.NAMESPACE_URL, vector_id))
            points.append(
                {
                    "id": point_id,
                    "vector": vector,
                    "payload": {
                        "tenant_id": args.tenant_id,
                        "kb_id": int(args.kb_id),
                        "doc_id": str(doc_id or file.stem),
                        "chunk_id": str(chunk_id or vector_id),
                        "title": file.name,
                        "preview": chunk[:240],
                        "source_type": file.suffix.lstrip(".") or "text",
                    },
                }
            )
            print(f"  - chunk_no={idx} chunk_id={chunk_id or vector_id} chars={len(chunk)} preview={chunk[:50]!r}")

            entities = extract_entities(chunk)
            if entities:
                print(f"    entities={','.join(entity['name'] for entity in entities)}")
            if neo4j is not None and entities:
                graph_ok = neo4j.upsert_document_graph(
                    tenant_id=args.tenant_id,
                    doc_id=str(doc_id or file.stem),
                    title=file.name,
                    chunk_id=str(chunk_id or vector_id),
                    chunk_preview=chunk,
                    entities=entities,
                )
                print(f"    neo4j_upsert={graph_ok}")

        if qdrant_ready and points:
            ok = qdrant.upsert_points(COLLECTION_NAME, points)
            print(f"  - qdrant_upsert={ok} points={len(points)}")

    if conn is not None:
        conn.close()


if __name__ == "__main__":
    main()
