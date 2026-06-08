package com.yonng.agent.dto;

/**
 * 故障根因候选。
 *
 * <p>由 AI 服务基于日志、代码和历史工单证据生成，Java 侧保留结构化字段方便前端展示。</p>
 *
 * @param cause 根因描述
 * @param confidence 置信度，范围通常为 0 到 1
 * @param evidence 支撑该根因的证据摘要
 */
public record IncidentRootCause(String cause, Double confidence, String evidence) {
}
