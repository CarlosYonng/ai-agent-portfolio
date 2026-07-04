# RAG 流式问答链路

## 目标架构

RAG 问答采用"Java 后端代理转发"的流式链路：前端通过 SSE 连接到 Java 后端，Java 将用户问题转发给 Python AI 服务（REST 调用），Python 完成 Agent 编排后返回完整结果，Java 将结果切片为 SSE 事件推送到前端。

## 主链路

1. 用户登录 Java 后端，拿到 JWT。
2. 前端调用 `POST /api/chat/messages/stream`（SSE 端点），请求体包含 `question`、可选 `kbId`、可选 `sessionId`。
3. Java ChatController 接收请求，由 ChatService 处理。
4. ChatService 保存 `chat_message:user`，调用 Python `/api/agent/ask`（REST）。
5. Python RAG Agent 执行核心编排：上下文整理、混合检索、答案生成、Trace 写入。
6. Python 返回 `AgentAskResponse`（包含 `answer`、`citations`、`traceId`）。
7. Java 将响应切分为 `metadata`、`delta`（逐段推送答案）、`citations`、`done` 等 SSE 事件。
8. Java 保存 `chat_message:assistant` 和会话标题。
9. 前端收到 SSE 事件流，逐步渲染答案、引用和 Trace 链接。
10. 历史列表和 Trace 查询走 Java API。

## 数据边界

- 前端只传业务输入：`question`、`kbId`、`sessionId`。
- 身份上下文只来自 JWT：`customerId`、`sub`。
- `chat_session` 和 `chat_message` 由 Java 管理。
- `agent_trace` 由 Python AI 服务写入 MySQL。
- Java 非流式端点 `POST /api/chat/messages` 保留为 REST 降级入口。

## 生产注意事项

- Java 和 Python AI 服务通过 `AI_SERVICE_BASE_URL` 通信，Python 不需要暴露公网端口。
- Java 对 AI 服务调用设置了 60 秒读取超时，超时时返回 `DOWNSTREAM_TIMEOUT` 错误。
- Nginx（Docker）或 Vite（本地开发）对 `/api/chat/messages/stream` 必须关闭 `proxy_buffering`。
- Python 侧 AI 服务存储失败时可以降级返回答案，但需要通过 `traceStore` 提示历史暂时不可写入。
- 当前 Agent 实现使用单 Agent ReAct 循环（`run_rag_agent`），先完成全部工具调用再切分推送答案。
