package com.yonng.agent.dto;

import com.yonng.agent.domain.ChatMessage;

import java.time.OffsetDateTime;

/**
 * 聊天消息明细响应。
 */
public record ChatMessageResponse(
        Long id,
        Long tenantId,
        Long sessionId,
        String role,
        String content,
        String traceId,
        OffsetDateTime createdAt
) {

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getTenantId(),
                message.getSessionId(),
                message.getRole(),
                message.getContent(),
                message.getTraceId(),
                message.getCreatedAt()
        );
    }
}
