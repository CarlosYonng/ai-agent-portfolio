package com.yonng.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yonng.agent.domain.AgentTrace;

import java.time.OffsetDateTime;

/**
 * Trace 时间线节点响应。
 *
 * <p>保留前端已使用的 snake_case 字段，同时让 MySQL 查询仍走 AgentTrace 实体和 MyBatis Plus。</p>
 */
public record AgentTraceNode(
        @JsonProperty("node_name") String nodeName,
        @JsonProperty("input_summary") String inputSummary,
        @JsonProperty("output_summary") String outputSummary,
        @JsonProperty("duration_ms") Integer durationMs,
        String metadata,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {

    /**
     * 从 Trace 持久化实体转换为时间线响应节点。
     */
    public static AgentTraceNode from(AgentTrace trace) {
        return new AgentTraceNode(
                trace.getNodeName(),
                trace.getInputSummary(),
                trace.getOutputSummary(),
                trace.getDurationMs(),
                trace.getMetadata(),
                trace.getCreatedAt()
        );
    }
}
