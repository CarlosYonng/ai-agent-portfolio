#!/usr/bin/env python3
"""导入 Java 代码到代码知识库。

脚本会优先写入 MySQL 的 code_symbol 表。
数据库不可用时降级为 dry-run，仍然输出扫描结果。
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT_DIR))

from shared.mysql_client import connect_mysql


CLASS_PATTERN = re.compile(r"\bclass\s+([A-Za-z0-9_]+)")
METHOD_PATTERN = re.compile(r"\b(public|private|protected)\s+[\w<>\[\]]+\s+([A-Za-z0-9_]+)\s*\(")


def extract_symbol_name(text: str, file: Path) -> str:
    """从 Java 文件中提取一个可展示的类/方法名。"""

    class_match = CLASS_PATTERN.search(text)
    if class_match:
        return class_match.group(1)
    method_match = METHOD_PATTERN.search(text)
    if method_match:
        return method_match.group(2)
    return file.stem


def insert_code_symbol(conn, service: str, file: Path, symbol_name: str, content: str) -> None:
    """写入代码片段索引表。"""

    if conn is None:
        return
    with conn.cursor() as cur:
        cur.execute(
            """
            insert into code_symbol (service_name, file_path, symbol_name, language, content, vector_id, metadata)
            values (%s, %s, %s, %s, %s, %s, %s)
            """,
            (service, str(file), symbol_name, file.suffix.lstrip(".") or "text", content, f"code_{service}_{file.stem}", "{}"),
        )
    conn.commit()


def main() -> None:
    """脚本入口。"""

    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-dir", required=True, help="Java 项目目录")
    parser.add_argument("--service", required=True, help="服务名")
    args = parser.parse_args()

    repo_dir = Path(args.repo_dir)
    java_files = list(repo_dir.rglob("*.java"))
    xml_files = list(repo_dir.rglob("*.xml"))
    yml_files = list(repo_dir.rglob("*.yml")) + list(repo_dir.rglob("*.yaml"))
    files = java_files + xml_files + yml_files

    conn = connect_mysql()
    print(f"[ingest_code] service={args.service} files={len(files)}")
    for file in files:
        text = file.read_text(encoding="utf-8", errors="ignore")
        symbol_name = extract_symbol_name(text, file)
        insert_code_symbol(conn, args.service, file, symbol_name, text)
        print(f"  - file={file} symbol={symbol_name} chars={len(text)} preview={text[:60]!r}")

    if conn is not None:
        conn.close()


if __name__ == "__main__":
    main()
