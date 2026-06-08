package com.yonng.agent.api;

import com.yonng.agent.dto.HealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * 自定义健康检查接口。
 *
 * <p>Actuator 也有 /actuator/health，这个接口用于演示和 smoke test。</p>
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final String version;

    public HealthController(@Value("${app.version:dev}") String version) {
        this.version = version;
    }

    /**
     * 返回 Java 后端当前运行状态。
     */
    @GetMapping
    public HealthResponse health() {
        return new HealthResponse(
                "UP",
                "agent-backend-java",
                version,
                OffsetDateTime.now().toString()
        );
    }
}
