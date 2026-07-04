package com.yonng.agent.service.knowledge;

import com.yonng.agent.dto.knowledge.DocumentCreateRequest;
import com.yonng.agent.dto.knowledge.DocumentDownloadInfo;
import com.yonng.agent.dto.knowledge.KnowledgeDocumentResponse;
import com.yonng.agent.exception.BusinessException;
import com.yonng.agent.exception.ErrorCode;
import com.yonng.agent.service.system.PortfolioMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 文档上传入库服务。
 *
 * <p>管理台上传文件后先落本地文件和 MySQL 元数据，再触发 Python 入库脚本完成切片、向量和图谱写入。
 * 这样页面、元数据和检索索引共享同一条状态流转，不再依赖人工执行脚本后手动改状态。</p>
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    /**
     * 控制同时只能有一个入库脚本在执行。
     *
     * 批量上传时前端逐个快速提交文件，后端为每个文件启动一个 Python 进程。
     * 这些进程并发调用外部 Embedding API 很容易触发服务端限流（429），
     * 导致大部分脚本失败、只有第一个成功。用信号量将并发度降为 1，
     * 后续入库任务排队等待，逐个执行，避免 API 限流。
     */
    private static final Semaphore INGEST_SEMAPHORE = new Semaphore(1);

    private final Environment environment;
    private final KnowledgeBaseService knowledgeBaseService;
    private final PortfolioMetricsService metricsService;

    public DocumentIngestionService(Environment environment,
                                    KnowledgeBaseService knowledgeBaseService,
                                    PortfolioMetricsService metricsService) {
        this.environment = environment;
        this.knowledgeBaseService = knowledgeBaseService;
        this.metricsService = metricsService;
    }

    /**
     * 保存上传文件并触发后台入库脚本，立即返回可轮询的文档状态。
     */
    public KnowledgeDocumentResponse uploadAndIngest(Long kbId,
                                                     Long customerId,
                                                     String title,
                                                     String originalFilename,
                                                     InputStream inputStream) {
        long startedAt = System.nanoTime();
        try {
            String safeFilename = sanitizeFilename(originalFilename);
            validateSupportedFile(safeFilename);
            Path storedFile = storeUploadedFile(kbId, safeFilename, inputStream);
            log.info("document_upload_received kbId={} customerId={} originalFilename={} storedFile={}",
                    kbId, customerId, safeFilename, storedFile);

            DocumentCreateRequest request = new DocumentCreateRequest();
            request.setKbId(kbId);
            request.setTitle(normalizeTitle(title, safeFilename));
            request.setSourceType(sourceTypeFor(safeFilename));
            request.setSourceUri(storedFile.toString());
            Long docId = knowledgeBaseService.createDocument(request, customerId);
            knowledgeBaseService.updateDocumentIngestProgress(docId, "PENDING", "UPLOADED", 5, 0, null, "");
            metricsService.recordKbUpload("accepted");
            metricsService.recordKbIngestion("upload", "success", Duration.ofNanos(System.nanoTime() - startedAt), "none");
            runIngestInBackground(kbId, customerId, docId, request.getTitle(), storedFile, "document_upload_indexed");
            return knowledgeBaseService.getDocument(docId);
        } catch (RuntimeException error) {
            metricsService.recordKbUpload("failed");
            metricsService.recordKbIngestion("upload", "error", Duration.ofNanos(System.nanoTime() - startedAt), error.getClass().getSimpleName());
            throw error;
        }
    }

    /**
     * 复用已上传源文件重新执行入库链路。
     */
    public KnowledgeDocumentResponse retryIngest(Long docId, Long customerId) {
        KnowledgeDocumentResponse document = knowledgeBaseService.getDocument(docId);
        if (customerId != null && document.customerId() != null && !customerId.equals(document.customerId())) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        Long effectiveCustomerId = document.customerId() != null ? document.customerId() : customerId;
        if (effectiveCustomerId == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_INGEST_FAILED);
        }
        DocumentDownloadInfo sourceFile = knowledgeBaseService.getDocumentDownloadInfo(docId, customerId);
        knowledgeBaseService.updateDocumentIngestProgress(docId, "PENDING", "RETRYING", 5, 0, null, "");
        runIngestInBackground(document.kbId(), effectiveCustomerId, docId, document.title(), sourceFile.path(), "document_retry_indexed");
        return knowledgeBaseService.getDocument(docId);
    }

    private void runIngestInBackground(Long kbId,
                                       Long customerId,
                                       Long docId,
                                       String title,
                                       Path storedFile,
                                       String successEvent) {
        CompletableFuture.runAsync(() -> {
            try {
                INGEST_SEMAPHORE.acquire();
                log.info("semaphore_acquired docId={} availablePermits={}", docId, INGEST_SEMAPHORE.availablePermits());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                knowledgeBaseService.updateDocumentIngestProgress(docId, "FAILED", "FAILED", null, null, null, "入库任务被中断");
                metricsService.recordKbIngestion("queue", "error", Duration.ZERO, "InterruptedException");
                return;
            }
            try {
                long startedAt = System.nanoTime();
                runIngestScript(kbId, customerId, docId, title, storedFile);
                knowledgeBaseService.updateDocumentIngestProgress(docId, "INDEXED", "INDEXED", 100, null, null, "");
                metricsService.recordKbIngestion("knowledge_ingestion", "success", Duration.ofNanos(System.nanoTime() - startedAt), "none");
                log.info("{} docId={} kbId={} customerId={} file={}", successEvent, docId, kbId, customerId, storedFile);
            } catch (RuntimeException ex) {
                knowledgeBaseService.updateDocumentIngestProgress(docId, "FAILED", "FAILED", null, null, null, summarizeError(ex));
                metricsService.recordKbIngestion("knowledge_ingestion", "error", Duration.ZERO, ex.getClass().getSimpleName());
                log.warn("文档自动入库失败 docId={} file={}", docId, storedFile, ex);
            } finally {
                INGEST_SEMAPHORE.release();
                log.info("semaphore_released docId={} availablePermits={}", docId, INGEST_SEMAPHORE.availablePermits());
            }
        });
    }

    private Path storeUploadedFile(Long kbId, String safeFilename, InputStream inputStream) {
        try {
            Path dir = resolvePath(property("agent.kb-upload-dir", "data/uploads/kb-docs")).resolve("kb-" + kbId);
            Files.createDirectories(dir);
            Path target = dir.resolve(Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + "-" + safeFilename);
            Files.copy(inputStream, target);
            return target.toAbsolutePath().normalize();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.DOCUMENT_INGEST_FAILED);
        }
    }

    private void runIngestScript(Long kbId, Long customerId, Long docId, String title, Path storedFile) {
        Path script = resolveIngestScript();
        List<String> command = new ArrayList<>();
        command.add(property("agent.kb-ingest-python", "python3"));
        command.add(script.toString());
        command.add("--kb-id");
        command.add(String.valueOf(kbId));
        command.add("--source-file");
        command.add(storedFile.toString());
        command.add("--doc-id");
        command.add(String.valueOf(docId));
        command.add("--customer-id");
        command.add(String.valueOf(customerId));
        command.add("--title");
        command.add(title);
        command.add("--strict-mysql");

        ProcessBuilder builder = new ProcessBuilder(command);
        Path workDir = script.getParent() != null && script.getParent().getParent() != null
                ? script.getParent().getParent()
                : Path.of(System.getProperty("user.dir"));
        builder.directory(workDir.toFile());
        builder.redirectErrorStream(true);
        String mysqlDsn = property("agent.kb-ingest-mysql-dsn", "");
        if (!mysqlDsn.isBlank()) {
            builder.environment().put("MYSQL_DSN", mysqlDsn);
        }
        copyOptionalEnv(builder, "agent.kb-ingest-embedding-provider", "EMBEDDING_PROVIDER");
        copyOptionalEnv(builder, "agent.kb-ingest-embedding-dimensions", "EMBEDDING_DIMENSIONS");

        try {
            Process process = builder.start();
            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    return "读取脚本输出失败：" + ex.getMessage();
                }
            });
            boolean finished = process.waitFor(propertyInt("agent.kb-ingest-timeout-seconds", 180), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                String output = outputFuture.getNow("");
                throw new IllegalStateException("入库脚本执行超时：" + output);
            }
            String output = outputFuture.join();
            if (process.exitValue() != 0) {
                throw new IllegalStateException("入库脚本执行失败 exit=" + process.exitValue() + " output=" + output);
            }
            log.info("document_ingest_script_completed docId={} kbId={} customerId={} file={} exit=0",
                    docId, kbId, customerId, storedFile);
            log.debug("document_ingest_script_output docId={} output={}", docId, output);
        } catch (IOException ex) {
            throw new IllegalStateException("无法启动入库脚本", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("入库脚本执行被中断", ex);
        }
    }

    private Path resolveIngestScript() {
        String configuredPath = property("agent.kb-ingest-script-path", "");
        if (!configuredPath.isBlank()) {
            return resolvePath(configuredPath);
        }
        Path fromRepoRoot = resolvePath("scripts/ingest_docs.py");
        if (Files.exists(fromRepoRoot)) {
            return fromRepoRoot;
        }
        Path fromBackendDir = resolvePath("../scripts/ingest_docs.py");
        if (Files.exists(fromBackendDir)) {
            return fromBackendDir;
        }
        throw new BusinessException(ErrorCode.DOCUMENT_INGEST_FAILED);
    }

    private static Path resolvePath(String value) {
        Path path = Path.of(value);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return Path.of(System.getProperty("user.dir")).resolve(path).normalize();
    }

    private String property(String key, String fallback) {
        return environment.getProperty(key, fallback);
    }

    private int propertyInt(String key, int fallback) {
        Integer value = environment.getProperty(key, Integer.class);
        return value == null ? fallback : value;
    }

    private void copyOptionalEnv(ProcessBuilder builder, String propertyKey, String envKey) {
        String value = property(propertyKey, "");
        if (!value.isBlank()) {
            // 只在显式配置 KB_INGEST_* 时覆盖脚本环境；默认让 ingest_docs.py 读取项目 .env 中的阿里百炼配置。
            builder.environment().put(envKey, value);
        }
    }

    private static String sanitizeFilename(String originalFilename) {
        String candidate = originalFilename == null || originalFilename.isBlank() ? "document.md" : originalFilename;
        String withoutPath = Path.of(candidate).getFileName().toString();
        String sanitized = withoutPath.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        return sanitized.isBlank() ? "document.md" : sanitized;
    }

    private static void validateSupportedFile(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".md") && !lower.endsWith(".txt")) {
            throw new BusinessException(ErrorCode.DOCUMENT_UPLOAD_INVALID);
        }
    }

    private static String normalizeTitle(String title, String safeFilename) {
        return title == null || title.isBlank() ? safeFilename : title.trim();
    }

    private static String sourceTypeFor(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".md") ? "MARKDOWN" : "TEXT";
    }

    private static String summarizeError(RuntimeException ex) {
        String message = ex.getMessage();
        if ((message == null || message.isBlank()) && ex.getCause() != null) {
            message = ex.getCause().getMessage();
        }
        if (message == null || message.isBlank()) {
            return "入库流程异常，请查看后端日志。";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
