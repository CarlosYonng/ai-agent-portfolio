"""提示词加载器。

提示词外部化后，调优 RAG 行为不需要改 Agent 代码；解析器只支持本项目当前需要的简单 yaml 字面量。
"""

from __future__ import annotations

from pathlib import Path


def load_prompt(name: str, default: str) -> str:
    """读取 default.yaml 中的多行提示词，失败时回退到代码默认值。"""

    path = Path(__file__).with_name("default.yaml")
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError:
        return default

    key = f"{name}:"
    for index, line in enumerate(lines):
        if line.strip() == f"{key} |":
            block: list[str] = []
            for raw in lines[index + 1:]:
                if raw and not raw.startswith(" "):
                    break
                block.append(raw[2:] if raw.startswith("  ") else raw)
            return "\n".join(block).strip() or default
    return default
