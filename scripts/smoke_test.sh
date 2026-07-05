#!/usr/bin/env bash
# 冒烟测试脚本。
# 用于确认核心服务是否启动；如果服务没启动，会快速失败。
# 端口通过 .env 的 AI_SERVICE_PORT / JAVA_PORT 读取，避免与其他本地服务冲突。

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$PROJECT_DIR/.env"

# 从 .env 中提取端口配置，带默认值
_ai_port="$(grep -v '^#' "$ENV_FILE" 2>/dev/null | grep -E '^AI_SERVICE_PORT=' | tail -1 | cut -d= -f2)"
_java_port="$(grep -v '^#' "$ENV_FILE" 2>/dev/null | grep -E '^JAVA_PORT=' | tail -1 | cut -d= -f2)"
AI_SERVICE_PORT="${_ai_port:-8000}"
JAVA_PORT="${_java_port:-8080}"

echo "[smoke] check backend-java (port $JAVA_PORT)"
curl -fsS "http://localhost:$JAVA_PORT/api/health"

echo
echo "[smoke] check ai-service (port $AI_SERVICE_PORT)"
curl -fsS "http://localhost:$AI_SERVICE_PORT/api/health"

echo
echo "[smoke] check backend-java prometheus metrics"
curl -fsS "http://localhost:$JAVA_PORT/actuator/prometheus" | grep -q "portfolio_java_http"

echo
echo "[smoke] check ai-service prometheus metrics"
curl -fsS "http://localhost:$AI_SERVICE_PORT/metrics" | grep -q "portfolio_ai_http"

echo
echo "[smoke] done"
