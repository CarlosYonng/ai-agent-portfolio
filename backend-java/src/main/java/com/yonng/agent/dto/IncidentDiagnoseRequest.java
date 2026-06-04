package com.yonng.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Java 微服务故障诊断请求。
 *
 * <p>由 Java 后端接收，再转发给 Python AI 服务的故障诊断 Agent。</p>
 */
public class IncidentDiagnoseRequest {

    /** demo 默认租户；接入登录后应从认证上下文获取。 */
    @NotNull
    private Long tenantId = 1L;

    /** demo 默认用户；用于后续审计和权限控制。 */
    @NotNull
    private Long userId = 1L;

    /** 故障所属服务名，用来限定日志、代码和工单检索范围。 */
    @NotBlank
    private String service = "order";

    /** 用户描述的故障现象或排障问题。 */
    @NotBlank
    private String question;

    /** 可选 traceId；传入后日志检索会更精准。 */
    private String traceId;

    /** 查询时间范围，第一版由 MCP 工具解释。 */
    private String timeRange = "last_1h";

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(String timeRange) {
        this.timeRange = timeRange;
    }
}
