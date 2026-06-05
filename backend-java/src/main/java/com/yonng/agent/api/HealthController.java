package com.yonng.agent.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 自定义健康检查接口。
 *
 * <p>Actuator 也有 /actuator/health，这个接口用于演示和 smoke test。</p>
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "agent-backend-java",
                "version", "1.0.0",
                "time", OffsetDateTime.now().toString()
        );
    }
}

