package com.yonng.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yonng.agent.domain.IncidentDiagnosisHistory;

import java.time.OffsetDateTime;

/**
 * 故障诊断历史响应。
 */
public record IncidentHistoryResponse(
        Long id,
        Long tenantId,
        Long userId,
        String serviceName,
        String businessTraceId,
        String agentTraceId,
        String question,
        String summary,
        @JsonProperty("response_json") String responseJson,
        OffsetDateTime createdAt
) {

    public static IncidentHistoryResponse from(IncidentDiagnosisHistory history) {
        return new IncidentHistoryResponse(
                history.getId(),
                history.getTenantId(),
                history.getUserId(),
                history.getServiceName(),
                history.getBusinessTraceId(),
                history.getAgentTraceId(),
                history.getQuestion(),
                history.getSummary(),
                history.getResponseJson(),
                history.getCreatedAt()
        );
    }
}
