"""AI Service 日志配置。

按生产常见做法输出控制台日志和按级别滚动文件，容器中通过 LOG_PATH 挂载到宿主机。
"""

from __future__ import annotations

import logging
import os
from logging.handlers import TimedRotatingFileHandler
from pathlib import Path


class ExactLevelFilter(logging.Filter):
    """只接收指定级别日志，便于生成 info/warn/error 独立文件。"""

    def __init__(self, level: int) -> None:
        super().__init__()
        self.level = level

    def filter(self, record: logging.LogRecord) -> bool:
        return record.levelno == self.level


def configure_logging() -> None:
    """初始化 Python 服务日志。"""

    log_path = Path(os.getenv("LOG_PATH", "logs"))
    log_path.mkdir(parents=True, exist_ok=True)
    formatter = logging.Formatter(
        "%(asctime)s app=ai-service level=%(levelname)s pid=%(process)d "
        "logger=%(name)s module=%(module)s line=%(lineno)d message=%(message)s"
    )
    root = logging.getLogger()
    root.setLevel(os.getenv("LOG_LEVEL", "INFO").upper())
    root.handlers.clear()

    console = logging.StreamHandler()
    console.setFormatter(formatter)
    root.addHandler(console)

    all_handler = TimedRotatingFileHandler(log_path / "ai-service.log", when="midnight", backupCount=30, encoding="utf-8")
    all_handler.setFormatter(formatter)
    root.addHandler(all_handler)

    for name, level, backups in (
        ("ai-service-info.log", logging.INFO, 30),
        ("ai-service-warn.log", logging.WARNING, 60),
        ("ai-service-error.log", logging.ERROR, 90),
    ):
        handler = TimedRotatingFileHandler(log_path / name, when="midnight", backupCount=backups, encoding="utf-8")
        handler.setFormatter(formatter)
        handler.addFilter(ExactLevelFilter(level))
        root.addHandler(handler)
