# 实施说明

本文档说明项目当前已经完成的能力、运行方式和后续扩展方向。描述以当前代码为准，避免把历史规划当成已实现功能。

## 1. 当前已经完成

### 1.1 后端：`backend-java/`

Spring Boot 3.3.5 + Java 21，五模块分层：

| 模块 | 职责 |
| --- | --- |
| `agent-domain` | 领域实体：用户、客户、会话、知识库、文档、Trace 等。 |
| `agent-infrastructure` | MyBatis Plus、外部客户端、Redis、MySQL/Qdrant/Neo4j 集成实现。 |
| `agent-application` | 业务服务、DTO、异常定义、文档入库编排、AI 服务调用。 |
| `agent-api` | Controller 分层：external、internal、ops。 |
| `agent-boot` | 启动配置和装配。 |

核心能力：

- JWT 登录、角色权限和客户数据隔离。
- 知识库 CRUD、文档上传/下载、文档状态管理和重试入库。
- 聊天会话管理：创建会话、保存用户/助手消息、查询历史。
- RAG REST 问答入口：`POST /api/chat/messages`。
- Trace 查询和统计。
- Micrometer 指标与 `GET /actuator/prometheus`。
- Apifox/OpenAPI 导出：`GET /api/docs/apifox-openapi.json`。

### 1.2 前端：`frontend/`

React 18 + Vite 5 + React Router 7 + Tailwind CSS：

- 登录/注册页面。
- RAG 对话面板，当前通过 REST JSON 调用 Java `POST /api/chat/messages`。
- 知识库管理面板，支持 CRUD、文档上传和状态查看。
- Trace 查看面板。
- 用户管理页面。
- `apiRequest` 统一注入 JWT 并处理错误。

### 1.3 AI 服务：`ai-service/`

FastAPI + Pydantic v2：

- `GET /api/health`：健康检查。
- `POST /api/agent/ask`：RAG Agent 问答。
- `GET /metrics`：Python 侧 Prometheus 指标。
- ReAct RAG Agent：有 `kb_id` 时走直接检索 + 基于证据生成答案的快路径；其他情况保留 ReAct 工具调用编排。
- HybridRetriever：Qdrant 语义召回优先；Qdrant 无结果时进入 Neo4j GraphRAG fallback；仍无结果返回空证据。
- 实体抽取：规则抽取错误码、API endpoint、业务术语等。
- Trace 持久化：写入 MySQL `agent_trace`，失败时记录日志并降级。
- Prompt 管理：YAML 文件 + 代码默认值双加载。
- Model/Embedding 客户端：OpenAI 兼容 API，调用真实模型服务。

### 1.4 数据入库：`scripts/`

- `scripts/ingest_docs.py`：文档入库脚本，支持 MySQL 元数据、真实 embedding、Qdrant 向量写入、Neo4j 图谱写入和 `--dry-run`。
- Java 上传链路会保存文件和文档记录，然后异步调用入库脚本。
- `POST /api/kb/documents/{id}/retry` 可重试失败文档。
- `scripts/smoke_test.sh`：服务冒烟测试。
- `make smoke-alert`：验证 Grafana webhook 到 Incident Copilot 的 payload 契约。

### 1.5 基础设施与观测

- Docker Compose 编排 Java、Python、前端和依赖服务。
- Prometheus 采集 Java `/actuator/prometheus` 与 Python `/metrics`。
- Grafana dashboard 和 alert rule 位于 `ops/monitoring/`。
- Grafana webhook 可推送到相邻项目 `ai-incident-copilot` 的 `/api/alerts/grafana`。

## 2. 首次运行

```bash
cd ai-agent-portfolio
make up
make smoke
```

`make up` 负责拉起服务。模型凭证缺失时，AI 服务会在调用阶段明确报错，不生成离线答案。

### 2.1 本地 debug

本地 debug 推荐只把 MySQL、Redis、Qdrant、Neo4j、Prometheus、Grafana 等作为基础设施容器启动，然后在 IDE 或终端分别运行 Java、Python 和前端：

```bash
make infra-up
make install-frontend
make run-ai
make run-java
make run-frontend
```

- `.env` 使用 `localhost`，适合本地进程互相调用。
- `.env.docker` 使用 Compose service name，适合容器之间互相调用。
- 前端本地开发通过 Vite 代理 `/api -> http://localhost:8080`。
- Docker 前端通过 Nginx 代理 `/api -> http://backend-java:8080`。

## 3. 数据入库链路

文档有两种入口：

1. 管理台上传：`POST /api/kb/{kbId}/documents/upload`。
2. 开发样例导入：`make ingest-a` 调用 `scripts/ingest_docs.py` 扫描 `datasets/kb_docs`。

入库过程：

1. 读取正文并计算 `content_hash`。
2. 按 `max_chars` 切分 chunk。
3. 写入 MySQL 文档和 chunk 元数据。
4. 调用真实 embedding API 生成向量。
5. 写入 Qdrant `kb_chunks` collection。
6. 抽取实体并写入 Neo4j Document-Chunk-Entity 图谱。
7. 任一关键依赖失败时文档进入 `FAILED`，可通过重试接口重新入库。

## 4. 当前检索策略

1. Qdrant 向量检索：负责语义召回，支持按客户、知识库等 payload filter。
2. Neo4j GraphRAG fallback：当 Qdrant 无结果时，按实体关系召回关联 chunk。
3. NO_EVIDENCE：两者都没有证据时，Agent 拒答或提示补充知识。

MySQL 当前不承担在线关键词检索兜底，只保存元数据、状态、聊天会话和 Trace。

## 5. 后续开发方向

1. Reranker：对召回结果重排，提升回答质量。
2. System Prompt 优化闭环：基于 Trace 和人工复盘迭代 `default.yaml`。
3. ACL 过滤增强：支持部门、角色级别的文档可见性。
4. 入库任务化：引入 outbox/worker，让外部索引清理和重建具备更强补偿能力。
5. 测试覆盖：补充 Java Controller 集成测试、Python 检索集成测试和前端关键流程测试。
6. 诊断联动：继续通过 Prometheus/Grafana webhook 与相邻 Incident Copilot 对接，故障诊断 Agent 不在本仓库内实现。
