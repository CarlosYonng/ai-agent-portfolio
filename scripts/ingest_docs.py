#!/usr/bin/env python3
"""企业知识库文档导入脚本（Pattern A 设计）。

在 RAG 架构中，该脚本是「离线数据处理」环节的核心入口，负责将磁盘上的
Markdown/TXT 文件经过「切片 → 向量化 → 嵌入向量库 + 知识图谱」完整链路，
写入 Qdrant（向量库，payload 存完整 chunk 文本）和 Neo4j（知识图谱），
使文档可被后续 RAG 检索。MySQL 仅写入 kb_document 元数据和入库进度，
供页面管理使用，不参与检索链路。

工作流程：
  1. 读取 .md / .txt 文件
  2. 按段落切片（max 600 字符），目前用简单的字符长度切分
  3. 写入 MySQL kb_document 元数据（仅供页面管理）
  4. 遍历每个切片：
     a. 调用 embedding API 生成向量
     b. 写入 Qdrant：向量 + payload.text（完整文本）
     c. 用正则提取命名实体，若有实体且 Neo4j 可用，写入知识图谱
  5. 批量 upsert 所有 point 到 Qdrant

容错策略：
  - 页面上传和正式入库默认要求 MySQL、Embedding、Qdrant 可用；
    关键依赖不可用时直接失败并回写 FAILED，避免出现“未索引但显示成功”的状态。
  - 如需只验证切片流程，可显式传入 --dry-run，该模式不会伪装成成功入库。
  - 页面上传会先由 Java 后端创建 PENDING 文档，再调用本脚本的单文件模式；
    脚本成功后回写 INDEXED，异常时回写 FAILED，避免页面状态和真实入库链路脱节。
"""

from __future__ import annotations

import argparse
import hashlib
import os
import sys
import uuid
from pathlib import Path

# 将项目根目录加入 sys.path，确保 shared/ 模块可导入。
# 这样做是为了让脚本可以直接 `python scripts/ingest_docs.py` 运行，
# 而不用每次都 pip install -e .。
ROOT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT_DIR))

from shared.embedding import embed_text, vector_size
from shared.entity_extractor import extract_entities
from shared.mysql_client import connect_mysql
from shared.neo4j_http import Neo4jHttpClient
from shared.qdrant_http import QdrantHttpClient


# Qdrant collection 名称，与 Java 后端检索时使用的一致。
# 如需多租户隔离，可在名称中追加 customer_id 后缀，但当前 demo 阶段共用同一 collection。
COLLECTION_NAME = "kb_chunks"


def connect_neo4j() -> Neo4jHttpClient | None:
    """创建 Neo4j HTTP 客户端连接，并确保图谱约束已就绪。

    约束（UNIQUE CONSTRAINT）只在首次创建时生效，重复调用是安全的。
    若 Neo4j 服务不可达或约束创建失败，返回 None 而非抛异常，
    调用方据此跳过图谱写入，实现降级。

    Returns:
        Neo4jHttpClient 实例，或 None（不可用时）。
    """
    base_url = os.environ.get("NEO4J_HTTP_URL", "http://localhost:7474")
    user = os.environ.get("NEO4J_USER", "neo4j")
    password = os.environ.get("NEO4J_PASSWORD", "agent123456")
    client = Neo4jHttpClient(base_url, user, password)
    if not client.ensure_constraints():
        # ensure_constraints() 内部会 catch HTTP 异常，不会抛；
        # 返回 false 说明连通性检查失败。
        print("[warn] Neo4j 不可用，跳过知识图谱写入。")
        return None
    return client


