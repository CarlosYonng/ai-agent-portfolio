package com.yonng.agent.dto;

import com.yonng.agent.domain.ChatSession;

import java.time.OffsetDateTime;

/**
 * 聊天会话列表响应。
 */
public record ChatSessionResponse(
        Long id,
        Long tenantId,
        Long userId,
        Long kbId,
        String title,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static ChatSessionResponse from(ChatSession session) {
        return new ChatSessionResponse(
                session.getId(),
                session.getTenantId(),
                session.getUserId(),
                session.getKbId(),
                session.getTitle(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }
}
