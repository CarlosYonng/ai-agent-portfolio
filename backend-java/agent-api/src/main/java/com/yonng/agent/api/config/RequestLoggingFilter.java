package com.yonng.agent.api.config;

import com.yonng.agent.service.system.PortfolioMetricsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * 请求日志过滤器。
 *
 * <p>为每个 HTTP 请求建立 traceId 和 MDC 上下文，业务日志、异常日志、访问日志因此可以按同一个 traceId 关联。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private final PortfolioMetricsService metricsService;

    public RequestLoggingFilter(PortfolioMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        String traceId = resolveTraceId(request);
        putMdc(request, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            putAuthenticatedUser();
            long durationMs = System.currentTimeMillis() - startedAt;
            int status = response.getStatus();
            metricsService.recordJavaHttpRequest(normalizeEndpoint(request.getRequestURI()), request.getMethod(), status, Duration.ofMillis(durationMs));
            if (status >= 500) {
                log.error("http_request_completed status={} durationMs={}", status, durationMs);
            } else if (status >= 400) {
                log.warn("http_request_completed status={} durationMs={}", status, durationMs);
            } else {
                log.info("http_request_completed status={} durationMs={}", status, durationMs);
            }
            MDC.clear();
        }
    }

    private static String normalizeEndpoint(String uri) {
        if (uri == null || uri.isBlank()) {
            return "unknown";
        }
        if (uri.matches("^/api/chat/sessions/[^/]+/messages$")) {
            return "/api/chat/sessions/{sessionId}/messages";
        }
        if (uri.matches("^/api/kb/[^/]+/documents/upload$")) {
            return "/api/kb/{kbId}/documents/upload";
        }
        if (uri.matches("^/api/kb/documents/[^/]+/retry$")) {
            return "/api/kb/documents/{id}/retry";
        }
        if (uri.matches("^/api/kb/documents/[^/]+(/download)?$")) {
            return uri.endsWith("/download") ? "/api/kb/documents/{id}/download" : "/api/kb/documents/{id}";
        }
        if (uri.matches("^/api/kb/[^/]+/documents$")) {
            return "/api/kb/{kbId}/documents";
        }
        if (uri.matches("^/api/kb/[^/]+$")) {
            return "/api/kb/{id}";
        }
        return uri;
    }

    private void putMdc(HttpServletRequest request, String traceId) {
        MDC.put("traceId", traceId);
        MDC.put("method", request.getMethod());
        MDC.put("uri", request.getRequestURI());
        MDC.put("clientIp", clientIp(request));
        MDC.put("userAgent", safeHeader(request, "User-Agent"));
    }

    private void putAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() != null) {
            MDC.put("userId", String.valueOf(authentication.getPrincipal()));
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String candidate = request.getHeader(TRACE_ID_HEADER);
        if (candidate == null || candidate.isBlank()) {
            return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        return candidate.length() > 64 ? candidate.substring(0, 64) : candidate;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return realIp == null || realIp.isBlank() ? request.getRemoteAddr() : realIp;
    }

    private static String safeHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.length() > 256 ? value.substring(0, 256) : value;
    }
}
