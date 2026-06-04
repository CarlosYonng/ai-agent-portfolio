# 实施说明

这份说明告诉你如何继续把当前骨架做成完整项目。

## 1. 当前已经完成

- `backend-java`：Spring Boot API 骨架，包括知识库、文档登记、聊天问答、Trace 查询。
- `frontend`：React + Vite 控制台，Nginx Docker 镜像负责静态资源和 `/api` 代理。
- `ai-service`：FastAPI Agent 服务，包括 Router、Rewrite、Retriever、Answer、Verifier 的固定链路。
- `mcp-server`：MCP 风格工具服务，包括日志查询、代码搜索、工单检索、报告生成。
- `infra`：MySQL、Redis、Qdrant、Neo4j、三个服务的 docker-compose 编排。
- `scripts`：文档导入、代码导入、日志导入、评测、冒烟测试、微调占位脚本。
- `datasets`：知识库文档、模拟日志、历史工单、评测样本。

## 2. 你需要手动完成的地方

这些事情需要你的本地环境或账号权限，我不能替你完成：

1. 安装 Docker Desktop。
2. 本地 debug 检查 `.env`；Docker 演示检查 `.env.docker`。需要真实模型时填写 `LLM_TOKEN`、`LLM_BASE_URL`、`LLM_MODEL`。
4. 如果要微调，需要准备 GPU 环境，安装 `transformers`、`peft`、`accelerate`。
5. 如果要接真实企业数据，需要替换 `datasets/` 中的模拟数据。

## 3. 首次运行

```bash
cd ai-agent-portfolio
make up
make smoke
```

如果你还没准备模型凭证，也可以直接 `make up`。默认 `.env.docker` 里使用 mock 模型，先把工程链路跑通。

## 3.1 本地 debug 运行方式

本地 debug 不需要把 Java 放进容器。推荐只把 MySQL、Redis、Qdrant、Neo4j 作为基础设施容器启动，然后在 IDE 或终端分别跑三个服务和前端：

```bash
make infra-up
make install-frontend
make run-mcp
make run-ai
make run-java
make run-frontend
```

- `.env` 使用 `localhost`，适合本地进程互相调用。
- `.env.docker` 使用 Compose service name，适合容器之间互相调用。
- 前端本地开发通过 Vite 代理 `/api -> http://localhost:8080`。
- Docker 前端通过 Nginx 代理 `/api -> http://backend-java:8080`。

如果 `make up` 失败，优先看 Docker Desktop 是否启动、端口 `3000/3306/6379/6333/7474/7687/8080/8000/8100` 是否被占用。

## 3.2 数据导入链路

现在导入脚本已经不是单纯打印：

- `scripts/ingest_docs.py` 会尝试写入 `kb_document`、`kb_doc_chunk`，并将本地 hash embedding 写入 Qdrant 的 `kb_chunks` collection。
- 同一个脚本会抽取错误码、API 路径和业务术语，并写入 Neo4j 的 `Document -> Chunk -> Entity` 图谱。
- `scripts/ingest_logs.py` 会尝试写入 `obs_log_event`。
- `scripts/ingest_tickets.py` 会尝试写入 `incident_ticket`。

如果 MySQL、Qdrant 或 Python 依赖不可用，脚本会自动降级为 dry-run。这样做是为了让你在没有完整环境时也能演示处理流程。

本地 hash embedding 仅用于打通工程链路，不代表真实语义效果。准备简历项目最终版时，建议替换为：

- `BAAI/bge-large-zh-v1.5`
- `bge-m3`
- OpenAI `text-embedding-3-large`
- Qwen embedding 兼容服务

## 4. 下一步开发顺序

### 第一步：让 AI 服务写入 Trace

已完成基础版：`ai-service/app/core/trace_store.py` 会优先写入 MySQL 的 `agent_trace` 表，失败时降级为日志打印。

### 第二步：让文档导入脚本真正写库

已完成基础版：

- 写入 `kb_document`
- 写入 `kb_doc_chunk`
- 使用本地 hash embedding
- upsert 到 Qdrant

待增强：

- 替换真实 embedding 模型
- 用 LLM/NER 模型替换当前规则实体抽取
- 在问答链路里加入 Neo4j 邻居实体召回

## 3.3 Qdrant 和 Neo4j 选型说明

