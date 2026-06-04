# AI Agent Portfolio

这是一个把 Java 后端经验和大模型 Agent/RAG 工程能力合在一起项目。

## 项目组成

- `backend-java/`: Spring Boot 后端入口，负责用户、知识库、会话、文档元数据、审计日志和调用 AI 服务。
- `frontend/`: React + Vite 前端控制台，覆盖知识库、RAG 问答、故障诊断和 Trace 查询。
- `ai-service/`: Python FastAPI 服务，负责 RAG、Agent 编排、检索、引用生成和评测。
- `mcp-server/`: MCP 风格工具服务，负责日志检索、代码检索、工单检索和报告生成。
- `infra/`: docker-compose、MySQL 初始化脚本和基础设施配置。
- `scripts/`: 数据导入、评测、冒烟测试和后续微调脚本。
- `datasets/`: 示例知识库、日志、历史工单和评测样本。
- `docs/BUSINESS_FLOW.md`: 业务流程说明，包含 RAG 和故障诊断两条主链路。

## 先做什么

1. 本地 debug 用 `.env`，Docker 一键运行用 `.env.docker`。
2. 执行 `make up` 启动完整 Docker 服务，浏览器访问 `http://localhost:3000`。
3. 本地断点调试时先执行 `make infra-up`，再分别执行 `make run-mcp`、`make run-ai`、`make run-java`、`make run-frontend`。
4. 执行 `make ingest-a` 导入知识库样例。
5. 执行 `make ingest-b` 导入故障诊断样例。
6. 执行 `make smoke` 验证服务。
7. 执行 `make test` 运行 Java、Python 和前端单元测试。

如果没有配置模型凭证，AI 服务会使用 mock 模型回答，但 Agent 链路、Trace、Qdrant 检索仍然可以开发和演示。

## 本地调试和 Docker 运行

本地调试时，Java、Python 和前端都运行在宿主机，配置读取 `.env`，服务地址使用 `localhost`。这种方式适合在 IDE 里给 Java Controller/Service、Python Agent 节点打断点。

Docker 演示时，Compose 读取 `.env.docker`，服务之间使用 `backend-java`、`ai-service`、`mcp-server` 这类容器服务名。前端由 Nginx 提供静态资源，并把 `/api` 反向代理到 Java 后端。

```bash
# Docker 完整演示
make up

# 本地 debug 基础设施
make infra-up
make install-frontend
make run-mcp
make run-ai
make run-java
make run-frontend
```

## 无依赖快速演示

如果你还没启动 Docker，也可以先运行本地演示脚本：

```bash
python3 scripts/local_demo.py
```

它会展示两个效果：

- 企业知识库 RAG Agent：实体抽取、证据召回、引用回答。
- Java 微服务故障诊断 Agent：日志、代码、工单证据聚合，输出根因和处理建议。

## 目前状态

当前是第一版可开发骨架，重点已经落好：

- Java 后端项目结构和核心接口。
- Python Agent 服务结构、可替换模型客户端、Qdrant 检索优先的 HybridRetriever。
- MCP 风格工具服务结构，日志、代码和工单查询会优先读 MySQL。
- Java 微服务故障诊断 Agent 已有 `/api/incident/diagnose` 和 Java 后端转发入口。
- MySQL 表结构。
- MySQL 表结构已补充表级和字段级中文 `COMMENT`，方便面试时直接讲数据模型。
- Qdrant/Neo4j/Docker 编排。
- 文档导入时会抽取错误码、API、业务术语，并写入 Neo4j 的 Document-Chunk-Entity 图谱。
- RAG 检索链路已支持 Qdrant -> Neo4j GraphRAG -> MySQL -> mock 的降级顺序。
- Python Agent 已补充单元测试，覆盖 RAG 编排、拒答校验和故障根因规则。
- Java 后端已补充 ChatService 会话标题逻辑单元测试。
- 示例数据和自动化脚本。

后续你可以按 `docs/IMPLEMENTATION_GUIDE.md` 和 `docs/BUSINESS_FLOW.md` 的步骤继续实现完整业务逻辑。
