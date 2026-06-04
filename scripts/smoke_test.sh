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
echo "[smoke] check mcp-server"
curl -fsS http://localhost:8100/api/health

echo
echo "[smoke] check ai-service incident endpoint"
curl -fsS -X POST http://localhost:8000/api/incident/diagnose \
  -H 'Content-Type: application/json' \
  -d '{"tenant_id":1,"user_id":1,"service":"order","trace_id":"demo-trace-001","question":"订单创建接口 500，帮我分析根因"}'

echo
echo "[smoke] done"
