# Incident Copilot Integration

## Webhook

Grafana contact point 推送到：

```text
${INCIDENT_COPILOT_GRAFANA_WEBHOOK_URL}
```

Docker 默认值：

```text
http://host.docker.internal:8080/api/alerts/grafana
```

本地默认值：

```text
http://localhost:8080/api/alerts/grafana
```

## 字段映射

| Grafana 字段 | Incident Copilot 字段 | 说明 |
| --- | --- | --- |
| `labels.alertname` | `signalName` | 告警名称 |
| `labels.service` / `labels.service_name` | `serviceName` | 服务名 |
| `labels.endpoint` / `labels.path` | `endpoint` | 接口或业务操作 |
| `labels.trace_id` / `labels.traceId` | `traceId` | 可为空，不作为 Prometheus label |
| `labels.exception_type` / `labels.exceptionType` | `exceptionType` | 归因类型 |
| `annotations.description` / `annotations.summary` | `summary` | 告警摘要 |
| `labels.error_rate` | `errorRate` | 错误率 |
| `labels.p95_latency` / `labels.p95Latency` / `labels.p95` | `p95Latency` | p95 延迟 |
| `labels.qps` | `qps` | QPS |
| `labels.affected_requests` / `labels.affectedRequests` | `affectedRequests` | 影响请求数 |
| `labels.severity` / `labels.severity_hint` | `severityHint` | 告警等级建议 |

当前规则会尽量提供 `error_rate`、`p95_latency` 或 `affected_requests`，确保满足 Incident Copilot 入站阈值，避免被标记为 `IGNORED`。

## 告警与 Runbook 对齐

| alertname | exception_type | service | endpoint | 建议 runbook |
| --- | --- | --- | --- | --- |
| `PortfolioAiServiceTimeout` | `AIServiceTimeout` | `ai-agent-portfolio` | `/api/chat/messages` | `portfolio-ai-service-timeout.md` |
| `PortfolioLlmProviderError` | `LLMProviderError` | `portfolio-ai-service` | `llm_chat_completion` | `portfolio-ai-service-timeout.md` |
| `PortfolioRagRetrievalEmptySpike` | `RAGRetrievalEmpty` | `portfolio-ai-service` | `retrieve_knowledge` | `portfolio-rag-retrieval-empty.md` |
| `PortfolioQdrantUnavailable` | `QdrantUnavailable` | `portfolio-ai-service` | `qdrant_search` | `portfolio-qdrant-unavailable.md` |
| `PortfolioGraphRagFallbackFailure` | `GraphRagFallbackFailure` | `portfolio-ai-service` | `graphrag_fallback` | `portfolio-graphrag-fallback-failure.md` |
| `PortfolioKnowledgeIngestionFailure` | `KnowledgeIngestionFailure` | `ai-agent-portfolio` | `knowledge_ingestion` | `portfolio-knowledge-ingestion-failure.md` |
| `PortfolioEmbeddingProviderError` | `EmbeddingProviderError` | `ai-agent-portfolio` | `embedding` | `portfolio-knowledge-ingestion-failure.md` |
| `PortfolioRedisDependencyDegraded` | `RedisUnavailable` | `ai-agent-portfolio` | `redis_cache` | `redis-cache-failure.md` |
| `PortfolioDatabaseDegraded` | `DatabaseSlowQuery` | `ai-agent-portfolio` | `mysql` | `database-slow-query.md` 或 `dependency-unavailable.md` |

## Grafana Payload 示例

```json
{
  "alerts": [
    {
      "fingerprint": "portfolio-ai-service-timeout-001",
      "startsAt": "2026-07-04T00:00:00Z",
      "labels": {
        "alertname": "PortfolioAiServiceTimeout",
        "service": "ai-agent-portfolio",
        "endpoint": "/api/chat/messages",
        "exception_type": "AIServiceTimeout",
        "severity": "P1",
        "error_rate": "0.052",
        "p95_latency": "4200",
        "qps": "180",
        "affected_requests": "34"
      },
      "annotations": {
        "summary": "ai-agent-portfolio chat messages are timing out while calling ai-service",
        "description": "Real Grafana alert generated from portfolio business metrics."
      }
    }
  ]
}
```

## 验收步骤

```bash
cd ../ai-incident-copilot
scripts/start-docker.sh
curl http://localhost:8080/api/health

cd ../ai-agent-portfolio
make up
curl http://localhost:8080/actuator/prometheus
curl http://localhost:8000/metrics
```

触发真实链路：

1. 调用 `POST /api/chat/messages`，确认 Java 和 Python metrics 增长。
2. 上传或重试文档入库，确认 `portfolio_kb_ingestion_*` 有数据。
3. 打开 Grafana `http://localhost:3001` 查看 Overview、Chat/RAG、Dependencies dashboard。
4. 触发至少两个告警，建议 `PortfolioAiServiceTimeout` 和 `PortfolioRagRetrievalEmptySpike`。
5. 查询 Incident Copilot：

```bash
curl "http://localhost:8080/api/incidents?serviceName=ai-agent-portfolio"
curl "http://localhost:8080/api/incidents/{incidentId}/alerts"
```

期望入站告警状态为 `INCIDENT_CREATED` 或 `CORRELATED`，不是 `IGNORED`。

## Incident Copilot Runbook 建议

本次不修改相邻仓库。若 Incident Copilot 中缺少以下 runbook，建议补齐：

- `portfolio-ai-service-timeout.md`
- `portfolio-rag-retrieval-empty.md`
- `portfolio-qdrant-unavailable.md`
- `portfolio-graphrag-fallback-failure.md`
- `portfolio-knowledge-ingestion-failure.md`
