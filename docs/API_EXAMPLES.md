# API 调用示例

## 1. Java 后端健康检查

```bash
curl http://localhost:8080/api/health
```

## 2. 创建知识库

```bash
curl -X POST http://localhost:8080/api/kb \
  -H 'Content-Type: application/json' \
  -d '{"tenantId":1,"name":"研发知识库","description":"接口文档和故障处理","visibility":"PRIVATE"}'
```

## 3. 发起知识问答

```bash
curl -X POST http://localhost:8080/api/chat/messages \
  -H 'Content-Type: application/json' \
  -d '{"tenantId":1,"userId":1,"question":"PAY_5001 是什么意思，应该怎么处理？"}'
```

## 4. 查询 MCP 工具

```bash
curl http://localhost:8100/api/tools
```

## 5. 搜索日志

```bash
curl -X POST http://localhost:8100/api/tools/search_logs \
  -H 'Content-Type: application/json' \
  -d '{"service":"order","trace_id":"demo-trace-001","time_range":"last_1h","level":"ERROR"}'
```

## 6. 搜索历史工单

```bash
curl -X POST http://localhost:8100/api/tools/search_tickets \
  -H 'Content-Type: application/json' \
  -d '{"service":"order","symptom":"订单创建接口 500"}'
```

## 7. 直接调用 AI 服务

```bash
curl -X POST http://localhost:8000/api/agent/ask \
  -H 'Content-Type: application/json' \
  -d '{"tenant_id":1,"user_id":1,"session_id":1,"message_id":1,"question":"PAY_5001 是什么意思？"}'
```

这个问题会触发实体抽取：`PAY_5001`。如果你已经执行 `make ingest-a` 且 Neo4j 正常，检索链路会尝试从 Neo4j 的 `Entity <- MENTIONS - Chunk <- HAS_CHUNK - Document` 关系中召回证据。

## 8. 故障诊断 Agent

通过 Java 后端统一入口调用：

```bash
curl -X POST http://localhost:8080/api/incidents/diagnose \
  -H 'Content-Type: application/json' \
  -d '{"tenantId":1,"userId":1,"service":"order","traceId":"demo-trace-001","question":"订单创建接口 500，帮我分析根因"}'
```

直接调用 AI 服务：

```bash
curl -X POST http://localhost:8000/api/incident/diagnose \
  -H 'Content-Type: application/json' \
  -d '{"tenant_id":1,"user_id":1,"service":"order","trace_id":"demo-trace-001","question":"订单创建接口 500，帮我分析根因"}'
```
