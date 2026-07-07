# API 边界说明

本文档用于区分 Java 后端接口的调用方，避免把前端用户操作、内部服务回写和运维探活混在同一概念里。

## 1. Controller 包结构

| 包路径 | 调用方 | URL 约定 | 职责 |
| --- | --- | --- | --- |
| `com.yonng.agent.api.external.*` | 浏览器 / React 管理台 | `/api/auth/**`、`/api/kb/**`、`/api/chat/**`、`/api/admin/**`、`/api/traces/**` | 用户可见业务操作，必须走登录身份、角色和客户隔离 |
| `com.yonng.agent.api.internal.*` | 受控脚本 / 内部服务 / 后台补偿任务 | `/api/internal/**` | 机器到机器调用，例如文档入库状态回写；当前先由平台管理员 JWT 保护，后续可演进为 service token |
| `com.yonng.agent.api.ops.*` | smoke test / 探活 / 接口文档工具 | `/api/health`、`/api/docs/**` | 运维和工具接口，不表达业务流程 |
| Spring Actuator | Prometheus / 运维平台 | `/actuator/prometheus` | Java 侧 metrics 暴露入口，不承载业务写入 |
| `com.yonng.agent.api.config.*` | Spring Security / MVC 配置 | 无业务 URL | 鉴权、当前用户解析、Redis、请求日志等横切配置 |

## 2. 当前接口归类

| 接口 | Controller | 调用方 | 说明 |
| --- | --- | --- | --- |
| `POST /api/auth/register`、`POST /api/auth/login`、`POST /api/auth/logout` | `external.auth.AuthController` | 登录页 / 注册页 | 用户认证，不给内部服务复用 |
| `/api/admin/users/**` | `external.admin.AdminUserController` | 平台管理台 | 管理用户账号 |
| `/api/admin/customers/**` | `external.admin.CustomerController` | 平台管理台 | 管理客户空间 |
| `/api/kb/**` | `external.knowledge.KnowledgeBaseController` | 知识库管理台 | 用户可见知识库、文档、上传、下载、删除 |
| `PATCH /api/internal/kb/documents/{id}/status` | `internal.knowledge.KnowledgeDocumentInternalController` | 导入脚本 / 内部补偿任务 | 文档状态回写；新内部调用应使用该路径 |
| `POST /api/chat/messages`、`GET /api/chat/sessions`、`GET /api/chat/sessions/{sessionId}/messages` | `external.chat.ChatController` | RAG 控制台 | Java 负责鉴权和会话历史，Agent 推理由服务层转发给 Python AI 服务；当前对外聊天接口为 REST JSON |
| `/api/traces/**` | `external.trace.TraceController` | Trace 面板 | 查看 Agent 执行轨迹和统计 |
| `/api/health`、`/api/docs/**` | `ops.*` | 运维 / 工具 | 健康检查与 OpenAPI 导出 |
| `/actuator/prometheus` | Spring Actuator | Prometheus | Java 指标采集；Python 指标由 AI 服务 `/metrics` 暴露 |

## 3. 新增接口规则

1. 前端页面会直接点击或轮询的接口放 `api.external`，并在类注释里写清楚调用页面。
2. 脚本、worker、补偿任务、跨服务回调放 `api.internal`，路径必须以 `/api/internal/` 开头。
3. 探活、文档、调试工具放 `api.ops`，不要混入业务 Controller。
4. 不要把同一个 Controller 同时承担“用户操作”和“内部回写”。旧入口确认没有调用方后直接删除，不保留长期兼容分支。