def chunk_text(text: str, max_chars: int = 600) -> list[str]:
    """按段落将文档正文切片，每片不超过 max_chars 字符。

    切片策略：以空行分隔的段落为基本单位，将多个段落合并到一个 chunk 中，
    直到累计字符数超过 max_chars 才切分。
    若单个段落本身超过 max_chars，则按中文句号（。）拆分为子句后再分片，
    避免单个巨段撑爆 chunk。

    Args:
        text: 文档完整正文。
        max_chars: 每片最大字符数（按 len() 计算，含换行符）。

    Returns:
        切片字符串列表，每片包含一个或多个完整段落/子句。

    设计说明：
      - 按段落切分而非按固定字符数截断，是为了保持语义完整性，
        避免在句子中间截断导致向量检索偏差。
      - max_chars=600 对应约 200~300 个中文字或 400~600 个英文字符，
        约等于 3~5 个段落，是一个经验值——太短则上下文不足，
        太长则向量精度下降。
      - 单段落超长时按句号兜底，是「段落完整」和「不超上限」之间的平衡：
        TODO 后续可按需替换为 LangChain RecursiveCharacterTextSplitter + token 计数。
    """
    chunks: list[str] = []
    current: list[str] = []
    current_len = 0

    for paragraph in text.splitlines():
        paragraph = paragraph.strip()
        if not paragraph:
            continue

        # ── 单段落超长：按句号拆成子句再分片 ──
        if len(paragraph) > max_chars:
            # 先把已积累的段落写为一个 chunk
            if current:
                chunks.append("\n".join(current))
                current = []
                current_len = 0
            # 在句号后插换行，再按行拆成子句，保证每个子句末尾保留句号
            clauses = [
                s.strip()
                for s in paragraph.replace("。", "。\n").splitlines()
                if s.strip()
            ]
            sub: list[str] = []
            sub_len = 0
            for clause in clauses:
                # 子句本身也超长（句号只在末尾或没有句号）：硬截断
                if len(clause) > max_chars:
                    # 先把已有子句写掉
                    if sub:
                        chunks.append("".join(sub))
                        sub = []
                        sub_len = 0
                    # 按 max_chars 硬截断
                    start = 0
                    while start < len(clause):
                        chunks.append(clause[start:start + max_chars])
                        start += max_chars
                    continue
                if sub_len + len(clause) > max_chars and sub:
                    chunks.append("".join(sub))
                    sub = []
                    sub_len = 0
                sub.append(clause)
                sub_len += len(clause)
            if sub:
                chunks.append("".join(sub))
            continue

        # ── 正常段落积累 ──
        # current 用 "\n" 拼接，加入新段落后有 len(current) + 1 项，即 len(current) 个换行符
        estimate = current_len + len(paragraph) + len(current)
        if estimate > max_chars and current:
            chunks.append("\n".join(current))
            current = []
            current_len = 0
        current.append(paragraph)
        current_len += len(paragraph)

    # 收尾：不要漏掉最后一个 chunk
    if current:
        chunks.append("\n".join(current))
    return chunks


def content_hash(text: str) -> str:
    """计算文档正文的 SHA-256 摘要，用于重复导入检测。

    content_hash 已写入 kb_document.content_hash 字段，
    但当前版本在 INSERT 时仅记录，未做查询去重（由后续版本实现）。
    此函数为幂等导入预留了基础设施。

    Args:
        text: 文档完整正文。

    Returns:
        64 字符的十六进制 SHA-256 摘要。
    """
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def update_document_status(conn, doc_id: int | None, status: str) -> None:
    """回写文档处理状态，供页面展示真实入库结果。"""
    update_document_progress(conn, doc_id, status=status)


def update_document_progress(
    conn,
    doc_id: int | None,
    *,
    status: str | None = None,
    stage: str | None = None,
    progress: int | None = None,
    chunk_done: int | None = None,
    chunk_total: int | None = None,
    error_message: str | None = None,
) -> None:
    """回写入库阶段和进度，避免管理台只能看到长时间 PENDING。"""
    if conn is None or doc_id is None:
        return

    assignments: list[str] = []
    params: list[object] = []
    if status is not None:
        assignments.append("status = %s")
        params.append(status)
        if status == "INDEXED":
            assignments.extend([
                "ingest_stage = 'INDEXED'",
                "progress_percent = 100",
                "error_message = null",
                "indexed_at = current_timestamp",
            ])
        elif status == "FAILED":
            assignments.append("ingest_stage = 'FAILED'")
    if stage is not None:
        assignments.append("ingest_stage = %s")
        params.append(stage)
    if progress is not None:
        assignments.append("progress_percent = %s")
        params.append(max(0, min(100, progress)))
    if chunk_done is not None:
        assignments.append("chunk_done = %s")
        params.append(max(0, chunk_done))
    if chunk_total is not None:
        assignments.append("chunk_total = %s")
        params.append(max(0, chunk_total))
    if error_message is not None:
        assignments.append("error_message = %s")
        params.append(error_message[:1000] if error_message else None)
    if not assignments:
        return

    params.append(doc_id)
    with conn.cursor() as cur:
        cur.execute(f"update kb_document set {', '.join(assignments)} where id = %s", params)
    conn.commit()


