package com.yonng.agent.exception;

import org.springframework.http.HttpStatus;

/**
 * 可预期业务失败。
 *
 * <p>Service 层用它表达用户可修正的业务问题，GlobalExceptionHandler 负责转换成稳定错误码响应。</p>
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message == null || message.isBlank() ? errorCode.message() : message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public HttpStatus status() {
        return errorCode.status();
    }
}
