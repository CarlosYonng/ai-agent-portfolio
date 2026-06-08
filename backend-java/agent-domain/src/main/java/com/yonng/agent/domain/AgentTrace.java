package com.yonng.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * Agent 执行轨迹。
 *
 * <p>Python AI 服务写入节点执行摘要，Java 后端通过 MyBatis Plus 统一查询并暴露给演示台。</p>
 */
@TableName("agent_trace")
public class AgentTrace {

    /** Trace 记录主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 一次 Agent 执行的链路 ID，前端用它查询节点时间线。 */
    private String traceId;

    /** 租户 ID，用于多租户隔离和后续审计。 */
    private Long tenantId;

    /** 关联的聊天消息 ID，便于从回答反查 Agent 执行过程。 */
    private Long messageId;

    /** Agent 节点名称，例如 Router、Retriever、Verifier。 */
    private String nodeName;

    /** 节点输入摘要，避免把完整用户问题或上下文写入 Trace 表。 */
    private String inputSummary;

    /** 节点输出摘要，用于演示和排障。 */
    private String outputSummary;

    /** 节点耗时，单位毫秒。 */
    private Integer durationMs;

    /** 节点扩展元数据，JSON 字符串格式。 */
    private String metadata;

    /** Trace 记录创建时间。 */
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public void setInputSummary(String inputSummary) {
        this.inputSummary = inputSummary;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public void setOutputSummary(String outputSummary) {
        this.outputSummary = outputSummary;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
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
