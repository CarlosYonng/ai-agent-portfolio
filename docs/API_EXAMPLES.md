# API 调用示例

## 1. Java 后端健康检查

```bash
curl http://localhost:8080/api/health
```

## 2. 创建知识库

```bash
curl -X POST http://localhost:8080/api/kb \
  -H 'Content-Type: application/json' \
  -d '{"name":"研发知识库","description":"接口文档和故障处理","visibility":"PRIVATE"}'
```

## 3. 发起知识问答（REST）

```bash
curl -X POST http://localhost:8080/api/chat/messages \
  -H 'Content-Type: application/json' \
  -d '{"question":"PAY_5001 是什么意思，应该怎么处理？"}'
```

## 4. 知识问答（SSE 流式）

SSE 端点需要浏览器或支持 EventSource 的客户端调用。

```bash
curl -N http://localhost:8080/api/chat/messages/stream \
  -H 'Content-Type: application/json' \
  -d '{"question":"支付超时怎么排查？"}'
```

## 5. 查询 Trace 轨迹

```bash
curl http://localhost:8080/api/traces/{traceId}
```

## 6. 查询最近 Trace 摘要

```bash
curl http://localhost:8080/api/traces/recent
```

## 7. 查询知识库列表

```bash
curl http://localhost:8080/api/kb
```

## 8. 上传文档

```bash
curl -X POST http://localhost:8080/api/kb/{kbId}/documents/upload \
  -F "file=@文档.md" \
  -F "title=我的文档"
```
