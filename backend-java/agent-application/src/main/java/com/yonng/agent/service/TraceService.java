package com.yonng.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonng.agent.domain.AgentTrace;
import com.yonng.agent.dto.AgentTraceNode;
import com.yonng.agent.dto.TraceSummaryResponse;
import com.yonng.agent.mapper.AgentTraceMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trace 查询服务。
 *
 * <p>Agent 可观测性是这个项目的简历亮点：面试时可以展示每个节点的输入输出摘要。</p>
 */
@Service
public class TraceService {

    private final AgentTraceMapper agentTraceMapper;

    public TraceService(AgentTraceMapper agentTraceMapper) {
        this.agentTraceMapper = agentTraceMapper;
    }

    /**
     * 返回一次 Agent 调用的节点轨迹。
     */
    public List<AgentTraceNode> listByTraceId(String traceId) {
        return agentTraceMapper.selectList(new LambdaQueryWrapper<AgentTrace>()
                        .select(AgentTrace::getNodeName,
                                AgentTrace::getInputSummary,
                                AgentTrace::getOutputSummary,
                                AgentTrace::getDurationMs,
                                AgentTrace::getMetadata,
                                AgentTrace::getCreatedAt)
                        .eq(AgentTrace::getTraceId, traceId)
                        .orderByAsc(AgentTrace::getId))
                .stream()
                .map(AgentTraceNode::from)
                .toList();
    }

    /**
     * 查询最近的 Agent Trace 摘要。
     */
    public List<TraceSummaryResponse> listRecent(Long tenantId) {
        List<AgentTrace> rows = agentTraceMapper.selectList(new LambdaQueryWrapper<AgentTrace>()
                .select(AgentTrace::getTraceId,
                        AgentTrace::getNodeName,
                        AgentTrace::getCreatedAt)
                .eq(AgentTrace::getTenantId, tenantId)
                .orderByDesc(AgentTrace::getId)
                .last("limit 200"));
        Map<String, TraceAccumulator> traces = new LinkedHashMap<>();
        for (AgentTrace row : rows) {
            traces.computeIfAbsent(row.getTraceId(), traceId -> new TraceAccumulator(row.getNodeName(), row.getCreatedAt()))
                    .increment();
        }
        return traces.entrySet()
                .stream()
                .limit(30)
                .map(entry -> new TraceSummaryResponse(
                        entry.getKey(),
                        entry.getValue().nodeCount,
                        entry.getValue().latestNode,
                        entry.getValue().createdAt))
                .toList();
    }

    private static class TraceAccumulator {
        private final String latestNode;
        private final java.time.OffsetDateTime createdAt;
        private int nodeCount;

        private TraceAccumulator(String latestNode, java.time.OffsetDateTime createdAt) {
            this.latestNode = latestNode;
            this.createdAt = createdAt;
        }

        private void increment() {
            nodeCount++;
        }
    }
}
