package com.yonng.agent.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 聊天请求 DTO。
 *
 * <p>customerId/userId 由 JWT 自动注入，不再由前端传递。</p>
 */
public class ChatRequest {

    /** 为空时自动创建新会话；不为空时追加到已有会话。 */
    private Long sessionId;

    /** 当前问答限定的知识库；为空时按租户全局知识检索。 */
    private Long kbId;

    /** 用户原始问题，会被 AI 服务改写为检索 query。 */
    @NotBlank
    private String question;

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getKbId() {
        return kbId;
    }

    public void setKbId(Long kbId) {
        this.kbId = kbId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
