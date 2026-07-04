package com.yonng.agent.domain.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 知识库文档元数据。
 *
 * <p>Pattern A：正文切片后完整文本存 Qdrant payload，实体关系存 Neo4j。</p>
 */
@TableName("kb_document")
public class KnowledgeDocument {

    /** 文档主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文档所属租户。 */
    private Long customerId;

    /** 文档所属知识库。 */
    private Long kbId;

    /** 文档标题，通常来自文件名或用户填写。 */
    private String title;

    /** 来源类型，例如 MARKDOWN、TXT、PDF。 */
    private String sourceType;

    /** 原始文件路径、URL 或对象存储地址。 */
    private String sourceUri;

    /** 文档版本，后续支持重建索引或文档更新时递增。 */
    private Integer version;

    /** PENDING/INDEXED 等状态，用于区分是否完成切片和索引。 */
    private String status;

    /** 当前入库阶段，供管理台展示切片、向量化、外部索引等进度。 */
    private String ingestStage;

    /** 入库完成百分比，避免用户只能看到长时间 PENDING。 */
    private Integer progressPercent;

    /** 文档切片总数，便于定位大文档入库耗时。 */
    private Integer chunkTotal;

    /** 已处理切片数，供页面展示细粒度处理进度。 */
    private Integer chunkDone;

    /** 最近一次入库失败原因，保留给页面排查和重试。 */
    private String errorMessage;

    /** 最近一次成功完成索引的时间。 */
    private OffsetDateTime indexedAt;

    /** 文档元数据创建时间。 */
    private OffsetDateTime createdAt;

    /** 文档元数据更新时间。 */
    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getKbId() {
        return kbId;
    }

    public void setKbId(Long kbId) {
        this.kbId = kbId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceUri() {
        return sourceUri;
    }

    public void setSourceUri(String sourceUri) {
        this.sourceUri = sourceUri;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIngestStage() {
        return ingestStage;
    }

    public void setIngestStage(String ingestStage) {
        this.ingestStage = ingestStage;
    }

    public Integer getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Integer progressPercent) {
        this.progressPercent = progressPercent;
    }

    public Integer getChunkTotal() {
        return chunkTotal;
    }

    public void setChunkTotal(Integer chunkTotal) {
        this.chunkTotal = chunkTotal;
    }

    public Integer getChunkDone() {
        return chunkDone;
    }

    public void setChunkDone(Integer chunkDone) {
        this.chunkDone = chunkDone;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public OffsetDateTime getIndexedAt() {
        return indexedAt;
    }

    public void setIndexedAt(OffsetDateTime indexedAt) {
        this.indexedAt = indexedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
