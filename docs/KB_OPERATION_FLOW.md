# 知识库相关操作流程图

本文档聚焦知识库从管理台维护、文档导入索引，到 RAG 问答和删除清理的完整操作链路。

## 1. 总体链路

```mermaid
flowchart TD
    U["知识库管理员 / 用户"] --> F["React 控制台"]

    F -->|知识库 CRUD| KBAPI["Java /api/kb"]
    KBAPI --> KBS["KnowledgeBaseService"]
    KBS --> KBSpace["MySQL kb_space"]
    KBS --> KBDoc["MySQL kb_document"]

    F -->|登记文档元数据| DocAPI["POST /api/kb/documents"]
    DocAPI --> KBDoc
    KBDoc --> Pending["status = PENDING"]

    AdminScript["make ingest-a / scripts/ingest_docs.py"] --> Parser["读取 Markdown / TXT"]
    Parser --> Chunk["切分 Chunk"]
    Chunk --> MysqlChunk["MySQL kb_doc_chunk"]
    Chunk --> Embed["生成 Embedding"]
    Embed --> Qdrant["Qdrant kb_chunks"]
    Chunk --> Entity["抽取实体"]
    Entity --> Neo4j["Neo4j Document / Chunk / Entity 图谱"]
    Chunk --> Indexed["kb_document.status = INDEXED"]

    F -->|RAG 问答 SSE| ChatAPI["Java /api/chat/stream"]
    ChatAPI --> ChatSvc["ChatService 保存 user message"]
    ChatSvc --> AIAsk["Python /api/agent/ask"]
    AIAsk --> RagAgent["RAG Agent 编排"]
    RagAgent --> Retriever["HybridRetriever"]
    Retriever --> Qdrant
    Retriever --> Neo4j
    Retriever --> MysqlChunk
    RagAgent --> Answer["AnswerAgent 生成答案"]
    Answer --> Verify["VerifierAgent 证据校验"]
    Verify --> Trace["MySQL agent_trace"]
    Verify --> ChatSvc
    ChatSvc --> AssistantMsg["保存 assistant message"]
    ChatSvc --> F
```

## 2. 管理台操作流

```mermaid
flowchart LR
    A["进入知识库页"] --> B["查询租户知识库列表<br/>GET /api/kb?tenantId=1"]
    B --> C{是否已有知识库}
    C -->|否| D["创建知识库<br/>POST /api/kb"]
    C -->|是| E["选择知识库"]
    D --> E
    E --> F["查询文档列表<br/>GET /api/kb/{kbId}/documents"]
    F --> G{操作类型}
    G -->|新增文档| H["登记文档元数据<br/>POST /api/kb/documents<br/>status=PENDING"]
    G -->|编辑知识库| I["更新名称/描述/可见性<br/>PUT /api/kb/{id}"]
    G -->|编辑文档| J["更新标题/来源<br/>PUT /api/kb/documents/{id}"]
    G -->|更新状态| K["PATCH /api/kb/documents/{id}/status"]
    G -->|删除文档| L["DELETE /api/kb/documents/{id}"]
    G -->|删除知识库| M["DELETE /api/kb/{id}"]
    H --> N["等待导入脚本解析并写索引"]
    L --> O["清理 MySQL 文档、Chunk、kg_relation 元数据"]
    M --> O
```

## 3. 文档导入与索引流

```mermaid
flowchart TD
    A["执行 make ingest-a"] --> B["scripts/ingest_docs.py"]
    B --> C["扫描 datasets/kb_docs 下 .md / .txt"]
    C --> D["读取正文并计算 content_hash"]
    D --> E["按 max_chars 切分 Chunk"]
    E --> F{MySQL 可用}
    F -->|是| G["写 kb_document<br/>status=INDEXED"]
    F -->|否| H["dry-run 打印导入过程"]
    G --> I["写 kb_doc_chunk"]
    H --> J["生成临时 doc_id / chunk_id"]
    I --> K["生成 Embedding"]
    J --> K
    K --> L{Qdrant 可用}
    L -->|是| M["upsert 到 kb_chunks<br/>payload 保存 tenant/kb/doc/chunk/title/preview"]
    L -->|否| N["跳过向量写入并告警"]
    E --> O["抽取实体"]
    O --> P{Neo4j 可用}
    P -->|是| Q["写 Document-Chunk-Entity 图谱"]
    P -->|否| R["跳过图谱写入并告警"]
    M --> S["文档可被语义召回"]
    Q --> T["文档可被 GraphRAG 召回"]
    I --> U["文档可被 MySQL FULLTEXT/LIKE 兜底召回"]
```

