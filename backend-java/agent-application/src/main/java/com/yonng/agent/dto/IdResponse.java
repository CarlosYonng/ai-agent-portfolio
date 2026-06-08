package com.yonng.agent.dto;

/**
 * 创建资源后的统一 ID 响应。
 *
 * <p>JDK 21 record 适合不可变响应 DTO，避免 Controller 返回裸 Map。</p>
 *
 * @param id 新创建资源的数据库主键
 */
public record IdResponse(Long id) {
}
