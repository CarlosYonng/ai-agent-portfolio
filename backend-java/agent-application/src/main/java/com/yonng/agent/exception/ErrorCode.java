package com.yonng.agent.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码注册表。
 *
 * <p>前端、日志和接口响应都依赖这里的稳定 code 识别业务失败原因；新增业务异常时优先在这里登记。</p>
 */
public enum ErrorCode {

    AUTH_INVALID_CREDENTIALS(HttpStatus.BAD_REQUEST, "用户名或密码错误"),
    AUTH_ACCOUNT_DISABLED(HttpStatus.CONFLICT, "账户已被禁用"),
    AUTH_INVITE_CODE_INVALID(HttpStatus.BAD_REQUEST, "邀请码无效或客户已停用"),
    AUTH_PLATFORM_INVITE_REJECTED(HttpStatus.FORBIDDEN, "平台账号不支持公开注册，请联系超级管理员"),
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "登录状态已失效，请重新登录"),
    PERMISSION_DENIED(HttpStatus.FORBIDDEN, "当前账号没有权限执行这个操作"),

    USER_USERNAME_EXISTS(HttpStatus.CONFLICT, "用户名已存在"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "用户不存在"),
    USER_PASSWORD_REQUIRED(HttpStatus.BAD_REQUEST, "密码不能为空"),
    USER_ROLE_INVALID(HttpStatus.BAD_REQUEST, "角色不合法"),
    USER_CANNOT_CREATE_KB(HttpStatus.FORBIDDEN, "外部用户无权创建知识库，请联系客户管理员"),

    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "客户不存在"),
    CUSTOMER_NAME_EXISTS(HttpStatus.CONFLICT, "客户名称已存在"),
    CUSTOMER_REQUIRED(HttpStatus.BAD_REQUEST, "客户用户必须选择所属客户"),
    CUSTOMER_INACTIVE(HttpStatus.BAD_REQUEST, "所属客户不存在或已停用"),
    CUSTOMER_INVITE_CODE_GENERATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "客户邀请码生成失败"),
    PLATFORM_CUSTOMER_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "平台客户（invite_code=PLATFORM）未初始化，请检查 seed 数据"),

    KB_NOT_FOUND(HttpStatus.NOT_FOUND, "知识库不存在"),
    KB_NAME_EXISTS(HttpStatus.CONFLICT, "知识库名称已存在，同一客户内名称唯一"),
    KB_VISIBILITY_INVALID(HttpStatus.BAD_REQUEST, "visibility 仅支持 PRIVATE/TEAM/PUBLIC"),
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "文档不存在"),
    DOCUMENT_STATUS_INVALID(HttpStatus.BAD_REQUEST, "status 仅支持 PENDING/INDEXED/FAILED"),
    DOCUMENT_UPLOAD_INVALID(HttpStatus.BAD_REQUEST, "仅支持上传 Markdown 或 TXT 文档"),
    DOCUMENT_INGEST_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "文档已保存，但自动入库失败，请查看文档状态后重试"),
    DOCUMENT_SOURCE_FILE_MISSING(HttpStatus.NOT_FOUND, "文档源文件不存在，请重新上传后再下载"),
    KB_INDEX_CLEANUP_FAILED(HttpStatus.BAD_GATEWAY, "知识库外部索引清理失败，数据库删除已回滚，请稍后重试"),

    CHAT_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "会话不存在或无权访问"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "操作过于频繁，请稍后重试"),
    TRACE_NOT_FOUND(HttpStatus.NOT_FOUND, "Trace 不存在或无权访问"),

    REQUEST_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "请求参数不合法"),
    CONTENT_SENSITIVE_REJECTED(HttpStatus.BAD_REQUEST, "您的问题包含敏感内容，请重新输入"),

    DOWNSTREAM_AI_ERROR(HttpStatus.BAD_GATEWAY, "AI 服务调用失败，请稍后重试"),
    SYSTEM_REQUEST_SERIALIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "服务内部请求组装失败，请联系维护人员处理"),
    SYSTEM_SCHEMA_MISMATCH(HttpStatus.INTERNAL_SERVER_ERROR, "系统配置未完成，请联系维护人员处理"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "系统内部异常，请联系维护人员处理");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
