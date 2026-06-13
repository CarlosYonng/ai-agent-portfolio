package com.yonng.agent.dto;

import java.time.OffsetDateTime;

/**
 * 统一 API 错误响应。
 */
public record ApiErrorResponse(
        String code,
        String message,
        String path,
        String traceId,
        OffsetDateTime timestamp
) {
}
