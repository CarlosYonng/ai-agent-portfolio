#!/usr/bin/env python3
"""导入模拟日志。

第一版按行读取日志并抽取 traceId，后续可写入 obs_log_event 表。
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT_DIR))

from shared.mysql_client import connect_mysql


TRACE_PATTERN = re.compile(r"traceId=([A-Za-z0-9_-]+)")
ENDPOINT_PATTERN = re.compile(r"endpoint=([^ ]+)")
MESSAGE_PATTERN = re.compile(r"message=(.*)$")


def ensure_service(conn, service: str) -> int | None:
    """确保服务存在，并返回 service_id。"""

    if conn is None:
        return None
    with conn.cursor() as cur:
        cur.execute(
            """
            insert into svc_service (name, env, owner)
            values (%s, 'demo', 'demo-owner')
            """,
            (service,),
        )
        service_id = cur.lastrowid
    conn.commit()
    return int(service_id)


def insert_log_event(conn, service_id: int | None, trace_id: str, level: str, endpoint: str, message: str) -> None:
    """写入日志事件。"""

    if conn is None or service_id is None:
        return
    exception_type = "NullPointerException" if "NullPointerException" in message else None
    with conn.cursor() as cur:
        cur.execute(
            """
            insert into obs_log_event
              (service_id, trace_id, level, endpoint, exception_type, message, stacktrace, occurred_at)
            values (%s, %s, %s, %s, %s, %s, %s, now())
            """,
            (service_id, trace_id, level, endpoint, exception_type, message, message),
        )
    conn.commit()


def main() -> None:
    """脚本入口。"""

    parser = argparse.ArgumentParser()
    parser.add_argument("--log-dir", required=True, help="日志目录")
    parser.add_argument("--service", required=True, help="服务名")
    args = parser.parse_args()

    log_dir = Path(args.log_dir)
    files = list(log_dir.glob("*.log"))
    print(f"[ingest_logs] service={args.service} files={len(files)}")

    conn = connect_mysql()
    service_id = ensure_service(conn, args.service)

    for file in files:
        for line_no, line in enumerate(file.read_text(encoding="utf-8").splitlines(), start=1):
            # 跳过空行，避免把文件末尾换行误识别成一条日志事件。
            if not line.strip():
                continue
            match = TRACE_PATTERN.search(line)
            trace_id = match.group(1) if match else "unknown"
            level = "ERROR" if "ERROR" in line else "INFO"
            endpoint_match = ENDPOINT_PATTERN.search(line)
            endpoint = endpoint_match.group(1) if endpoint_match else ""
            message_match = MESSAGE_PATTERN.search(line)
            message = message_match.group(1) if message_match else line
            insert_log_event(conn, service_id, trace_id, level, endpoint, message)
            print(f"  - file={file.name} line={line_no} trace_id={trace_id} level={level}")

    if conn is not None:
        conn.close()


if __name__ == "__main__":
    main()
