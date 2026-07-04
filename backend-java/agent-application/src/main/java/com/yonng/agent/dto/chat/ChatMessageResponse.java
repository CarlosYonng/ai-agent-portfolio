package com.yonng.agent.dto.chat;

import com.yonng.agent.domain.chat.ChatMessage;

import java.time.OffsetDateTime;

/**
 * 聊天消息明细响应。
 */
public record ChatMessageResponse(
        Long id,
        Long customerId,
        Long sessionId,
        String role,
        String content,
        String traceId,
        String citations,
        OffsetDateTime createdAt
) {

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getCustomerId(),
                message.getSessionId(),
                message.getRole(),
                message.getContent(),
                message.getTraceId(),
                message.getCitations(),
                message.getCreatedAt()
        );
    }
}