def update_existing_document(
    conn,
    customer_id: int,
    kb_id: int,
    doc_id: int,
    file: Path,
    text: str,
    title: str | None,
) -> int | None:
    """复用 Java 后端已创建的文档记录，并把来源和哈希刷新为本次上传文件。"""
    if conn is None:
        return None

    with conn.cursor() as cur:
        cur.execute(
            """
            update kb_document
            set title = %s,
                source_type = %s,
                source_uri = %s,
                status = 'PENDING',
                ingest_stage = 'PENDING',
                progress_percent = 0,
                chunk_done = 0,
                chunk_total = null,
                error_message = null,
                indexed_at = null,
                content_hash = %s
            where id = %s and kb_id = %s and customer_id = %s
            """,
            (
                title or file.name,
                source_type_for(file),
                str(file),
                content_hash(text),
                doc_id,
                kb_id,
                customer_id,
            ),
        )
        if cur.rowcount == 0:
            raise RuntimeError(f"文档不存在或不属于当前知识库：doc_id={doc_id} kb_id={kb_id}")
    conn.commit()
    return doc_id


def source_type_for(file: Path) -> str:
    """根据文件扩展名生成后端统一使用的来源类型。"""
    suffix = file.suffix.lower().lstrip(".")
    if suffix == "md":
        return "MARKDOWN"
    if suffix == "txt":
        return "TEXT"
    return suffix.upper() or "TEXT"


def insert_document(conn, customer_id: int, kb_id: int, file: Path, text: str, title: str | None = None) -> int | None:
    """向 kb_document 表写入一条文档元数据记录。

    字段说明：
      - title: 取文件名（不含路径），保持与用户原始命名一致。
      - source_type: 从文件扩展名推断（如 .md → "md"），无扩展名则用 "text"。
      - source_uri: 文件的绝对路径，用于后续定位源文件。
      - status: 初始设为 PENDING，脚本结束后再更新 INDEXED/FAILED，
                方便页面在长文档导入时看到真实进度。
      - content_hash: 用于后续重复检测（字段已预留）。
      - ingest_stage: 初始为 'PENDING'，由 ingest_file 按实际步骤推进
                      （CHUNKING → EMBEDDING → WRITING_VECTOR → INDEXED）。

    Args:
        conn: MySQL 连接对象（可为 None，此时直接返回 None 实现降级）。
        customer_id: 租户/客户 ID。
        kb_id: 所属知识库 ID。
        file: 源文件的 Path 对象。
        text: 文件完整正文。

    Returns:
        自增的 doc_id（int），或 None（MySQL 不可用）。
    """
    if conn is None:
        return None

    with conn.cursor() as cur:
        cur.execute(
            """
            insert into kb_document (
                customer_id, kb_id, title, source_type, source_uri, status,
                ingest_stage, progress_percent, chunk_done, content_hash
            )
            values (%s, %s, %s, %s, %s, 'PENDING', 'PENDING', 0, 0, %s)
            """,
            (customer_id, kb_id, title or file.name, source_type_for(file), str(file), content_hash(text)),
        )
        doc_id = cur.lastrowid
    conn.commit()
    return int(doc_id)


def resolve_files(source_dir: str | None, source_file: str | None) -> list[Path]:
    """解析导入文件列表，支持目录批量导入和页面上传后的单文件导入。"""
    if source_file:
        file = Path(source_file)
        return [file] if file.suffix.lower() in {".md", ".txt"} else []
    if not source_dir:
        raise ValueError("--source-dir 和 --source-file 至少提供一个")
    root = Path(source_dir)
    return list(root.glob("*.md")) + list(root.glob("*.txt"))


