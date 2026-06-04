package com.yonng.agent.service;

import com.yonng.agent.mapper.AgentTraceMapper;
import org.springframework.stereotype.Service;

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
    public List<Map<String, Object>> listByTraceId(String traceId) {
        return agentTraceMapper.listByTraceId(traceId);
    }
}
