package com.yonng.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonng.agent.domain.AgentTrace;
import com.yonng.agent.dto.AgentTraceNode;
import com.yonng.agent.mapper.AgentTraceMapper;
import org.springframework.stereotype.Service;

import java.util.List;

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
}
