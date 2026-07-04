package com.yonng.agent.service.trace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yonng.agent.domain.chat.ChatMessage;
import com.yonng.agent.domain.chat.ChatSession;
import com.yonng.agent.domain.trace.AgentTrace;
import com.yonng.agent.dto.trace.AgentTraceNode;
import com.yonng.agent.dto.trace.TraceSummaryResponse;
import com.yonng.agent.exception.BusinessException;
import com.yonng.agent.exception.ErrorCode;
import com.yonng.agent.mapper.chat.ChatMessageMapper;
import com.yonng.agent.mapper.chat.ChatSessionMapper;
import com.yonng.agent.mapper.trace.AgentTraceMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.OffsetDateTime;

/**
 * Trace 查询服务。
 *
 * <p>Agent 可观测性是这个项目的简历亮点：面试时可以展示每个节点的输入输出摘要。</p>
 */
@Service
public class TraceService {

    private final AgentTraceMapper agentTraceMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionMapper chatSessionMapper;

    public TraceService(AgentTraceMapper agentTraceMapper,
                        ChatMessageMapper chatMessageMapper,
                        ChatSessionMapper chatSessionMapper) {
        this.agentTraceMapper = agentTraceMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.chatSessionMapper = chatSessionMapper;
    }

    /**
     * 返回一次 Agent 调用的节点轨迹。
     */
    public List<AgentTraceNode> listByTraceId(String traceId, Long customerId, Long userId) {
        LambdaQueryWrapper<AgentTrace> wrapper = new LambdaQueryWrapper<AgentTrace>()
                        .select(AgentTrace::getNodeName,
                                AgentTrace::getCustomerId,
                                AgentTrace::getMessageId,
                                AgentTrace::getInputSummary,
                                AgentTrace::getOutputSummary,
                                AgentTrace::getDurationMs,
                                AgentTrace::getMetadata,
                                AgentTrace::getCreatedAt)
                        .eq(AgentTrace::getTraceId, traceId)
                        .orderByAsc(AgentTrace::getId);
        if (customerId != null) {
            wrapper.eq(AgentTrace::getCustomerId, customerId);
        }
        List<AgentTrace> rows = agentTraceMapper.selectList(wrapper);
        if (rows.isEmpty() || !traceVisibleToUser(rows.get(0), userId)) {
            throw new BusinessException(ErrorCode.TRACE_NOT_FOUND);
        }
        return rows
                .stream()
                .map(AgentTraceNode::from)
                .toList();
    }

    /**
     * 查询最近的 Agent Trace 摘要。
     */
    public List<TraceSummaryResponse> listRecent(Long customerId, Long userId) {
        LambdaQueryWrapper<AgentTrace> wrapper = new LambdaQueryWrapper<AgentTrace>()
                .select(AgentTrace::getTraceId,
                        AgentTrace::getMessageId,
                        AgentTrace::getNodeName,
                        AgentTrace::getCreatedAt)
                .orderByDesc(AgentTrace::getId)
                .last("limit 200");
        if (customerId != null) {
            wrapper.eq(AgentTrace::getCustomerId, customerId);
        }
        List<AgentTrace> rows = agentTraceMapper.selectList(wrapper);
        Map<String, TraceAccumulator> traces = new LinkedHashMap<>();
        for (AgentTrace row : rows) {
            if (!traceVisibleToUser(row, userId)) {
                continue;
            }
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

    /**
     * 聚合最近 N 小时的 Trace 节点调用量和平均耗时。
     */
    public Map<String, Object> stats(int hours, Long customerId) {
        int safeHours = Math.max(1, Math.min(hours, 720));
        QueryWrapper<AgentTrace> wrapper = new QueryWrapper<AgentTrace>()
                .select("node_name", "count(*) as total", "avg(duration_ms) as avg_duration_ms", "max(created_at) as latest_at")
                .ge("created_at", OffsetDateTime.now().minusHours(safeHours))
                .groupBy("node_name")
                .orderByDesc("total");
        if (customerId != null) {
            wrapper.eq("customer_id", customerId);
        }
        return Map.of(
                "hours", safeHours,
                "nodes", agentTraceMapper.selectMaps(wrapper)
        );
    }

    private boolean traceVisibleToUser(AgentTrace trace, Long userId) {
        if (userId == null) {
            return true;
        }
        if (trace.getMessageId() == null) {
            return false;
        }
        ChatMessage message = chatMessageMapper.selectById(trace.getMessageId());
        if (message == null) {
            return false;
        }
        ChatSession session = chatSessionMapper.selectById(message.getSessionId());
        return session != null && userId.equals(session.getUserId());
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
