package com.yonng.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yonng.agent.domain.IncidentDiagnosisHistory;
import com.yonng.agent.dto.IncidentDiagnoseRequest;
import com.yonng.agent.dto.IncidentDiagnoseResponse;
import com.yonng.agent.dto.IncidentHistoryResponse;
import com.yonng.agent.mapper.IncidentDiagnosisHistoryMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
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
    private final IncidentDiagnosisHistoryMapper historyMapper;
    private final HistorySchemaService historySchemaService;

    public IncidentService(RestClient aiRestClient,
                           ObjectMapper objectMapper,
                           IncidentDiagnosisHistoryMapper historyMapper,
                           HistorySchemaService historySchemaService) {
        this.aiRestClient = aiRestClient;
        this.objectMapper = objectMapper;
        this.historyMapper = historyMapper;
        this.historySchemaService = historySchemaService;
    }

    /**
     * 调用 Python 故障诊断 Agent。
     */
    public IncidentDiagnoseResponse diagnose(IncidentDiagnoseRequest request) {
        Map<String, Object> aiRequest = new HashMap<>();
        aiRequest.put("tenant_id", request.getTenantId());
        aiRequest.put("user_id", request.getUserId());
        aiRequest.put("service", request.getService());
        aiRequest.put("question", request.getQuestion());
        aiRequest.put("trace_id", request.getTraceId());
        aiRequest.put("time_range", request.getTimeRange());

        try {
            String jsonBody = objectMapper.writeValueAsString(aiRequest);
            IncidentDiagnoseResponse response = aiRestClient.post()
                    .uri("/api/incident/diagnose")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .body(IncidentDiagnoseResponse.class);
            saveHistory(request, response);
            return response;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化诊断请求失败", e);
        }
    }

    /**
     * 查询诊断历史。
     */
    public List<IncidentHistoryResponse> listHistory(Long tenantId, Long userId) {
        historySchemaService.ensureSchema();
        try {
            return historyMapper.selectList(new LambdaQueryWrapper<IncidentDiagnosisHistory>()
                            .eq(IncidentDiagnosisHistory::getTenantId, tenantId)
                            .eq(IncidentDiagnosisHistory::getUserId, userId)
                            .orderByDesc(IncidentDiagnosisHistory::getId)
                            .last("limit 50"))
                    .stream()
                    .map(IncidentHistoryResponse::from)
                    .toList();
        } catch (RuntimeException error) {
            return List.of();
        }
    }

    private void saveHistory(IncidentDiagnoseRequest request, IncidentDiagnoseResponse response) {
        if (response == null) {
            return;
        }
        try {
            historySchemaService.ensureSchema();
            IncidentDiagnosisHistory history = new IncidentDiagnosisHistory();
            history.setTenantId(request.getTenantId());
            history.setUserId(request.getUserId());
            history.setServiceName(request.getService());
            history.setBusinessTraceId(request.getTraceId());
            history.setAgentTraceId(response.traceId());
            history.setQuestion(request.getQuestion());
            history.setSummary(response.summary());
            history.setResponseJson(objectMapper.writeValueAsString(response));
            historyMapper.insert(history);
        } catch (Exception error) {
            // 历史保存失败不能影响本次诊断结果返回。
        }
    }
}
