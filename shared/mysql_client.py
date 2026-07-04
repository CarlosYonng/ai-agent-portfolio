"""MySQL 连接工具。

脚本统一用这里连接 MySQL。默认保留返回 None 的开发便利模式；
正式入库链路传入 required=True，连接失败会直接抛错，让文档状态进入 FAILED。
"""

from __future__ import annotations

import os
from urllib.parse import urlparse


def connect_mysql(env_name: str = "MYSQL_DSN", required: bool = False):
    """连接 MySQL，required=True 时连接失败直接抛错。"""

    try:
        import pymysql
    except ImportError:
        if required:
            raise RuntimeError("PyMySQL 未安装，无法写入 MySQL 入库元数据。") from None
        print("[warn] PyMySQL 未安装，跳过 MySQL 写入。")
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
    except Exception as exc:  # noqa: BLE001 - CLI 工具需要把连接错误转换为清晰日志。
        if required:
            raise RuntimeError(f"MySQL 连接失败：{exc}") from exc
        print(f"[warn] MySQL 连接失败：{exc}，跳过 MySQL 写入。")
        return None
