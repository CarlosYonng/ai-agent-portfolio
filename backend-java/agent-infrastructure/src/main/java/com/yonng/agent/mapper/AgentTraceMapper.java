package com.yonng.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yonng.agent.domain.AgentTrace;

/**
 * Agent Trace 查询 Mapper。
 *
 * <p>Trace 数据由 Python AI 服务写入 MySQL，Java 后端负责把它暴露给前端/面试演示。</p>
 */
public interface AgentTraceMapper extends BaseMapper<AgentTrace> {
}
