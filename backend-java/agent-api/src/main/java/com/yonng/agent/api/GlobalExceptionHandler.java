package com.yonng.agent.api;

import com.yonng.agent.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;
import java.util.UUID;

/**
 * API 统一异常处理。
 *
 * <p>前端依赖 code 做用户文案映射，traceId 用于排障；message 只保留服务端诊断语义。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException error,
                                                                 HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(error.getStatusCode().value());
        HttpStatus safeStatus = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        String reason = error.getReason() == null ? safeStatus.getReasonPhrase() : error.getReason();
        String traceId = traceId();
        log.warn("api_error traceId={} status={} path={} message={}", traceId, safeStatus.value(), request.getRequestURI(), reason);
        return ResponseEntity.status(safeStatus)
                .body(body(safeStatus.name(), reason, request, traceId));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException error,
                                                            HttpServletRequest request) {
        String message = error.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        String traceId = traceId();
        log.warn("api_validation_error traceId={} path={} message={}", traceId, request.getRequestURI(), message);
        return ResponseEntity.badRequest()
                .body(body("VALIDATION_ERROR", message.isBlank() ? "请求参数不合法" : message, request, traceId));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException error,
                                                                     HttpServletRequest request) {
        String message = error.getConstraintViolations()
                .stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .collect(Collectors.joining("; "));
        String traceId = traceId();
        log.warn("api_constraint_error traceId={} path={} message={}", traceId, request.getRequestURI(), message);
        return ResponseEntity.badRequest()
                .body(body("VALIDATION_ERROR", message.isBlank() ? "请求参数不合法" : message, request, traceId));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(MissingServletRequestParameterException error,
                                                              HttpServletRequest request) {
        String traceId = traceId();
        String message = "缺少必填参数：" + error.getParameterName();
        log.warn("api_missing_param traceId={} path={} message={}", traceId, request.getRequestURI(), message);
        return ResponseEntity.badRequest()
                .body(body("MISSING_PARAMETER", message, request, traceId));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException error,
                                                              HttpServletRequest request) {
        String traceId = traceId();
        String message = "参数类型不正确：" + error.getName();
        log.warn("api_type_mismatch traceId={} path={} message={}", traceId, request.getRequestURI(), message);
        return ResponseEntity.badRequest()
                .body(body("TYPE_MISMATCH", message, request, traceId));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException error,
                                                                HttpServletRequest request) {
        String traceId = traceId();
        String message = "请求体不是合法 JSON，或字段格式不正确";
        log.warn("api_bad_body traceId={} path={} message={}", traceId, request.getRequestURI(), error.getMessage());
        return ResponseEntity.badRequest()
                .body(body("BAD_REQUEST_BODY", message, request, traceId));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiErrorResponse> handleDownstreamError(RestClientException error,
                                                                  HttpServletRequest request) {
        String traceId = traceId();
        log.error("api_downstream_error traceId={} path={} message={}", traceId, request.getRequestURI(), error.getMessage(), error);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(body("DOWNSTREAM_ERROR", "AI 服务调用失败，请根据错误编号查看后端日志", request, traceId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception error,
                                                           HttpServletRequest request) {
        String traceId = traceId();
        log.error("api_internal_error traceId={} path={} message={}", traceId, request.getRequestURI(), error.getMessage(), error);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body("INTERNAL_ERROR", "服务器处理失败，请根据错误编号查看后端日志", request, traceId));
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + " " + (error.getDefaultMessage() == null ? "不合法" : error.getDefaultMessage());
    }

    private ApiErrorResponse body(String code, String message, HttpServletRequest request, String traceId) {
        return new ApiErrorResponse(code, message, request.getRequestURI(), traceId, OffsetDateTime.now());
    }

    private String traceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

}
