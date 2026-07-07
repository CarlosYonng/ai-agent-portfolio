# RAG REST 问答链路

> 文件名保留 `RAG_STREAMING_FLOW.md` 是为了兼容历史引用；当前实现已经不是对外 SSE 流式链路。

## 当前架构

RAG 问答采用“前端 -> Java 后端 -> Python AI 服务”的 REST JSON 链路。前端调用 Java `POST /api/chat/messages`，Java 负责身份校验、客户隔离、会话消息落库和调用 Python；Python 完成 RAG 编排后返回完整答案、引用和 Trace ID。

## 主链路

1. 用户登录 Java 后端，拿到 JWT。
2. 前端调用 `POST /api/chat/messages`，请求体包含 `question`、可选 `kbId`、可选 `sessionId`。
3. Java ChatController 接收请求，由 ChatService 处理。
4. ChatService 保存 `chat_message:user`，并同步调用 Python `/api/agent/ask`。
5. Python RAG Agent 执行上下文整理、混合检索、答案生成和 Trace 写入。
6. Python 返回 `answer`、`citations`、`traceId`、`summaryBlocks` 等字段。
7. Java 保存 `chat_message:assistant`，更新会话标题。
8. 前端收到完整 JSON 后展示答案、引用和 Trace 链接。
9. 历史列表、会话消息和 Trace 查询继续走 Java API。

## 数据边界

- 前端只传业务输入：`question`、`kbId`、`sessionId`。
- 身份上下文只来自 JWT：`customerId`、`userId`。
- `chat_session` 和 `chat_message` 由 Java 管理。
- `agent_trace` 由 Python AI 服务写入 MySQL。
- Python AI 服务通常只在内网或 Compose 网络内暴露，由 Java 代理访问。

## 检索边界

1. Qdrant 语义召回优先。
2. Qdrant 无结果时进入 Neo4j GraphRAG fallback。
3. 两者均无证据时返回 NO_EVIDENCE，由 Agent 拒答或提示补充知识。

MySQL 当前不作为在线检索兜底。

## 生产注意事项

- Java 和 Python AI 服务通过 `AI_SERVICE_BASE_URL` 通信。
- Java 对 AI 服务调用设置读取超时，超时时返回下游异常并记录业务指标。
- 当前没有对外 `/api/chat/messages/stream` 端点，也不需要为聊天链路配置 SSE proxy buffering。
- Python 侧 Trace 写入失败时可以降级返回答案，但需要在日志和指标中暴露失败。
- 指标通过 Java `/actuator/prometheus` 和 Python `/metrics` 暴露给 Prometheus。
