package com.yonng.agent.api;

import com.yonng.agent.dto.IncidentDiagnoseRequest;
import com.yonng.agent.service.IncidentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Java 微服务故障诊断接口。
 *
 * <p>面试演示项目 B 时，可以从这个接口统一进入。</p>
 */
@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    /**
     * 统一接收故障描述，再由 Python Agent 聚合日志、代码和工单证据。
     */
    @PostMapping("/diagnose")
    public Map<String, Object> diagnose(@Valid @RequestBody IncidentDiagnoseRequest request) {
        return incidentService.diagnose(request);
    }
}
