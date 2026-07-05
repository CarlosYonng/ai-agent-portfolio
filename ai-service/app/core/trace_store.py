"""Agent Trace 存储。

优先写 MySQL 的 agent_trace 表，失败时降级为日志打印。
"""

from __future__ import annotations

import logging
import time
from typing import Any

from app.core.mysql import connect_mysql
from app.core.settings import settings


logger = logging.getLogger("agent-trace")


def log_trace(
    trace_id: str,
    customer_id: int | None,
    message_id: int | None,
    node_name: str,
    input_summary: str,
    output_summary: str,
    started_at: float,
    metadata: dict[str, Any] | None = None,
) -> None:
    """记录 Agent 节点摘要。

    这里同步写库即可，因为每个节点的数据量很小；后续高并发时可以改成异步队列。
    """

    duration_ms = int((time.time() - started_at) * 1000)
    metadata = metadata or {}
    logger.info(
        "trace_id=%s node=%s duration_ms=%s input=%s output=%s metadata=%s",
        trace_id,
        node_name,
        duration_ms,
        input_summary,
        output_summary,
        metadata,
    )

    try:
        import json
        import pymysql  # noqa: F401 - 仅用于检测依赖是否存在。
    except ImportError:
        return

    try:
        conn = connect_mysql()
        with conn.cursor() as cur:
            cur.execute(
                """
                insert into agent_trace
                  (trace_id, customer_id, message_id, node_name, input_summary, output_summary, duration_ms, metadata)
                values (%s, %s, %s, %s, %s, %s, %s, %s)
                """,
                (
                    trace_id,
                    customer_id,
                    message_id,
                    node_name,
                    input_summary[:1000],
                    output_summary[:1000],
                    duration_ms,
                    json.dumps(metadata, ensure_ascii=False),
                ),
            )
        conn.commit()
        conn.close()
    except Exception as exc:  # noqa: BLE001 - Trace 失败不能影响主链路。
        logger.warning("failed to persist trace: %s", exc)
