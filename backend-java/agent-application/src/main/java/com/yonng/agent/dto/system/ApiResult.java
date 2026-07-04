package com.yonng.agent.dto.system;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * 携带业务数据的 API 响应。
 *
 * <p>适用于需要返回 {@code data} 的端点（GET 详情/列表、POST 上传等）。
 * 无数据响应请使用 {@link ApiStatus}，两类响应共用 {@code code} / {@code message} 结构，
 * 前端从同一切口解析，通过有无 {@code data} 字段区分。</p>
 *
 * @param code    成功为 "ok"
 * @param message 成功为 "ok"
 * @param data    业务数据
 * @param traceId 保留字段，当前始终为 {@code null}
 * @param <T>     业务数据类型
 */
@JsonInclude(Include.NON_NULL)
public record ApiResult<T>(
        String code,
        String message,
        T data,
        String traceId
) {

    /** 成功有数据。 */
    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>("ok", "ok", data, null);
    }
}