def ingest_file(
    *,
    file: Path,
    conn,
    qdrant: QdrantHttpClient,
    qdrant_ready: bool,
    neo4j: Neo4jHttpClient | None,
    customer_id: int,
    kb_id: int,
    doc_id: int | None,
    title: str | None,
    domain_terms: dict[str, str] | None = None,
) -> bool:
    """导入单个文档，并负责状态回写。"""
    effective_doc_id: int | None = doc_id
    try:
        # 1. 读文件，同时创建/更新文档记录（标记 READING，表示即将开始处理）
        text = file.read_text(encoding="utf-8")
        if doc_id is not None:
            effective_doc_id = update_existing_document(conn, customer_id, kb_id, doc_id, file, text, title)
        else:
            effective_doc_id = insert_document(conn, customer_id, kb_id, file, text, title)

        # 2. 标记 CHUNKING → 开始切片
        update_document_progress(
            conn,
            effective_doc_id,
            stage="CHUNKING",
            progress=8,
            chunk_done=0,
            chunk_total=None,
            error_message="",
        )
        chunks = chunk_text(text)
        print(f"[document] title={title or file.name} doc_id={effective_doc_id or file.stem} chunks={len(chunks)}")

        points = []
        for idx, chunk in enumerate(chunks):
            chunk_id = f"doc_{effective_doc_id or file.stem}_{idx}"
            # 3. 标记 EMBEDDING → 开始嵌入当前 chunk
            update_document_progress(
                conn,
                effective_doc_id,
                stage="EMBEDDING",
                progress=10 + int(((idx + 1) / max(len(chunks), 1)) * 70),
                chunk_done=idx + 1,
                chunk_total=len(chunks),
            )
            vector = embed_text(chunk)
            point_id = str(uuid.uuid5(uuid.NAMESPACE_URL, chunk_id))
            points.append(
                {
                    "id": point_id,
                    "vector": vector,
                    "payload": {
                        "customer_id": customer_id,
                        "kb_id": kb_id,
                        "doc_id": str(effective_doc_id or file.stem),
                        "chunk_id": chunk_id,
                        "title": title or file.name,
                        "text": chunk,
                        "source_type": source_type_for(file),
                    },
                }
            )
            print(f"  - chunk_no={idx} chunk_id={chunk_id} chars={len(chunk)} text={chunk[:50]!r}")

            entities = extract_entities(chunk, domain_terms=domain_terms)
            if entities:
                print(f"    entities={','.join(entity['name'] for entity in entities)}")
            if neo4j is not None and entities:
                graph_ok = neo4j.upsert_document_graph(
                    customer_id=customer_id,
                    kb_id=kb_id,
                    doc_id=str(effective_doc_id or file.stem),
                    title=title or file.name,
                    chunk_id=chunk_id,
                    chunk_preview=chunk,
                    entities=entities,
                )
                print(f"    neo4j_upsert={graph_ok}")

        if qdrant_ready and points:
            update_document_progress(
                conn,
                effective_doc_id,
                stage="WRITING_VECTOR",
                progress=90,
                chunk_done=len(chunks),
                chunk_total=len(chunks),
            )
            ok = qdrant.upsert_points(COLLECTION_NAME, points)
            print(f"  - qdrant_upsert={ok} points={len(points)}")
            if not ok:
                raise RuntimeError("Qdrant 写入失败")

        update_document_status(conn, effective_doc_id, "INDEXED")
        return True
    except Exception as exc:  # noqa: BLE001 - 导入任务需要逐文档落状态。
        print(f"[error] 文档导入失败 file={file} error={exc}")
        update_document_progress(conn, effective_doc_id, status="FAILED", error_message=str(exc))
        return False


