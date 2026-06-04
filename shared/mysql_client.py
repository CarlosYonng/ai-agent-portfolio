"""MySQL 连接工具。

脚本统一用这里连接 MySQL。没有安装 PyMySQL 或数据库不可用时返回 None，
这样导入脚本仍能 dry-run 演示处理流程。
"""

from __future__ import annotations

import os
from urllib.parse import urlparse


def connect_mysql(env_name: str = "MYSQL_DSN"):
    """连接 MySQL，失败时返回 None。"""

    try:
        import pymysql
    except ImportError:
        print("[warn] PyMySQL 未安装，跳过 MySQL 写入，仅执行 dry-run。")
        return None

    dsn = os.getenv(env_name, "mysql://agent:agent123@localhost:3306/agentdb")
    parsed = urlparse(dsn)
    try:
        return pymysql.connect(
            host=parsed.hostname or "localhost",
            port=parsed.port or 3306,
            user=parsed.username or "agent",
            password=parsed.password or "agent123",
            database=(parsed.path or "/agentdb").lstrip("/"),
            charset="utf8mb4",
            autocommit=False,
            cursorclass=pymysql.cursors.DictCursor,
        )
    except Exception as exc:  # noqa: BLE001 - 脚本需要友好降级。
        print(f"[warn] MySQL 连接失败：{exc}，仅执行 dry-run。")
        return None

