package com.yonng.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 聊天请求 DTO。
 *
 * <p>tenantId/userId 在第一版由前端传入，后续接入登录后应从 JWT 中解析，避免伪造。</p>
 */
public class ChatRequest {

    /** 租户 ID；第一版由调用方传入，生产环境应从认证上下文获取。 */
    @NotNull
    private Long tenantId;

    /** 用户 ID；用于会话归属和后续审计。 */
    @NotNull
    private Long userId;

    /** 为空时自动创建新会话；不为空时追加到已有会话。 */
    private Long sessionId;

    /** 用户原始问题，会被 AI 服务改写为检索 query。 */
    @NotBlank
    private String question;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
