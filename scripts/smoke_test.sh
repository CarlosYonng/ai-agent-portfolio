#!/usr/bin/env bash
# 冒烟测试脚本。
# 用于确认核心服务是否启动；如果服务没启动，会快速失败。

set -euo pipefail

echo "[smoke] check backend-java"
curl -fsS http://localhost:8080/api/health

echo
echo "[smoke] check ai-service"
curl -fsS http://localhost:8000/api/health

echo
echo "[smoke] done"
