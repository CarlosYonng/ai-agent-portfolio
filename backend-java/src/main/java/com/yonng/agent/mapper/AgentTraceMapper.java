package com.yonng.agent.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * Agent Trace 查询 Mapper。
 *
 * <p>Trace 数据由 Python AI 服务写入 MySQL，Java 后端负责把它暴露给前端/面试演示。</p>
 */
public interface AgentTraceMapper {

    /**
     * 按 traceId 返回节点执行顺序，帮助定位 Router/Retriever/Verifier 等节点的输入输出。
     */
    @Select("""
            select node_name, input_summary, output_summary, duration_ms, metadata, created_at
            from agent_trace
            where trace_id = #{traceId}
            order by id
            """)
    List<Map<String, Object>> listByTraceId(@Param("traceId") String traceId);
}
