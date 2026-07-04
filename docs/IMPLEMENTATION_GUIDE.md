# 实施说明

这份说明告诉你项目的当前完成状态以及后续扩展方向。

## 1. 当前已经完成

### 后端（backend-java）
Spring Boot 3.3.5 + Java 21，5 层模块：
- agent-domain：领域实体（用户、会话、知识库、文档、Trace 等）
- agent-infrastructure：MyBatis Plus 数据映射器
- agent-application：业务服务、DTO、异常定义、Redis 缓存
- agent-api：Controller 分层（external/internal/ops）
- agent-boot：启动配置和装配

核心功能：
- 知识库 CRUD、文档上传/下载、文档状态管理
- 聊天会话管理（创建、历史查询、SSE 流式推送）
- JWT 认证、角色/权限控制（SUPER_ADMIN / PLATFORM_ADMIN / CUSTOMER_ADMIN / USER）
- 客户数据隔离
- Agent Trace 查询和统计

### 前端（frontend）
React 18 + Vite 5 + React Router 7 + Tailwind CSS：
- 登录/注册页面
- RAG 对话面板（支持 SSE 流式展示）
- 知识库管理面板（CRUD、文档上传）
- Trace 查看面板
- 用户管理页面（平台管理员）
- 自定义 Hook：`useAuth`、`useChat`、`useKnowledgeBase`
- API 客户端封装 `apiRequest`（统一 JWT 注入和错误解析）

### AI 服务（ai-service）
FastAPI + Pydantic v2 配置管理：
- **ReAct RAG Agent**（`run_rag_agent`）：单一 Agent 循环，支持工具调用（检索、图谱查询）
- **HybridRetriever**：三路混合检索（Qdrant 语义 + Neo4j GraphRAG + MySQL 关键词），带 degrade 降级
- **实体抽取**（`entity_extractor.py`）：规则抽取错误码、API ENDPOINT、业务术语
- **Trace 持久化**（`trace_store.py`）：写入 MySQL agent_trace 表，失败降级为日志
- **Prompt 管理**（`prompts.py`）：YAML 文件 + 代码内默认值双加载
- **Model 客户端**（`model_client.py`）：OpenAI 兼容格式，支持 chat/generate
- **Embedding**（`embedding.py`）：通过 OpenAI 兼容 API 调用真实 embedding 模型

### 数据导入脚本（scripts/）
- `scripts/ingest_docs.py`：文档入库脚本，支持 MySQL 元数据写入、真实 embedding API 调用、Qdrant 向量入库、Neo4j 图谱写入。关键依赖不可用时文档进入 FAILED 状态，支持 `--dry-run` 验证流程。
- `scripts/smoke_test.sh`：冒烟测试脚本

### 基础设施（infra/）
- docker-compose.yml 编排三个服务（backend-java / ai-service / frontend）
- 统一版本号 1.1.0

## 2. 你需要手动完成的地方

1. 安装 Docker Desktop。
2. 本地 debug 检查 `.env`；Docker 运行检查 `.env.docker`。必须填写真实 `LLM_TOKEN`、`LLM_BASE_URL`、`LLM_MODEL`。
3. 如果要接企业数据，优先走上传、同步任务或专用数据源，不把业务数据硬编码进仓库。

## 3. 首次运行

```bash
cd ai-agent-portfolio
make up
make smoke
```

`make up` 只负责拉起服务；模型凭证缺失时 AI 服务会在调用阶段明确报错，不生成离线答案。

### 3.1 本地 debug 运行方式

本地 debug 不需要把 Java 放进容器。推荐只把 MySQL、Redis、Qdrant、Neo4j 作为基础设施容器启动，然后在 IDE 或终端分别跑三个服务和前端：

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

### 3.2 数据导入链路

`make ingest-a` 会触发 `scripts/ingest_docs.py`：

1. 扫描 `datasets/kb_docs` 下 `.md` / `.txt` 文件
2. 读取正文、计算 `content_hash`
3. 按 `max_chars` 切分 Chunk
4. 写入 MySQL `kb_document`，状态 `INDEXED`
5. 调用真实 embedding API 生成向量，写入 Qdrant `kb_chunks` collection
6. 抽取实体（错误码、API 路径、业务术语），写入 Neo4j 图谱
7. 任一关键依赖失败时文档进入 `FAILED` 状态

### 3.3 Qdrant 和 Neo4j 选型说明

- Qdrant 负责向量检索，适合文档 chunk 的语义召回，支持 payload filter（客户、知识库、权限过滤）。
- Neo4j 负责知识图谱，适合表达 API、错误码、系统、模块、文档片段之间的关系，支持 GraphRAG 扩展。
- 可替代方案：向量库可替换为 Milvus、pgvector、Elasticsearch/OpenSearch；图数据库可替换为 NebulaGraph、TuGraph。

### 3.4 当前检索顺序

1. Qdrant 向量检索（真实 embedding 模型）
2. Neo4j GraphRAG 实体图谱召回
3. MySQL FULLTEXT/LIKE 关键词检索
4. 无证据时返回 NO_EVIDENCE，由 Verifier 拒答或提示补充知识

## 4. 后续开发方向

1. **Reranker**：对多路召回结果重排，提升回答质量
2. **System Prompt 优化闭环**：基于 Trace 和人工复盘，迭代 `default.yaml`
3. **ACL 过滤增强**：部门、角色级别的文档可见性
4. **故障诊断 Agent**：日志/代码/工单导入 + MCP 工具协议 + 诊断 Agent
5. **测试覆盖**：Java Controller 集成测试 + Testcontainers
