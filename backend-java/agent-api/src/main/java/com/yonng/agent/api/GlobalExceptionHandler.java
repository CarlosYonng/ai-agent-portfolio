package com.yonng.agent.api;

import com.yonng.agent.dto.system.ApiStatus;
import com.yonng.agent.exception.BusinessException;
import com.yonng.agent.exception.ErrorCode;
import com.yonng.agent.service.system.DiagnosisLogClient;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.ConstraintViolationException;
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

    private final DiagnosisLogClient diagnosisLogClient;

    public GlobalExceptionHandler(DiagnosisLogClient diagnosisLogClient) {
        this.diagnosisLogClient = diagnosisLogClient;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiStatus> handleBusiness(BusinessException error,
                                                          HttpServletRequest request) {
        String traceId = traceId();
        log.warn("api_business_error traceId={} code={} status={} path={} message={}",
                traceId, error.errorCode().name(), error.status().value(), request.getRequestURI(), error.getMessage());
        return ResponseEntity.status(error.status())
                .body(ApiStatus.error(error.errorCode().name(), error.getMessage(), traceId));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiStatus> handleResponseStatus(ResponseStatusException error,
                                                                HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(error.getStatusCode().value());
        HttpStatus safeStatus = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        String reason = error.getReason() == null ? safeStatus.getReasonPhrase() : error.getReason();
        String traceId = traceId();
        log.warn("api_error traceId={} status={} path={} message={}", traceId, safeStatus.value(), request.getRequestURI(), reason);
        return ResponseEntity.status(safeStatus)
                .body(ApiStatus.error(safeStatus.name(), reason, traceId));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiStatus> handleBadRequest(IllegalArgumentException error,
                                                           HttpServletRequest request) {
        String traceId = traceId();
        log.warn("api_bad_request traceId={} path={} message={}", traceId, request.getRequestURI(), error.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiStatus.error("BAD_REQUEST", error.getMessage(), traceId));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiStatus> handleIllegalState(IllegalStateException error,
                                                             HttpServletRequest request) {
        String traceId = traceId();
        log.warn("api_conflict traceId={} path={} message={}", traceId, request.getRequestURI(), error.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiStatus.error("CONFLICT", error.getMessage(), traceId));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiStatus> handleValidation(MethodArgumentNotValidException error,
                                                           HttpServletRequest request) {
        String message = error.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .collect(Collectors.joining("; "));
        String traceId = traceId();
        log.warn("api_validation_error traceId={} path={} message={}", traceId, request.getRequestURI(), message);
        return ResponseEntity.badRequest()
                .body(ApiStatus.error("VALIDATION_ERROR", message.isBlank() ? "请求参数不合法" : message, traceId));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiStatus> handleConstraintViolation(ConstraintViolationException error,
                                                                    HttpServletRequest request) {
        String message = error.getConstraintViolations()
                .stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .collect(Collectors.joining("; "));
        String traceId = traceId();
        log.warn("api_constraint_error traceId={} path={} message={}", traceId, request.getRequestURI(), message);
        return ResponseEntity.badRequest()
                .body(ApiStatus.error("VALIDATION_ERROR", message.isBlank() ? "请求参数不合法" : message, traceId));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiStatus> handleMissingParam(MissingServletRequestParameterException error,
                                                             HttpServletRequest request) {
        String traceId = traceId();
        String message = "缺少必填参数：" + error.getParameterName();
        log.warn("api_missing_param traceId={} path={} message={}", traceId, request.getRequestURI(), message);
        return ResponseEntity.badRequest()
                .body(ApiStatus.error("MISSING_PARAMETER", message, traceId));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiStatus> handleTypeMismatch(MethodArgumentTypeMismatchException error,
                                                             HttpServletRequest request) {
        String traceId = traceId();
        String message = "参数类型不正确：" + error.getName();
        log.warn("api_type_mismatch traceId={} path={} message={}", traceId, request.getRequestURI(), message);
        return ResponseEntity.badRequest()
                .body(ApiStatus.error("TYPE_MISMATCH", message, traceId));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiStatus> handleUnreadableBody(HttpMessageNotReadableException error,
                                                               HttpServletRequest request) {
        String traceId = traceId();
        String message = "请求体不是合法 JSON，或字段格式不正确";
        log.warn("api_bad_body traceId={} path={} message={}", traceId, request.getRequestURI(), error.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiStatus.error("BAD_REQUEST_BODY", message, traceId));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiStatus> handleDownstreamError(RestClientException error,
                                                                 HttpServletRequest request) {
        String traceId = traceId();
        log.error("api_downstream_error traceId={} path={} message={}", traceId, request.getRequestURI(), error.getMessage(), error);
        diagnosisLogClient.pushError(error, request.getRequestURI(), traceId, "ERROR");
        if (isTimeout(error)) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(ApiStatus.error("DOWNSTREAM_TIMEOUT", "下游服务响应超时，请稍后重试", traceId));
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiStatus.error(ErrorCode.DOWNSTREAM_AI_ERROR.name(), ErrorCode.DOWNSTREAM_AI_ERROR.message(), traceId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiStatus> handleException(Exception error,
                                                          HttpServletRequest request) {
        String traceId = traceId();
        if (isSchemaMismatch(error)) {
            log.error("api_schema_mismatch traceId={} path={} message={}", traceId, request.getRequestURI(), error.getMessage(), error);
            diagnosisLogClient.pushError(error, request.getRequestURI(), traceId, "ERROR");
            return ResponseEntity.status(ErrorCode.SYSTEM_SCHEMA_MISMATCH.status())
                    .body(ApiStatus.error(ErrorCode.SYSTEM_SCHEMA_MISMATCH.name(), ErrorCode.SYSTEM_SCHEMA_MISMATCH.message(), traceId));
        }
        log.error("api_internal_error traceId={} path={} message={}", traceId, request.getRequestURI(), error.getMessage(), error);
        diagnosisLogClient.pushError(error, request.getRequestURI(), traceId, "ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiStatus.error(ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.message(), traceId));
    }

    private static String formatFieldError(FieldError error) {
        return error.getField() + " " + (error.getDefaultMessage() == null ? "不合法" : error.getDefaultMessage());
    }

    private static boolean isSchemaMismatch(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            String className = cursor.getClass().getName();
            String message = cursor.getMessage() == null ? "" : cursor.getMessage();
            if (className.contains("BadSqlGrammarException")
                    || className.contains("SQLSyntaxErrorException")
                    || message.contains("Unknown column")
                    || message.contains("Unknown table")
                    || message.contains("doesn't exist")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static boolean isTimeout(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            String className = cursor.getClass().getName().toLowerCase();
            String message = cursor.getMessage() == null ? "" : cursor.getMessage().toLowerCase();
            if (cursor instanceof ResourceAccessException
                    || className.contains("timeout")
                    || message.contains("timed out")
                    || message.contains("timeout")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static String traceId() {
        String current = MDC.get("traceId");
        if (current != null && !current.isBlank()) {
            return current;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

}
