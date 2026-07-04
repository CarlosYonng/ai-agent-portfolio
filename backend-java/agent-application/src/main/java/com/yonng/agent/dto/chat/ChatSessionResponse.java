package com.yonng.agent.dto.chat;

import com.yonng.agent.domain.chat.ChatSession;

import java.time.OffsetDateTime;

/**
 * 聊天会话列表响应。
 */
public record ChatSessionResponse(
        Long id,
        Long customerId,
        Long userId,
        Long kbId,
        String title,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static ChatSessionResponse from(ChatSession session) {
        return new ChatSessionResponse(
                session.getId(),
                session.getCustomerId(),
                session.getUserId(),
                session.getKbId(),
                session.getTitle(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }
}
