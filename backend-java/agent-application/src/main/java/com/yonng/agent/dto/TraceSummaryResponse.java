package com.yonng.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/**
 * Agent Trace 历史摘要。
 */
public record TraceSummaryResponse(
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("node_count") int nodeCount,
        @JsonProperty("latest_node") String latestNode,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {
}
