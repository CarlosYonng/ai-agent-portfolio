package com.yonng.agent.dto;

import com.yonng.agent.domain.KnowledgeDocument;

import java.time.OffsetDateTime;

/**
 * 知识库文档响应 DTO。
 *
 * <p>管理台只需要文档元数据，正文和索引细节仍留在 chunk、Qdrant、Neo4j 链路中。</p>
 *
 * @param id 文档主键
 * @param tenantId 文档所属租户
 * @param kbId 文档所属知识库
 * @param title 文档标题
 * @param sourceType 来源类型，例如 MARKDOWN、TXT、PDF
 * @param sourceUri 原始文件路径或对象存储地址
 * @param version 文档版本号，用于后续索引重建
 * @param status 文档处理状态：PENDING/INDEXED/FAILED
 * @param createdAt 创建时间
 */
public record KnowledgeDocumentResponse(
        Long id,
        Long tenantId,
        Long kbId,
        String title,
        String sourceType,
        String sourceUri,
        Integer version,
        String status,
        OffsetDateTime createdAt
) {

    /**
     * 从持久化实体转换为 API 响应对象。
     */
    public static KnowledgeDocumentResponse from(KnowledgeDocument document) {
        return new KnowledgeDocumentResponse(
                document.getId(),
                document.getTenantId(),
                document.getKbId(),
                document.getTitle(),
                document.getSourceType(),
                document.getSourceUri(),
                document.getVersion(),
                document.getStatus(),
                document.getCreatedAt()
        );
    }
}
