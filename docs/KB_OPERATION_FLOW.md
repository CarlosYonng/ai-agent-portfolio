# 知识库相关操作流程图

本文档聚焦知识库从管理台维护、文档上传/导入索引，到 RAG 问答和删除清理的完整操作链路。

## 1. 总体链路

```mermaid
flowchart TD
    U["知识库管理员 / 用户"] --> F["React 控制台"]

    F -->|知识库 CRUD| KBAPI["Java /api/kb"]
    KBAPI --> KBS["KnowledgeBaseService"]
    KBS --> KBSpace["MySQL kb_space"]
    KBS --> KBDoc["MySQL kb_document"]

    F -->|上传文档| UploadAPI["POST /api/kb/{kbId}/documents/upload"]
    UploadAPI --> StoreFile["保存文件与文档元数据"]
    StoreFile --> Pending["status = PENDING"]
    Pending --> Ingest["异步调用 scripts/ingest_docs.py"]

    Ingest --> Parser["解析 Markdown / TXT"]
    Parser --> Chunk["切分 Chunk"]
    Chunk --> Metadata["写 MySQL 元数据"]
    Chunk --> Embed["生成 Embedding（真实 API）"]
    Embed --> Qdrant["Qdrant kb_chunks"]
    Chunk --> Entity["抽取实体"]
    Entity --> Neo4j["Neo4j Document / Chunk / Entity 图谱"]
    Metadata --> Indexed["kb_document.status = INDEXED"]
    Qdrant --> Indexed
    Neo4j --> Indexed

    F -->|RAG 问答 REST| ChatAPI["POST /api/chat/messages"]
    ChatAPI --> ChatSvc["ChatService 保存 user message"]
    ChatSvc --> AIAsk["Python /api/agent/ask"]
    AIAsk --> RagAgent["RAG Agent 编排"]
    RagAgent --> Retriever["HybridRetriever"]
    Retriever --> Qdrant
    Retriever --> Neo4j
    RagAgent --> Trace["MySQL agent_trace"]
    RagAgent --> ChatSvc
    ChatSvc --> AssistantMsg["保存 assistant message"]
    AssistantMsg --> F["返回 answer / citations / traceId"]
```

## 2. 管理台操作流

```mermaid
flowchart LR
    A["进入知识库页"] --> B["查询客户知识库列表<br/>GET /api/kb"]
    B --> C{是否已有知识库}
    C -->|否| D["创建知识库<br/>POST /api/kb"]
    C -->|是| E["选择知识库"]
    D --> E
    E --> F["查询文档列表<br/>GET /api/kb/{kbId}/documents"]
    F --> G{操作类型}
    G -->|上传文档| H["上传文件<br/>POST /api/kb/{kbId}/documents/upload"]
    G -->|重试失败| I["重试入库<br/>POST /api/kb/documents/{id}/retry"]
    G -->|编辑知识库| J["更新名称/描述/可见性<br/>PUT /api/kb/{id}"]
    G -->|查看状态| K["轮询文档列表<br/>GET /api/kb/{kbId}/documents"]
    G -->|删除文档| L["DELETE /api/kb/documents/{id}"]
    G -->|删除知识库| M["DELETE /api/kb/{id}"]
    H --> N["等待异步入库"]
    I --> N
    L --> O["清理 MySQL 文档元数据 + Qdrant 向量 + Neo4j 图谱"]
    M --> O
```

## 3. 文档导入与索引流

```mermaid
flowchart TD
    A["上传文档或执行 make ingest-a"] --> B["scripts/ingest_docs.py"]
    B --> C["读取正文并计算 content_hash"]
    C --> D["按 max_chars 切分 Chunk"]
    D --> E["写 kb_document / chunk 元数据"]
    E --> F["生成 Embedding（真实 API）"]
    F --> G["upsert 到 Qdrant kb_chunks"]
    D --> H["抽取错误码 / API / 业务术语"]
    H --> I["写 Neo4j Document-Chunk-Entity 图谱"]
    G --> J["文档可被语义召回"]
    I --> K["文档可被 GraphRAG 召回"]
    F -->|失败| X["status = FAILED"]
    G -->|失败| X
    I -->|失败| X
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
    G --> H["RAG Agent 编排"]
    H --> I["HybridRetriever"]
    I --> J{Qdrant 有结果}
    J -->|是| K["返回语义召回证据"]
    J -->|否| L{Neo4j 有实体召回}
    L -->|是| M["返回图谱召回证据"]
    L -->|否| N["返回 NO_EVIDENCE"]
    K --> O["基于证据生成答案"]
    M --> O
    N --> P["拒答或提示补充知识"]
    O --> Q["写 agent_trace"]
    P --> Q
    Q --> R["返回 answer / citations / traceId"]
    R --> S["Java 写 chat_message:assistant"]
    S --> T["前端展示答案、引用和 Trace"]
```

