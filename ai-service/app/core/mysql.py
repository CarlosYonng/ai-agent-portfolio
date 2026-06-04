"""AI 服务 MySQL 连接工具。"""

from __future__ import annotations

from urllib.parse import urlparse

from app.core.settings import settings


def connect_mysql(dict_cursor: bool = False):
    """连接 MySQL。

    失败时抛出异常，由调用方决定是否降级。
    """

    import pymysql

    parsed = urlparse(settings.mysql_dsn)
    cursorclass = pymysql.cursors.DictCursor if dict_cursor else pymysql.cursors.Cursor
    return pymysql.connect(
        host=parsed.hostname or "localhost",
        port=parsed.port or 3306,
        user=parsed.username or "agent",
        password=parsed.password or "agent123",
        database=(parsed.path or "/agentdb").lstrip("/"),
        charset="utf8mb4",
        autocommit=False,
        cursorclass=cursorclass,
    )

