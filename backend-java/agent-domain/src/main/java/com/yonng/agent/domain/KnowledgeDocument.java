package com.yonng.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 知识库文档元数据。
 *
 * <p>真实正文被切成 kb_doc_chunk，向量写入 Qdrant，实体关系写入 Neo4j。</p>
 */
@TableName("kb_document")
public class KnowledgeDocument {

    /** 文档主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文档所属租户。 */
    private Long tenantId;

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

    /** 文档元数据创建时间。 */
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
