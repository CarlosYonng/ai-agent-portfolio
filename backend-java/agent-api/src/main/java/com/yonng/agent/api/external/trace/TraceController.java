package com.yonng.agent.api.external.trace;

import com.yonng.agent.api.config.CurrentUser;
import com.yonng.agent.api.config.RoleAccess;
import com.yonng.agent.dto.system.ApiResult;
import com.yonng.agent.dto.trace.AgentTraceNode;
import com.yonng.agent.dto.trace.TraceSummaryResponse;
import com.yonng.agent.dto.user.UserInfo;
import com.yonng.agent.service.trace.TraceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 外部 Agent Trace 查询接口。
 *
 * <p>调用方：浏览器 Trace 面板。用于查看 Agent 如何检索、生成和验证，支撑线上排障与质量评估。</p>
 */
@RestController
@RequestMapping("/api/traces")
public class TraceController {

    private final TraceService traceService;

    public TraceController(TraceService traceService) {
        this.traceService = traceService;
    }

    /**
     * 查询一次 Agent 调用的节点执行轨迹。
     */
    @GetMapping("/{traceId}")
    public ApiResult<List<AgentTraceNode>> getTrace(@CurrentUser UserInfo user,
                                                    @PathVariable String traceId) {
        return ApiResult.ok(traceService.listByTraceId(traceId, RoleAccess.customerScope(user), RoleAccess.userScope(user)));
    }

    /**
     * 查询最近的 Agent Trace 历史。
     */
    @GetMapping
    public ApiResult<List<TraceSummaryResponse>> listRecent(@CurrentUser UserInfo user) {
        return ApiResult.ok(traceService.listRecent(RoleAccess.customerScope(user), RoleAccess.userScope(user)));
    }

    /**
     * 查询最近一段时间的节点聚合统计。
     */
    @GetMapping("/stats")
    public ApiResult<Map<String, Object>> stats(@CurrentUser UserInfo user,
                                                @RequestParam(defaultValue = "24") int hours) {
        return ApiResult.ok(traceService.stats(hours, RoleAccess.customerScope(user)));
    }
}