Qdrant 和 Neo4j 都是主流选择，适合简历项目：

- Qdrant 负责向量检索，适合文档 chunk 的语义召回，并支持 payload filter，便于做租户、知识库、权限过滤。
- Neo4j 负责知识图谱，适合表达 API、错误码、系统、模块、文档片段之间的关系，后续可扩展 GraphRAG。
- 如果面试官问替代方案，可以说：向量库可替换为 Milvus、pgvector、Elasticsearch/OpenSearch；图数据库可替换为 NebulaGraph、TuGraph，但 Neo4j 的生态和 Cypher 表达更适合单人快速落地。

### 第三步：实现真实 Qdrant 检索

已完成基础版：`ai-service/app/retrieval/hybrid_retriever.py` 会使用本地 hash embedding 查询 Qdrant，并按 `tenant_id` 做 payload filter。Qdrant 没有结果时，会继续使用 MySQL FULLTEXT/LIKE 做关键词检索。

当前实际检索顺序：

1. Qdrant 向量检索
2. Neo4j GraphRAG 实体图谱召回
3. MySQL FULLTEXT/LIKE 关键词检索
4. mock 兜底证据

待增强：

- 替换真实 embedding 模型
- 增加 ACL 部门过滤
- 增加 MySQL FULLTEXT 优化或 Elasticsearch/OpenSearch BM25
- 增加 reranker

### 第四步：实现 BM25 和图谱检索

- MySQL 使用 FULLTEXT/LIKE，后续可替换为 Elasticsearch/OpenSearch
- Neo4j 已按实体名查询相关 chunk 证据，后续可扩展为实体邻居、多跳路径和关系类型过滤
- 合并三路召回结果后做 rerank

### 第五步：接入真实 MCP 工具

把 `mcp-server` 里的 mock 返回改成真实查询：

- `search_logs` 查询 `obs_log_event`，已完成基础版
- `search_code` 查询 `code_symbol`，已完成基础版
- `search_tickets` 查询 `incident_ticket`，已完成基础版

### 第七步：故障诊断 Agent

已完成基础版：

- AI 服务新增 `/api/incident/diagnose`
- Java 后端新增 `/api/incidents/diagnose`
- 诊断 Agent 会聚合日志、代码、历史工单证据
- 输出 summary、root_causes、actions、evidences

待增强：

- `search_code` 后续可从 MySQL 关键词检索升级为 Qdrant 代码向量检索
- 引入真实指标数据，如 QPS、P95、错误率
- 把诊断报告写入数据库并生成 Markdown/PDF

### 第六步：补测试和评测

- Python Agent 单元测试已完成基础版，覆盖 RAG 编排、Verifier 拒答和故障根因规则。
- Java 单元测试已完成基础版，覆盖 ChatService 会话标题生成。
- 后续继续补 Java Controller 集成测试和带 Testcontainers 的 MySQL 集成测试。
- `scripts/run_rag_eval.py` 调用真实 `/api/agent/ask`，计算 Recall@5。

运行命令：

```bash
make test-python
make test-java
```

如果本机没有安装 Python 依赖或 Maven，可以先在 Docker/IDE 环境中安装依赖后运行；当前仓库已经提供测试代码和 Makefile 入口。

## 5. 面试演示脚本

你可以按这个顺序演示：

1. 展示 `docker-compose.yml`，说明基础设施。
2. 展示 MySQL 表结构和 Qdrant collection 设计。
3. 运行 `make ingest-a`，说明文档如何进入知识库。
4. 调用 `/api/chat/messages`，展示答案和引用。
5. 展示 Agent 链路：Router -> Rewrite -> Retriever -> Answer -> Verifier。
6. 运行故障诊断工具：`/api/tools/search_logs`、`/api/tools/search_code`、`/api/tools/search_tickets`。
7. 展示评测集和 Recall@5 评测思路。

## 6. 简历描述重点

简历上不要只写“调用大模型 API”。要强调：

- 多路检索：向量检索 + 关键词检索 + 图谱检索。
- 多 Agent：路由、改写、检索、重排、回答、验证。
- 工程化：权限过滤、Trace、日志、评测、Docker 部署。
- 后端经验迁移：Java 微服务故障诊断、日志、代码、工单、Runbook。