def main() -> None:
    """脚本入口：执行文档导入全流程。

    命令行参数：
      --kb-id       必需，目标知识库 ID（对应 Java 端的 KnowledgeBase.id）。
      --source-dir  可选，包含 .md / .txt 文件的目录路径。
      --source-file 可选，单个 .md / .txt 文件路径，供页面上传后触发。
      --doc-id      可选，复用后端已创建的文档 ID，并回写状态。
      --rebuild     可选，是否重建索引（当前仅标记日志，具体逻辑待实现）。
      --customer-id 可选，客户 ID，默认 1。

    执行步骤（每个文件）：
      1. 读取文件内容
      2. 按段落切片
      3. 写入 MySQL kb_document 元数据（仅供页面管理）
      4. 遍历每个切片：
         a. 调用 embedding API 生成向量
         b. 生成 UUID5 唯一 point_id，写入 Qdrant（向量 + payload.text 完整文本）
         c. 用正则提取命名实体，若有实体且 Neo4j 可用，写入知识图谱
      5. 批量 upsert 所有 point 到 Qdrant
    """
    parser = argparse.ArgumentParser(
        description="将 Markdown/TXT 文档导入知识库（MySQL + Qdrant + Neo4j）"
    )
    parser.add_argument("--kb-id", required=True, help="知识库 ID，对应后端 KnowledgeBase.id")
    parser.add_argument("--source-dir", help="包含 .md / .txt 文件的目录路径")
    parser.add_argument("--source-file", help="单个 .md / .txt 文件路径，通常来自页面上传")
    parser.add_argument("--doc-id", type=int, help="已存在的 kb_document.id，传入后会复用并回写状态")
    parser.add_argument("--title", help="覆盖默认文件名的文档标题")
    parser.add_argument("--rebuild", action="store_true", help="是否重建索引（当前仅打印标记，逻辑待实现）")
    parser.add_argument("--customer-id", type=int, default=1, help="客户 ID，默认 1")
    parser.add_argument("--domain-terms", help="知识库领域术语，JSON 格式，如 '{\"用户\": \"Concept\", \"订单系统\": \"System\"}'")
    parser.add_argument("--strict-mysql", action="store_true", help="MySQL 不可用时直接失败，供页面上传链路使用")
    parser.add_argument("--dry-run", action="store_true", help="只执行文件读取和切片检查，不写入 MySQL/Qdrant/Neo4j")
    args = parser.parse_args()

    # 解析领域术语（JSON → dict）
    domain_terms: dict[str, str] | None = None
    if args.domain_terms:
        import json
        try:
            domain_terms = json.loads(args.domain_terms)
            if not isinstance(domain_terms, dict):
                print("[error] --domain-terms 必须是 JSON 对象")
                sys.exit(1)
        except json.JSONDecodeError as e:
            print(f"[error] --domain-terms JSON 解析失败: {e}")
            sys.exit(1)

    # ---- 扫描源文件 ----
    files = resolve_files(args.source_dir, args.source_file)
    customer_id = args.customer_id
    print(f"[ingest_docs] kb_id={args.kb_id} files={len(files)} rebuild={args.rebuild}")

    # ---- 初始化各存储后端：正式链路要求关键存储真实可用 ----
    conn = connect_mysql(required=args.strict_mysql and not args.dry_run)
    if conn is None and args.strict_mysql:
        print("[error] strict-mysql 已启用，但 MySQL 不可用。")
        sys.exit(2)
    qdrant = QdrantHttpClient(os.environ.get("QDRANT_URL", "http://localhost:6333"))
    if args.dry_run:
        qdrant_ready = False
        neo4j = None
        print("[dry-run] 只验证文件读取和切片，不写入任何外部存储。")
    else:
        qdrant_ready = qdrant.ensure_collection(COLLECTION_NAME, vector_size())
        if not qdrant_ready:
            print("[error] Qdrant 不可用，无法写入向量索引。")
            sys.exit(3)
        neo4j = connect_neo4j()

    if not files:
        print("[error] 未找到可导入的 .md/.txt 文件。")
        sys.exit(1)

    failed = 0
    for file in files:
        ok = ingest_file(
            file=file,
            conn=conn,
            qdrant=qdrant,
            qdrant_ready=qdrant_ready,
            neo4j=neo4j,
            customer_id=customer_id,
            kb_id=int(args.kb_id),
            doc_id=args.doc_id,
            title=args.title,
            domain_terms=domain_terms,
        )
        failed += 0 if ok else 1

    if conn is not None:
        conn.close()
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
