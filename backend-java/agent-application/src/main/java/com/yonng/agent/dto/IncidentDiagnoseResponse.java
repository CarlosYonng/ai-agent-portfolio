package com.yonng.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 故障诊断响应。
 *
 * <p>根因和动作已结构化；evidences 来自日志、代码、工单等不同工具，由 IncidentEvidence 承接扩展字段。</p>
 *
 * @param traceId 本次故障诊断链路 ID
 * @param summary 故障诊断摘要
 * @param rootCauses 按置信度排序的根因候选
 * @param actions 建议执行的处理动作
 * @param evidences 原始证据列表；不同工具返回字段不同，因此保留扩展结构
 */
public record IncidentDiagnoseResponse(
        @JsonProperty("trace_id") String traceId,
        String summary,
        @JsonProperty("root_causes") List<IncidentRootCause> rootCauses,
        List<String> actions,
        List<IncidentEvidence> evidences
) {
}
