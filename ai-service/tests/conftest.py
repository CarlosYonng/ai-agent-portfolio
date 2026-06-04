"""pytest 公共配置。

测试直接运行在 ai-service 目录时可以正常 import app；从仓库根目录运行时，
这里也会把 ai-service 加入 sys.path，避免测试路径依赖当前工作目录。
"""

from __future__ import annotations

import sys
from pathlib import Path


AI_SERVICE_ROOT = Path(__file__).resolve().parents[1]
if str(AI_SERVICE_ROOT) not in sys.path:
    sys.path.insert(0, str(AI_SERVICE_ROOT))