## 4. RAG 问答流

```mermaid
flowchart TD
    A["用户输入问题"] --> B["Java ChatService"]
    B --> C{是否已有 sessionId}
    C -->|否| D["创建 chat_session"]
    C -->|是| E["复用会话"]
    D --> F["写 chat_message:user"]
    E --> F
    F --> G["调用 Python /api/agent/ask"]
    G --> H["RouterAgent<br/>判断问题类型"]
    H --> I["RewriteAgent<br/>改写检索 query 和 filters"]
    I --> J["RetrieverAgent<br/>HybridRetriever"]
    J --> K{Qdrant 有结果}
    K -->|是| L["返回语义召回证据"]
    K -->|否| M{Neo4j 有实体召回}
    M -->|是| N["返回图谱召回证据"]
    M -->|否| O{MySQL 有关键词结果}
    O -->|是| P["返回 FULLTEXT/LIKE 证据"]
    O -->|否| Q["返回 mock 证据<br/>仅用于本地演示"]
    L --> R["AnswerAgent<br/>基于证据生成答案"]
    N --> R
    P --> R
    Q --> R
    R --> S{VerifierAgent<br/>是否有证据}
    S -->|有| T["通过答案"]
    S -->|无| U["拒答并标记 NO_EVIDENCE"]
    T --> V["写 agent_trace"]
    U --> V
    V --> W["返回 answer / citations / traceId"]
    W --> X["Java 切成 SSE metadata/delta/citations/done"]
    X --> Y["写 chat_message:assistant"]
    Y --> Z["前端展示答案、引用和 Trace"]
```

## 5. 文档状态流转

```mermaid
stateDiagram-v2
    [*] --> PENDING: 管理台登记文档
    PENDING --> INDEXED: 导入脚本解析、切片、写索引成功
    PENDING --> FAILED: 解析或索引失败
    FAILED --> PENDING: 管理员修正来源后重试
    INDEXED --> PENDING: 文档内容变更后等待重建索引
    INDEXED --> [*]: 删除文档或删除知识库
```

## 6. 删除与降级边界

```mermaid
flowchart TD
    A["删除文档或知识库"] --> B["Java KnowledgeBaseService"]
    B --> C["查询文档下 Chunk"]
    C --> D["先删 kg_relation<br/>避免外键阻断"]
    D --> E["删除 kb_doc_chunk"]
    E --> F["删除 kb_document"]
    F --> G["删除 kb_space（仅删除知识库时）"]
    F --> H["Qdrant / Neo4j 在线索引<br/>由重建脚本或运维任务按 doc_id 兜底清理"]

    I["查询时基础设施不可用"] --> J{可用召回源}
    J -->|Qdrant 可用| K["语义召回"]
    J -->|Qdrant 不可用，Neo4j 可用| L["图谱召回"]
    J -->|图谱不可用，MySQL 可用| M["关键词兜底"]
    J -->|数据库都不可用| N["mock 证据，本地演示兜底"]
```

## 7. 操作和存储映射

| 操作 | 前端入口 | Java 接口 | 主要存储 | 说明 |
| --- | --- | --- | --- | --- |
| 创建知识库 | 知识库空间表单 | `POST /api/kb` | `kb_space` | 创建租户级知识库入口 |
| 查询知识库 | 当前知识库下拉框 | `GET /api/kb?tenantId=...` | `kb_space` | 按租户隔离 |
| 更新知识库 | 编辑空间信息 | `PUT /api/kb/{id}` | `kb_space` | 不触发索引重建 |
| 删除知识库 | 删除按钮 | `DELETE /api/kb/{id}` | `kb_space`、`kb_document`、`kb_doc_chunk` | 外部索引由脚本兜底 |
| 登记文档 | 文档表单 | `POST /api/kb/documents` | `kb_document` | 默认 `PENDING` |
| 导入文档 | `make ingest-a` | `scripts/ingest_docs.py` | MySQL、Qdrant、Neo4j | 解析、切片、向量、图谱 |
| RAG 问答 | RAG 调试台 | `/api/chat/stream` -> `/api/agent/ask` | `chat_message`、`agent_trace` | 返回答案、引用、traceId |
