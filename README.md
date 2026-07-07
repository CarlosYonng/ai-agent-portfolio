# AI Agent Portfolio

这是一个把 Java 后端工程、企业知识库 RAG Agent、React 管理台和可观测性告警串在一起的全栈演示项目。当前仓库重点展示“知识库入库 -> RAG 问答 -> Trace/指标观测 -> Grafana 告警转交 Incident Copilot”的闭环。

## 项目组成

| 目录 | 说明 |
| --- | --- |
| `backend-java/` | Spring Boot 3.3.5 + Java 21 后端，按 `domain -> infrastructure -> application -> api -> boot` 五模块分层。负责认证、客户隔离、知识库、文档、聊天会话、Trace 查询、AI 服务调用和 Java 侧指标。 |
| `ai-service/` | Python FastAPI Agent 服务。提供 `/api/agent/ask`、`/api/health` 和 `/metrics`，负责 ReAct RAG 编排、检索、答案生成、引用和 Trace 持久化。 |
| `frontend/` | React + Vite 管理控制台。通过相对路径 `/api` 调 Java 后端，覆盖登录、知识库管理、RAG 问答、Trace 和用户管理。 |
| `shared/` | Python 脚本工具库，沉淀 embedding、实体抽取、MySQL/Qdrant/Neo4j 客户端等可复用逻辑。 |
| `infra/` | Docker Compose、数据库初始化和基础设施配置。 |
| `ops/monitoring/` | Prometheus 与 Grafana 配置、告警规则和 dashboard provisioning。 |
| `scripts/` | 文档入库、冒烟测试、本地服务和告警 smoke 脚本。 |
| `datasets/` | 可导入的知识库样例文档。正式业务数据建议通过上传、同步任务或外部数据源接入。 |
| `docs/` | 架构、业务流程、接口边界、知识库操作、可观测性和 Incident Copilot 集成说明。 |

## 当前主链路

1. 用户在前端创建知识库并上传文档。
2. Java `POST /api/kb/{kbId}/documents/upload` 保存文件和文档元数据，异步调用 `scripts/ingest_docs.py` 入库。
3. 入库脚本切分文档、调用真实 embedding、写入 Qdrant，并抽取实体写入 Neo4j；MySQL 保存元数据、会话、消息、Trace 和状态。
4. 前端调用 Java `POST /api/chat/messages` 发起问答。当前对外聊天接口是 REST JSON，不暴露 SSE stream 端点。
5. Java 保存 user message，同步调用 Python `/api/agent/ask`。
6. Python RAG 以 Qdrant 语义召回优先；Qdrant 无结果时按实体进入 Neo4j GraphRAG fallback；仍无结果则返回空证据，由 Agent 拒答或提示补充知识。
7. Java 保存 assistant message，前端展示答案、引用、Trace ID 和摘要块。
8. Java `/actuator/prometheus` 与 Python `/metrics` 暴露真实业务指标，Prometheus/Grafana 负责采集、看板和告警。

## 快速开始

本项目默认接真实 LLM 和 embedding 服务。未配置 `LLM_TOKEN`、`LLM_BASE_URL`、`LLM_MODEL` 等凭证时，AI 调用会明确失败，避免把 mock/offline 结果当成真实业务答案。

### 本地调试和 Docker 运行

本地调试时，Java、Python 和前端都运行在宿主机，配置读取 `.env`，服务地址使用 `localhost`。这种方式适合在 IDE 里给 Java Controller/Service、Python Agent 节点打断点。

Docker 运行时，Compose 读取 `.env.docker`，服务之间使用 `backend-java`、`ai-service` 这类容器服务名。前端由 Nginx 提供静态资源，并把 `/api` 反向代理到 Java 后端。

### 监控与告警

当前项目只保留指标暴露端点；Prometheus + Grafana 已独立到相邻项目
`monitor-server`，由它抓取本项目指标并把真实业务告警推送到
`ai-incident-copilot` 的 `/api/alerts/grafana`。详见：

- `docs/observability.md`
- `docs/incident-copilot-integration.md`

本项目 Docker 启动后暴露：

- Java metrics: `http://localhost:8080/actuator/prometheus`
- AI metrics: `http://localhost:8000/metrics`

监控服务访问入口在 `../monitor-server`：

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3001`

```bash
# Docker 完整运行
make up

# 冒烟验证
make smoke

# 可选：验证 Grafana webhook 到相邻 Incident Copilot 的字段契约
make smoke-alert
```

访问入口：

- 前端控制台：`http://localhost:3000`
- Java API：`http://localhost:8080`
- Python AI Service：`http://localhost:8000`
- Prometheus：`http://localhost:9090`
- Grafana：`http://localhost:3001`

本地断点调试时，先启动基础设施，再分别启动 Python、Java 和前端：

```bash
make infra-up
make install-frontend
make run-ai
make run-java
make run-frontend
```

本地进程读取 `.env`，服务地址使用 `localhost`；Docker Compose 读取 `.env.docker`，服务之间使用 `backend-java`、`ai-service` 等容器服务名。

## 常用接口

| 能力 | 接口 |
| --- | --- |
| Java 健康检查 | `GET /api/health` |
| OpenAPI/Apifox 导入 | `GET /api/docs/apifox-openapi.json` |
| 创建/查询知识库 | `POST /api/kb`、`GET /api/kb` |
| 上传文档 | `POST /api/kb/{kbId}/documents/upload` |
| 重试入库 | `POST /api/kb/documents/{id}/retry` |
| RAG 问答 | `POST /api/chat/messages` |
| 会话列表 | `GET /api/chat/sessions` |
| 会话消息 | `GET /api/chat/sessions/{sessionId}/messages` |
| Trace 查询 | `GET /api/traces/{traceId}`、`GET /api/traces/recent` |
| Java metrics | `GET /actuator/prometheus` |
| Python metrics | `GET /metrics` |

## 可观测性与 Incident Copilot

当前分支已经接入 Prometheus + Grafana。Grafana 告警可以通过 `INCIDENT_COPILOT_GRAFANA_WEBHOOK_URL` 推送到相邻项目 `../ai-incident-copilot` 的 `/api/alerts/grafana`，由对方负责创建或关联事件。

本仓库不内置独立 MCP 故障诊断服务；故障诊断、事件编排和 runbook 归属相邻 Incident Copilot。这里提供真实业务指标和 webhook 入站契约。

详细说明：

- `docs/observability.md`
- `docs/incident-copilot-integration.md`
- `docs/BUSINESS_FLOW.md`
- `docs/KB_OPERATION_FLOW.md`
- `docs/API_EXAMPLES.md`

## 当前状态

- Java 后端分层、认证、客户隔离、知识库、文档上传/重试、聊天会话、Trace 查询和 Micrometer 指标已接通。
- Python AI 服务具备真实模型调用、embedding、Qdrant 检索、Neo4j GraphRAG fallback、Trace 持久化和 Prometheus metrics。
- 前端控制台已覆盖知识库管理、REST 问答、引用展示和 Trace 查看。
- 文档入库会抽取错误码、API、业务术语等实体，并写入 Neo4j 的 Document-Chunk-Entity 图谱。
- 在线检索路径是 `Qdrant -> Neo4j -> NO_EVIDENCE`。MySQL 承担元数据、会话、Trace 和状态管理，不作为当前在线检索兜底。
