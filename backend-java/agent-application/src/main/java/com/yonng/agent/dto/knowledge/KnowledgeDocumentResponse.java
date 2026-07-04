package com.yonng.agent.dto.knowledge;

import com.yonng.agent.domain.knowledge.KnowledgeDocument;

import java.time.OffsetDateTime;

/**
 * 知识库文档响应 DTO。
 *
 * <p>管理台只需要文档元数据，正文和索引细节仍留在 chunk、Qdrant、Neo4j 链路中。</p>
 *
 * @param id 文档主键
 * @param customerId 文档所属租户
 * @param kbId 文档所属知识库
 * @param title 文档标题
 * @param sourceType 来源类型，例如 MARKDOWN、TXT、PDF
 * @param sourceUri 原始文件路径或对象存储地址
 * @param version 文档版本号，用于后续索引重建
 * @param status 文档处理状态：PENDING/INDEXED/FAILED
 * @param ingestStage 当前入库阶段：UPLOADED/CHUNKING/EMBEDDING/WRITING_VECTOR/INDEXED/FAILED
 * @param progressPercent 入库完成百分比
 * @param chunkTotal 文档分片总数
 * @param chunkDone 已处理分片数
 * @param errorMessage 最近一次失败原因
 * @param indexedAt 最近一次成功索引时间
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record KnowledgeDocumentResponse(
        Long id,
        Long customerId,
        Long kbId,
        String title,
        String sourceType,
        String sourceUri,
        Integer version,
        String status,
        String ingestStage,
        Integer progressPercent,
        Integer chunkTotal,
        Integer chunkDone,
        String errorMessage,
        OffsetDateTime indexedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    /**
     * 从持久化实体转换为 API 响应对象。
     */
    public static KnowledgeDocumentResponse from(KnowledgeDocument document) {
        return new KnowledgeDocumentResponse(
                document.getId(),
                document.getCustomerId(),
                document.getKbId(),
                document.getTitle(),
                document.getSourceType(),
                document.getSourceUri(),
                document.getVersion(),
                document.getStatus(),
                document.getIngestStage(),
                document.getProgressPercent(),
                document.getChunkTotal(),
                document.getChunkDone(),
                document.getErrorMessage(),
                document.getIndexedAt(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
