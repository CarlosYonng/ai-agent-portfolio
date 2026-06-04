#!/usr/bin/env python3
"""RAG 评测脚本。

第一版读取 JSONL 并打印评测条数。后续接入 AI 服务后，计算 Recall@k、MRR、引用准确率。
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> None:
    """脚本入口。"""

    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", required=True, help="JSONL 评测集路径")
    parser.add_argument("--top-k", type=int, default=5, help="检索 Top K")
    args = parser.parse_args()

    dataset_path = Path(args.dataset)
    cases = [json.loads(line) for line in dataset_path.read_text(encoding="utf-8").splitlines() if line.strip()]
    print(f"[run_rag_eval] dataset={dataset_path} cases={len(cases)} top_k={args.top_k}")

    for case in cases:
        # 这里未来应调用 /api/agent/ask，然后比较 gold_doc_ids 或 gold_root_cause。
        print(f"  - case={case['id']} question={case['question']}")


if __name__ == "__main__":
    main()

