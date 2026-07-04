package com.yonng.agent.dto.knowledge;

import com.yonng.agent.domain.knowledge.KnowledgeBase;

import java.time.OffsetDateTime;

/**
 * 知识库响应 DTO。
 *
 * <p>API 层不直接暴露持久化实体，后续表字段变化不会直接影响前端契约。JDK 21 record 让响应对象天然不可变。</p>
 *
 * @param id 知识库主键
 * @param customerId 知识库所属租户
 * @param name 知识库名称
 * @param description 知识库业务说明
 * @param visibility 可见性：PRIVATE/TEAM/PUBLIC
 * @param createdAt 创建时间
 */
public record KnowledgeBaseResponse(
        Long id,
        Long customerId,
        String name,
        String description,
        String visibility,
        OffsetDateTime createdAt
) {

    /**
     * 从持久化实体转换为 API 响应对象。
     */
    public static KnowledgeBaseResponse from(KnowledgeBase knowledgeBase) {
        return new KnowledgeBaseResponse(
                knowledgeBase.getId(),
                knowledgeBase.getCustomerId(),
                knowledgeBase.getName(),
                knowledgeBase.getDescription(),
                knowledgeBase.getVisibility(),
                knowledgeBase.getCreatedAt()
        );
    }
}
