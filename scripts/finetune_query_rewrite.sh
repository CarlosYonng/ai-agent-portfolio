#!/usr/bin/env bash
# Query Rewrite 微调占位脚本。
# 真正微调需要 GPU、基础模型、训练数据和 transformers/peft 环境。
# 你后续可以把这里替换为 Qwen LoRA SFT 的训练命令。

set -euo pipefail

BASE_MODEL="${1:-Qwen/Qwen2.5-1.5B-Instruct}"
DATASET="${2:-datasets/eval/query_rewrite_sft.jsonl}"

echo "[finetune] base_model=${BASE_MODEL}"
echo "[finetune] dataset=${DATASET}"
echo "[finetune] TODO: install transformers peft accelerate bitsandbytes, then run LoRA SFT."

