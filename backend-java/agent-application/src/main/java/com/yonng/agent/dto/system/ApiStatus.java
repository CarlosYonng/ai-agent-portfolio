package com.yonng.agent.dto.system;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * 统一 API 空响应（无 data 字段）。
 *
 * <p>适用于创建/更新/删除等不需要返回业务数据的端点。和 {@link ApiResult} 共用
 * {@code code} / {@code message} 字段结构，前端从同一切口解析，通过有无 data 字段区分。</p>
 *
 * @param code    成功为 "ok"，异常时为业务错误码
 * @param message 成功为 "ok"，异常时为人类可读的错误描述
 * @param traceId 仅异常时携带，用于排障追踪
 */
@JsonInclude(Include.NON_NULL)
public record ApiStatus(
        String code,
        String message,
        String traceId
) {

    /** 成功无数据。 */
    public static ApiStatus ok() {
        return new ApiStatus("ok", "ok", null);
    }

    /** 异常响应。 */
    public static ApiStatus error(String code, String message, String traceId) {
        return new ApiStatus(code, message, traceId);
    }
}
