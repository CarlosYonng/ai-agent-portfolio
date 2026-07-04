# Observability for ai-agent-portfolio

这套监控链路基于项目真实业务路径，不新增对外 SSE 告警：

```text
POST /api/chat/messages
  -> Java ChatService
  -> Python /api/agent/ask
  -> LLM / Embedding / Qdrant / Neo4j
  -> Prometheus
  -> Grafana alert webhook
  -> ai-incident-copilot /api/alerts/grafana
```

## 启动

先启动相邻项目 Incident Copilot：

```bash
cd ../ai-incident-copilot
scripts/start-docker.sh
curl http://localhost:8080/api/health
```

再启动本项目：

```bash
cd ../ai-agent-portfolio
make up
```

访问入口：

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3001`，默认 `admin/admin`
- Java metrics: `http://localhost:8080/actuator/prometheus`
- AI metrics: `http://localhost:8000/metrics`

## 环境变量

Docker 容器推送宿主机上的 Incident Copilot 时使用：

```env
INCIDENT_COPILOT_BASE_URL=http://host.docker.internal:8080/api
INCIDENT_COPILOT_GRAFANA_WEBHOOK_URL=http://host.docker.internal:8080/api/alerts/grafana
```

本地进程直接运行时可以使用：

```env
INCIDENT_COPILOT_BASE_URL=http://localhost:8080/api
INCIDENT_COPILOT_GRAFANA_WEBHOOK_URL=http://localhost:8080/api/alerts/grafana
```

本地演示故障注入默认关闭：

```env
PORTFOLIO_DEMO_FAULTS_ENABLED=false
PORTFOLIO_DEMO_AI_SLEEP_MS=65000
```

## Java 指标

- `portfolio_java_http_requests_total{endpoint,method,status}`
- `portfolio_java_http_request_duration_seconds_bucket{endpoint,method}`
- `portfolio_chat_requests_total{status}`
- `portfolio_chat_ai_service_duration_seconds_bucket`
- `portfolio_chat_ai_service_errors_total{error_type}`
- `portfolio_kb_upload_total{status}`
- `portfolio_kb_ingestion_total{stage,status}`
- `portfolio_kb_ingestion_duration_seconds_bucket{stage}`
- `portfolio_kb_ingestion_failures_total{stage,error_type}`
- `portfolio_redis_operations_total{operation,status}`
- `portfolio_redis_operation_duration_seconds_bucket{operation}`
- `portfolio_mysql_operations_total{operation,status}`
- `portfolio_mysql_operation_duration_seconds_bucket{operation}`

## Python AI Service 指标

- `portfolio_ai_http_requests_total{endpoint,method,status}`
- `portfolio_ai_http_request_duration_seconds_bucket{endpoint,method}`
- `portfolio_agent_ask_total{status}`
- `portfolio_agent_ask_duration_seconds_bucket`
- `portfolio_llm_requests_total{operation,model,status,error_type}`
- `portfolio_llm_request_duration_seconds_bucket{operation,model}`
- `portfolio_embedding_requests_total{operation,provider,status,error_type}`
- `portfolio_embedding_request_duration_seconds_bucket{operation,provider}`
- `portfolio_rag_retrieve_total{status}`
- `portfolio_rag_retrieve_duration_seconds_bucket`
- `portfolio_rag_retrieved_chunks_bucket`
- `portfolio_rag_retrieval_empty_total{reason}`
- `portfolio_rag_answer_citation_count_bucket`
- `portfolio_qdrant_search_total{status,error_type}`
- `portfolio_qdrant_search_duration_seconds_bucket`
- `portfolio_neo4j_query_total{operation,status,error_type}`
- `portfolio_neo4j_query_duration_seconds_bucket{operation}`
- `portfolio_graphrag_fallback_total{status}`
- `portfolio_graph_context_hit_total{status}`

这些指标不把 `traceId`、用户输入、sessionId、messageId、documentId 放入 label，避免高基数污染 Prometheus。需要诊断关联时查看日志、`agent_trace` 或 Grafana webhook raw payload。

## 本地触发

AI service timeout：

1. 设置 `PORTFOLIO_DEMO_FAULTS_ENABLED=true`，`PORTFOLIO_DEMO_AI_SLEEP_MS` 大于 Java `AI_READ_TIMEOUT_MS`。
2. 调用 `POST /api/chat/messages`，问题中包含 `__demo_ai_sleep__`。
3. 观察 `PortfolioAiServiceTimeout`。

RAG 空召回：

1. 设置 `PORTFOLIO_DEMO_FAULTS_ENABLED=true`。
2. 调用知识库问答，问题中包含 `__demo_empty_rag__`。
3. 连续触发后观察 `PortfolioRagRetrievalEmptySpike`。

Qdrant / Neo4j：

- 临时把 `QDRANT_URL` 指向不可达地址，触发 `PortfolioQdrantUnavailable`。
- 临时把 `NEO4J_HTTP_URL` 指向不可达地址，触发 `PortfolioGraphRagFallbackFailure`。

知识库入库：

- 上传文档时让 embedding token 为空或指向不可达 provider，入库脚本失败后触发 `PortfolioKnowledgeIngestionFailure`。

## Smoke

Grafana provisioning 或规则评估不方便等待时，可以用同构 payload 验证 Incident Copilot 入站契约：

```bash
make smoke-alert
```

这个脚本只验证 `/api/alerts/grafana` 字段映射，不替代真实 Prometheus/Grafana 方案。
