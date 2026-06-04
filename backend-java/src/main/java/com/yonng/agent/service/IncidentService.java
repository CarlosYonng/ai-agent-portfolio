package com.yonng.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yonng.agent.dto.IncidentDiagnoseRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * 故障诊断服务。
 *
 * <p>这里保持 Java 统一入口，真正的诊断编排交给 Python AI 服务。</p>
 */
@Service
public class IncidentService {

    private final RestClient aiRestClient;
    private final ObjectMapper objectMapper;

    public IncidentService(RestClient aiRestClient, ObjectMapper objectMapper) {
        this.aiRestClient = aiRestClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 调用 Python 故障诊断 Agent。
     */
    public Map<String, Object> diagnose(IncidentDiagnoseRequest request) {
        Map<String, Object> aiRequest = new HashMap<>();
        aiRequest.put("tenant_id", request.getTenantId());
        aiRequest.put("user_id", request.getUserId());
        aiRequest.put("service", request.getService());
        aiRequest.put("question", request.getQuestion());
        aiRequest.put("trace_id", request.getTraceId());
        aiRequest.put("time_range", request.getTimeRange());

        try {
            String jsonBody = objectMapper.writeValueAsString(aiRequest);
            return aiRestClient.post()
                    .uri("/api/incident/diagnose")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .body(Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化诊断请求失败", e);
        }
    }
}
