package com.yonng.agent.api;

import com.yonng.agent.dto.AgentTraceNode;
import com.yonng.agent.service.TraceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Agent Trace 查询接口。
 *
 * <p>这个接口非常适合面试演示：用户不只看到答案，还能看到 Agent 怎么检索和验证。</p>
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
    public List<AgentTraceNode> getTrace(@PathVariable String traceId) {
        return traceService.listByTraceId(traceId);
    }
}
