package com.yonng.agent.service.system;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * ai-agent-portfolio 业务指标出口。
 *
 * <p>这里集中定义 Prometheus 指标名称和低基数标签，避免把用户输入、traceId、文档 ID
 * 这类高基数字段写进 label。traceId 仍保留在日志和异常 payload 中用于诊断关联。</p>
 */
@Service
public class PortfolioMetricsService {

    private final MeterRegistry registry;

    public PortfolioMetricsService(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordJavaHttpRequest(String endpoint, String method, int status, Duration duration) {
        String statusLabel = String.valueOf(status);
        Counter.builder("portfolio_java_http_requests")
                .description("Java 后端 HTTP 请求总数")
                .tag("endpoint", safe(endpoint))
                .tag("method", safe(method))
                .tag("status", statusLabel)
                .register(registry)
                .increment();
        Timer.builder("portfolio_java_http_request_duration")
                .description("Java 后端 HTTP 请求耗时")
                .tag("endpoint", safe(endpoint))
                .tag("method", safe(method))
                .publishPercentileHistogram()
                .register(registry)
                .record(duration);
    }

    public void recordChatRequest(String status) {
        Counter.builder("portfolio_chat_requests")
                .description("聊天问答请求总数")
                .tag("status", safe(status))
                .register(registry)
                .increment();
    }

    public void recordAiServiceCall(Duration duration, String status, String errorType) {
        Timer.builder("portfolio_chat_ai_service_duration")
                .description("Java 调用 Python AI 服务耗时")
                .publishPercentileHistogram()
                .register(registry)
                .record(duration);
        if (!"success".equals(status)) {
            Counter.builder("portfolio_chat_ai_service_errors")
                    .description("Java 调用 Python AI 服务错误数")
                    .tag("error_type", safe(errorType))
                    .register(registry)
                    .increment();
        }
    }

    public void recordKbUpload(String status) {
        Counter.builder("portfolio_kb_upload")
                .description("知识库上传请求总数")
                .tag("status", safe(status))
                .register(registry)
                .increment();
    }

    public void recordKbIngestion(String stage, String status, Duration duration, String errorType) {
        Counter.builder("portfolio_kb_ingestion")
                .description("知识库入库阶段执行次数")
                .tag("stage", safe(stage))
                .tag("status", safe(status))
                .register(registry)
                .increment();
        Timer.builder("portfolio_kb_ingestion_duration")
                .description("知识库入库阶段耗时")
                .tag("stage", safe(stage))
                .publishPercentileHistogram()
                .register(registry)
                .record(duration);
        if (!"success".equals(status)) {
            Counter.builder("portfolio_kb_ingestion_failures")
                    .description("知识库入库阶段失败数")
                    .tag("stage", safe(stage))
                    .tag("error_type", safe(errorType))
                    .register(registry)
                    .increment();
        }
    }

    public void recordRedisOperation(String operation, String status, Duration duration) {
        Counter.builder("portfolio_redis_operations")
                .description("Redis 操作总数")
                .tag("operation", safe(operation))
                .tag("status", safe(status))
                .register(registry)
                .increment();
        Timer.builder("portfolio_redis_operation_duration")
                .description("Redis 操作耗时")
                .tag("operation", safe(operation))
                .publishPercentileHistogram()
                .register(registry)
                .record(duration);
    }

    public void recordMysqlOperation(String operation, String status, Duration duration) {
        Counter.builder("portfolio_mysql_operations")
                .description("MySQL 业务操作总数")
                .tag("operation", safe(operation))
                .tag("status", safe(status))
                .register(registry)
                .increment();
        Timer.builder("portfolio_mysql_operation_duration")
                .description("MySQL 业务操作耗时")
                .tag("operation", safe(operation))
                .publishPercentileHistogram()
                .register(registry)
                .record(duration);
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_./{}:-]", "_");
    }
}
