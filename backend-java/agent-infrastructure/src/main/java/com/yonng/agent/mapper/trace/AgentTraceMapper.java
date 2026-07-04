package com.yonng.agent.mapper.trace;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yonng.agent.domain.trace.AgentTrace;

/**
 * Agent Trace 查询 Mapper。
 *
 * <p>Trace 数据由 Python AI 服务写入 MySQL，Java 后端负责暴露给前端做排障和质量评估。</p>
 */
public interface AgentTraceMapper extends BaseMapper<AgentTrace> {
}
