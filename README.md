# AI Agent Portfolio

这是一个把 Java 后端经验和大模型 Agent/RAG 工程能力合在一起项目。

## 项目组成

- `backend-java/`: Spring Boot 后端入口，负责用户、知识库、会话、文档元数据、审计日志和调用 AI 服务。
- `frontend/`: React + Vite 前端控制台，覆盖知识库、RAG 问答和 Trace 查询。
- `ai-service/`: Python FastAPI 服务，负责 RAG、Agent 编排、检索和引用生成。
- `infra/`: docker-compose、MySQL 初始化脚本和基础设施配置。
- `scripts/`: 数据导入、冒烟测试和本地服务脚本。
- `datasets/`: 可导入知识文档；正式业务数据通过上传、同步任务或外部数据源接入。
- `docs/BUSINESS_FLOW.md`: 业务流程说明，包含 RAG 和故障诊断两条主链路。
- `docs/KB_OPERATION_FLOW.md`: 知识库管理、导入索引、RAG 问答和删除清理流程图。
- `docs/RAG_PROMPT_OPTIMIZATION_PLAN.md`: 后续基于 RAG 回答情况优化 system prompt 的设想，当前阶段不落代码。

## 先做什么

1. 本地 debug 用 `.env`，Docker 一键运行用 `.env.docker`。
2. 执行 `make up` 启动完整 Docker 服务，浏览器访问 `http://localhost:3000`。
3. 本地断点调试时先在 `../ai-agent-infra-stack` 启动中间件，再分别执行 `make run-ai`、`make run-java`、`make run-frontend`。
4. 配置真实模型和 Embedding 凭证后，执行 `make ingest-a` 导入知识文档。
5. 执行 `make smoke` 验证服务。
6. 执行 `make test` 运行 Java、Python 和前端单元测试。

AI 服务现在默认接真实模型服务。未配置 `LLM_TOKEN`、`LLM_BASE_URL`、`LLM_MODEL` 时会明确失败，避免把离线结果当成真实业务答案。

## 监控与告警

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

## 本地调试和 Docker 运行

本地调试时，Java、Python 和前端都运行在宿主机，配置读取 `.env`，服务地址使用 `localhost`。这种方式适合在 IDE 里给 Java Controller/Service、Python Agent 节点打断点。

Docker 运行时，Compose 读取 `.env.docker`，服务之间使用 `backend-java`、`ai-service` 这类容器服务名。前端由 Nginx 提供静态资源，并把 `/api` 反向代理到 Java 后端。

```bash
# Docker 完整运行
make up

# 本地 debug 基础设施
cd ../ai-agent-infra-stack && docker compose up -d
cd ../ai-agent-portfolio
make install-frontend
make run-ai
make run-java
make run-frontend
```

## Apifox 接口文档

Java 后端启动后，Apifox 可直接导入：

```text
http://localhost:8080/api/docs/apifox-openapi.json
```

该文档是标准 OpenAPI 3.0 JSON，覆盖认证、聊天、知识库、文档、Trace、客户和用户管理接口。

## 目前状态

当前骨架已完成，核心链路均已打通：

- Java 后端项目结构和核心接口。
- Python Agent 服务结构、可替换模型客户端、Qdrant 检索优先的 HybridRetriever。
- Agent Ops 自身异常可推送到外部诊断服务，推送失败不影响主流程。
- MySQL 表结构已补充表级和字段级中文 `COMMENT`，方便排查和交接数据模型。
- Qdrant/Neo4j/Docker 编排。
- 文档导入时会抽取错误码、API、业务术语，并写入 Neo4j 的 Document-Chunk-Entity 图谱。
- RAG 检索链路已支持 Qdrant -> Neo4j GraphRAG -> MySQL 的降级顺序。
- Python Agent 已补充单元测试，覆盖 RAG 编排和拒答校验。
- Java 后端已补充 ChatService 会话标题逻辑单元测试。

后续你可以按 `docs/IMPLEMENTATION_GUIDE.md` 和 `docs/BUSINESS_FLOW.md` 的步骤继续实现完整业务逻辑。
