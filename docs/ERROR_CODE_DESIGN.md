# 错误码与页面提示设计

## 目标

- 业务异常必须返回稳定 `code`，前端根据 `code` 展示可理解的提示。
- 响应体保留 `traceId`，用于后端日志定位；页面需要把错误编号展示给用户。
- 不再用“服务器处理失败，请根据错误编号查看后端日志”作为业务失败文案。

## 后端约定

统一错误响应：

```json
{
  "code": "USER_USERNAME_EXISTS",
  "message": "用户名已存在",
  "traceId": "a1b2c3d4e5f6",
  "timestamp": "2026-06-14T10:00:00+08:00"
}
```

- 可预期业务失败使用 `BusinessException(ErrorCode.xxx)`。
- 新增业务异常先登记到 `ErrorCode`，再在 Service 层抛出。
- 参数校验、未登录、无权限、下游 AI 异常和数据库结构不匹配都必须返回 JSON 错误体。
- 环境、数据库、下游服务等系统类错误不能把修复步骤直接暴露到页面；页面只展示用户安全文案和错误编号。
- 未知系统异常才返回 `INTERNAL_ERROR`，文案只说明系统异常，不替代日志排查。

## 前端约定

- 页面请求优先使用 `apiRequest`，不要直接 `fetch`。
- `apiRequest` 负责解析错误响应、映射用户文案、触发全局 `agent-api-error` toast。
- 登录/注册页不在控制台主框架内，因此本页直接展示错误文案和错误编号。
- 内部管理页既显示 toast，也可在表单区域保留简短操作结果。

## 已覆盖的业务错误码

| code | 场景 |
| --- | --- |
| `AUTH_INVALID_CREDENTIALS` | 用户名或密码错误 |
| `AUTH_ACCOUNT_DISABLED` | 账号被禁用 |
| `AUTH_INVITE_CODE_INVALID` | 邀请码无效或客户停用 |
| `AUTH_REQUIRED` | 未登录或登录失效 |
| `PERMISSION_DENIED` | 当前账号无权限 |
| `USER_USERNAME_EXISTS` | 用户名已存在 |
| `USER_NOT_FOUND` | 用户不存在 |
| `USER_PASSWORD_REQUIRED` | 创建用户缺少密码 |
| `USER_ROLE_INVALID` | 角色不合法 |
| `CUSTOMER_NOT_FOUND` | 客户不存在 |
| `CUSTOMER_NAME_EXISTS` | 客户名称已存在 |
| `CUSTOMER_REQUIRED` | 客户用户未选择所属客户 |
| `CUSTOMER_INACTIVE` | 所属客户不存在或停用 |
| `CUSTOMER_INVITE_CODE_GENERATE_FAILED` | 客户邀请码生成失败 |
| `KB_NOT_FOUND` | 知识库不存在 |
| `KB_VISIBILITY_INVALID` | 知识库可见性不合法 |
| `DOCUMENT_NOT_FOUND` | 文档不存在 |
| `DOCUMENT_STATUS_INVALID` | 文档状态不合法 |
| `CHAT_SESSION_NOT_FOUND` | 会话不存在或无权访问 |
| `TRACE_NOT_FOUND` | Trace 不存在或无权访问 |
| `DOWNSTREAM_AI_ERROR` | AI 服务调用失败 |
| `SYSTEM_REQUEST_SERIALIZATION_FAILED` | Java 组装下游请求失败 |
| `SYSTEM_SCHEMA_MISMATCH` | 数据库结构与代码版本不一致，对外展示为系统配置异常 |
| `INTERNAL_ERROR` | 未分类系统异常 |
