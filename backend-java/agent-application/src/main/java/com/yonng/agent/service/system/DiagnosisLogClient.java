package com.yonng.agent.service.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 诊断服务异常推送客户端。
 *
 * <p>Agent Ops 只推送异常事实，不直接依赖诊断库表；推送失败会降级为本地 warn 日志。</p>
 */
@Component
public class DiagnosisLogClient {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisLogClient.class);

    private final RestClient diagnosisRestClient;

    public DiagnosisLogClient(@Qualifier("diagnosisRestClient") RestClient diagnosisRestClient) {
        this.diagnosisRestClient = diagnosisRestClient;
    }

    /**
     * 把服务端异常推给外部诊断服务。
     */
    public void pushError(Throwable error, String endpoint, String traceId, String level) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("service", "agent-ops");
        payload.put("level", level);
        payload.put("trace_id", traceId);
        payload.put("endpoint", endpoint);
        payload.put("exception_type", error.getClass().getSimpleName());
        payload.put("message", error.getMessage() == null ? error.getClass().getName() : error.getMessage());
        payload.put("stack_trace", stackTrace(error));
        payload.put("environment", System.getProperty("spring.profiles.active", "local"));
        payload.put("occurred_at", OffsetDateTime.now().toString());

        try {
            diagnosisRestClient.post()
                    .uri("/api/logs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception pushError) {
            log.warn("diagnosis_push_failed traceId={} endpoint={} message={}",
                    traceId, endpoint, pushError.getMessage());
        }
    }

    private String stackTrace(Throwable error) {
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
