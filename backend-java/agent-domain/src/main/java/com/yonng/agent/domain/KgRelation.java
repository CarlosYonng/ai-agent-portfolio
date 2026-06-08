package com.yonng.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 知识图谱关系。
 *
 * <p>实体之间通过 relation_type 连接，evidence_chunk_id 指向文档切片作为证据来源。
 * Neo4j 存储完整图谱，MySQL 侧保留一份参照以便管理台级联删除。</p>
 */
@TableName("kg_relation")
public class KgRelation {

    /** 关系主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关系所属租户。 */
    private Long tenantId;

    /** 源实体 ID，对应 Neo4j 中的起点实体。 */
    private Long sourceEntityId;

    /** 目标实体 ID，对应 Neo4j 中的终点实体。 */
    private Long targetEntityId;

    /** 关系类型，例如 DEPENDS_ON、CAUSES、MENTIONS。 */
    private String relationType;

    /** 证据切片 ID，指向支撑该关系的文档片段。 */
    private Long evidenceChunkId;

    /** 关系创建时间。 */
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

    public Long getSourceEntityId() {
        return sourceEntityId;
    }

    public void setSourceEntityId(Long sourceEntityId) {
        this.sourceEntityId = sourceEntityId;
    }

    public Long getTargetEntityId() {
        return targetEntityId;
    }

    public void setTargetEntityId(Long targetEntityId) {
        this.targetEntityId = targetEntityId;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public Long getEvidenceChunkId() {
        return evidenceChunkId;
    }

    public void setEvidenceChunkId(Long evidenceChunkId) {
        this.evidenceChunkId = evidenceChunkId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
