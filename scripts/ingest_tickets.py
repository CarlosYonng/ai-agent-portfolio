#!/usr/bin/env python3
"""导入历史故障工单。

脚本会把 Markdown 工单解析成 symptom/root_cause/solution 三段，并写入 incident_ticket。
数据库不可用时降级为 dry-run。
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT_DIR))

from shared.mysql_client import connect_mysql


def parse_ticket(text: str) -> dict[str, str]:
    """从简单 Markdown 工单中抽取字段。"""

    fields = {"symptom": "", "root_cause": "", "solution": ""}
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith("现象："):
            fields["symptom"] = stripped.replace("现象：", "", 1)
        elif stripped.startswith("根因："):
            fields["root_cause"] = stripped.replace("根因：", "", 1)
        elif stripped.startswith("解决方案："):
            fields["solution"] = stripped.replace("解决方案：", "", 1)
    return fields


def insert_ticket(conn, service: str, fields: dict[str, str]) -> None:
    """写入工单表。"""

    if conn is None:
        return
    with conn.cursor() as cur:
        cur.execute(
            """
            insert into incident_ticket (service_name, symptom, root_cause, solution, severity)
            values (%s, %s, %s, %s, 'P2')
            """,
            (service, fields["symptom"], fields["root_cause"], fields["solution"]),
        )
    conn.commit()


def main() -> None:
    """脚本入口。"""

    parser = argparse.ArgumentParser()
    parser.add_argument("--ticket-dir", required=True, help="工单 Markdown 目录")
    parser.add_argument("--service", required=True, help="服务名")
    args = parser.parse_args()

    conn = connect_mysql()
    files = list(Path(args.ticket_dir).glob("*.md"))
    print(f"[ingest_tickets] service={args.service} files={len(files)}")
    for file in files:
        fields = parse_ticket(file.read_text(encoding="utf-8"))
        insert_ticket(conn, args.service, fields)
        print(f"  - file={file.name} symptom={fields['symptom'][:50]!r}")

    if conn is not None:
        conn.close()


if __name__ == "__main__":
    main()
