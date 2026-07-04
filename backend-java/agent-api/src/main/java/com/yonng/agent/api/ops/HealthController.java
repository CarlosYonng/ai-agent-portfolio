package com.yonng.agent.api.ops;

import com.yonng.agent.dto.system.ApiResult;
import com.yonng.agent.dto.system.HealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * 运维健康检查接口。
 *
 * <p>调用方：smoke test、负载均衡探活和本地开发脚本。业务前端不依赖该接口。</p>
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
    public ApiResult<HealthResponse> health() {
        return ApiResult.ok(new HealthResponse(
                "UP",
                "agent-backend-java",
                version,
                OffsetDateTime.now().toString()
        ));
    }
}
