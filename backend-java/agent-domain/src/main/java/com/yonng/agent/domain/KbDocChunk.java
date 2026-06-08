package com.yonng.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 文档切片。
 *
 * <p>文档正文被切成若干 chunk，向量写入 Qdrant，实体关系写入 Neo4j。
 * MySQL 侧保留一份副本，用于证据回显和管理台删除时的外键感知。</p>
 */
@TableName("kb_doc_chunk")
public class KbDocChunk {

    /** 文档切片主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 切片所属租户。 */
    private Long tenantId;

    /** 切片所属文档。 */
    private Long docId;

    /** 文档内切片序号，用于还原原文顺序。 */
    private Integer chunkNo;

    /** 标题层级路径，辅助关键词检索和证据展示。 */
    private String titlePath;

    /** 切片正文，MySQL 保留副本用于兜底检索和证据回显。 */
    private String content;

    /** 切片估算 token 数，用于控制上下文窗口。 */
    private Integer tokenCount;

    /** Qdrant 向量点 ID，用于同步外部向量索引。 */
    private String vectorId;

    /** 切片扩展元数据，JSON 字符串格式。 */
    private String metadata;

    /** 切片创建时间。 */
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

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public Integer getChunkNo() {
        return chunkNo;
    }

    public void setChunkNo(Integer chunkNo) {
        this.chunkNo = chunkNo;
    }

    public String getTitlePath() {
        return titlePath;
    }

    public void setTitlePath(String titlePath) {
        this.titlePath = titlePath;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
    }

    public String getVectorId() {
        return vectorId;
    }

    public void setVectorId(String vectorId) {
        this.vectorId = vectorId;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
