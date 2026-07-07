# API 调用示例

以下示例默认服务运行在本地 Docker 或本地 debug 环境。业务接口通常需要登录后携带 JWT：

```bash
AUTH_HEADER="Authorization: Bearer <token>"
```

## 1. 健康检查

```bash
curl http://localhost:8080/api/health
curl http://localhost:8000/api/health
```

## 2. 创建知识库

```bash
curl -X POST http://localhost:8080/api/kb \
  -H "$AUTH_HEADER" \
  -H "Content-Type: application/json" \
  -d '{"name":"研发知识库","description":"接口文档和故障处理","visibility":"PRIVATE"}'
```

## 3. 上传文档并触发入库

```bash
curl -X POST http://localhost:8080/api/kb/{kbId}/documents/upload \
  -H "$AUTH_HEADER" \
  -F "file=@docs/sample.md" \
  -F "title=示例文档"
```

## 4. 重试失败文档

```bash
curl -X POST http://localhost:8080/api/kb/documents/{documentId}/retry \
  -H "$AUTH_HEADER"
```

## 5. 发起 RAG 问答

当前对外聊天接口是 REST JSON：

```bash
curl -X POST http://localhost:8080/api/chat/messages \
  -H "$AUTH_HEADER" \
  -H "Content-Type: application/json" \
  -d '{"question":"PAY_5001 是什么意思，应该怎么处理？","kbId":"<kb-id>"}'
```

返回中重点关注：

- `answer`：答案正文。
- `citations`：引用证据。
- `traceId`：Agent 执行轨迹 ID。
- `summaryBlocks`：前端展示用摘要块。

## 6. 查询会话

```bash
curl http://localhost:8080/api/chat/sessions \
  -H "$AUTH_HEADER"

curl http://localhost:8080/api/chat/sessions/{sessionId}/messages \
  -H "$AUTH_HEADER"
```

## 7. 查询 Trace

```bash
curl http://localhost:8080/api/traces/{traceId} \
  -H "$AUTH_HEADER"

curl http://localhost:8080/api/traces/recent \
  -H "$AUTH_HEADER"
```

## 8. 直接调用 Python AI Service

通常由 Java 后端代理调用；本地调试时可以直接请求：

```bash
curl -X POST http://localhost:8000/api/agent/ask \
  -H "Content-Type: application/json" \
  -d '{"question":"支付回调失败如何排查？","kb_id":"<kb-id>","customer_id":"<customer-id>"}'
```

## 9. Metrics

```bash
curl http://localhost:8080/actuator/prometheus
curl http://localhost:8000/metrics
```

## 10. Grafana webhook smoke

```bash
make smoke-alert
```

该命令验证本项目生成的 Grafana payload 能被相邻 `ai-incident-copilot` 的 `/api/alerts/grafana` 正确接收和映射。
