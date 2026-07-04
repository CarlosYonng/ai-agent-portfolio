#!/usr/bin/env bash
# 向 Incident Copilot 发送与 Grafana webhook 同构的最小告警，用于验证入站字段映射。

set -euo pipefail

WEBHOOK_URL="${INCIDENT_COPILOT_GRAFANA_WEBHOOK_URL:-http://localhost:8080/api/alerts/grafana}"
STARTS_AT="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

curl -fsS -X POST "${WEBHOOK_URL}" \
  -H 'Content-Type: application/json' \
  -d "{
    \"alerts\": [
      {
        \"fingerprint\": \"portfolio-smoke-ai-service-timeout\",
        \"startsAt\": \"${STARTS_AT}\",
        \"labels\": {
          \"alertname\": \"PortfolioAiServiceTimeout\",
          \"service\": \"ai-agent-portfolio\",
          \"endpoint\": \"/api/chat/messages\",
          \"exception_type\": \"AIServiceTimeout\",
          \"severity\": \"P1\",
          \"error_rate\": \"0.052\",
          \"p95_latency\": \"4200\",
          \"qps\": \"180\",
          \"affected_requests\": \"34\"
        },
        \"annotations\": {
          \"summary\": \"ai-agent-portfolio chat messages are timing out while calling ai-service\",
          \"description\": \"Smoke payload with the same shape as Grafana webhook for Incident Copilot contract validation.\"
        }
      }
    ]
  }"

echo
echo "[smoke] sent Grafana-compatible alert to ${WEBHOOK_URL}"