当前没有对外 `/api/chat/messages/stream` 端点；前端使用 REST JSON 等待完整结果后渲染。

## 5. 文档状态流转

```mermaid
stateDiagram-v2
    [*] --> PENDING: 上传或登记文档
    PENDING --> INDEXING: 入库脚本开始处理
    INDEXING --> INDEXED: 解析、切片、向量、图谱写入成功
    INDEXING --> FAILED: 解析或索引失败
    FAILED --> PENDING: 管理员重试
    INDEXED --> PENDING: 文档内容变更后等待重建索引
    INDEXED --> [*]: 删除文档或删除知识库
```

## 6. 删除与降级边界

```mermaid
flowchart TD
    A["删除文档或知识库"] --> B["Java KnowledgeBaseService"]
    B --> C["读取 kb_space / kb_document<br/>拿到 customer_id、kb_id、doc_id"]
    C --> D["同步删除 Qdrant points<br/>按 customer_id + kb_id/doc_id 过滤"]
    D --> E["同步删除 Neo4j Document/Chunk/MENTIONS<br/>并清理孤立 Entity"]
    E --> F["删除 kb_document"]
    F --> G["删除 kb_space（仅删除知识库时）"]
    D -->|失败| X["抛出 KB_INDEX_CLEANUP_FAILED<br/>MySQL 事务回滚"]
    E -->|失败| X

    I["查询时基础设施不可用"] --> J{可用召回源}
    J -->|Qdrant 可用| K["语义召回"]
    J -->|Qdrant 无结果，Neo4j 可用| L["图谱召回"]
    J -->|均无证据| M["NO_EVIDENCE<br/>不生成无来源内容"]
```

## 7. 操作和存储映射

| 操作 | 前端入口 | Java 接口 | 主要存储 | 说明 |
| --- | --- | --- | --- | --- |
| 创建知识库 | 知识库空间表单 | `POST /api/kb` | `kb_space` | 创建客户级知识库入口 |
| 查询知识库 | 当前知识库下拉框 | `GET /api/kb` | `kb_space` | 按客户隔离 |
| 更新知识库 | 编辑空间信息 | `PUT /api/kb/{id}` | `kb_space` | 不触发索引重建 |
| 删除知识库 | 删除按钮 | `DELETE /api/kb/{id}` | MySQL、Qdrant、Neo4j | 同步清理外部索引；失败则 MySQL 删除回滚 |
| 上传文档 | 文档上传表单 | `POST /api/kb/{kbId}/documents/upload` | MySQL、文件存储 | 保存文件并异步入库 |
| 重试文档 | 失败文档操作 | `POST /api/kb/documents/{id}/retry` | MySQL、Qdrant、Neo4j | 重新执行入库 |
| 样例导入 | 命令行 | `make ingest-a` | MySQL、Qdrant、Neo4j | 导入 `datasets/kb_docs` |
| 删除文档 | 删除按钮 | `DELETE /api/kb/documents/{id}` | MySQL、Qdrant、Neo4j | 同步清理外部索引；失败则 MySQL 删除回滚 |
| RAG 问答 | RAG 调试台 | `/api/chat/messages` -> `/api/agent/ask` | `chat_message`、`agent_trace` | 返回答案、引用、traceId |

## 8. 一致性说明

MySQL、Qdrant 和 Neo4j 不是同一个事务资源，当前项目也没有 XA/2PC 协议支持，所以不能把三者变成真正的单一 ACID 事务。删除链路采用同步强校验方案：外部索引先按幂等条件删除，任何一个外部存储失败都会抛出业务异常，Spring 事务回滚 MySQL 元数据删除。这样可以避免最常见、最危险的状态：管理台已经看不到文档，但 RAG 仍能从向量库或图谱召回旧内容。

生产级进一步增强建议是增加 outbox/tombstone 表：先在 MySQL 事务内把知识库或文档标记为 DELETING 并写清理任务，读路径过滤 DELETING；后台 worker 幂等清理 Qdrant/Neo4j，成功后再物理删除 MySQL 元数据。
