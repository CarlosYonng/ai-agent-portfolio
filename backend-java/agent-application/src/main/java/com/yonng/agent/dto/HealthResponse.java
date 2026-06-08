package com.yonng.agent.dto;

/**
 * 后端健康检查响应。
 *
 * <p>用于前端 smoke test 和演示页展示服务状态。</p>
 *
 * @param status 服务状态，当前固定为 UP
 * @param service 服务名称，便于前端区分 Java 后端和 AI 服务
 * @param version 应用版本号
 * @param time 当前服务时间，便于排查环境时区和实例存活状态
 */
public record HealthResponse(String status, String service, String version, String time) {
}
