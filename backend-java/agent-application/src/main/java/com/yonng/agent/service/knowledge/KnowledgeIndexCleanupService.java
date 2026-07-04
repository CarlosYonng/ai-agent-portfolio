package com.yonng.agent.service.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yonng.agent.domain.knowledge.KnowledgeBase;
import com.yonng.agent.domain.knowledge.KnowledgeDocument;
import com.yonng.agent.exception.BusinessException;
import com.yonng.agent.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库外部索引清理网关。
 *
 * <p>删除知识库或文档时，Java 后端必须同步清理 Qdrant 向量和 Neo4j 图谱。
 * 这些外部存储不参与 MySQL 本地事务，因此这里把删除操作设计为幂等、失败即抛错，
 * 由上层事务阻止 MySQL 元数据提前删除，避免管理台状态和检索索引脱节。</p>
 */
@Service
public class KnowledgeIndexCleanupService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexCleanupService.class);

    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public KnowledgeIndexCleanupService(Environment environment, ObjectMapper objectMapper) {
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(propertyLong("agent.index-cleanup-connect-timeout-ms", 3000)))
                .build();
    }

    /**
     * 按知识库维度清理外部索引，Qdrant 直接按 kb_id 过滤，Neo4j 兼容旧数据按 doc_id 逐条删除。
     */
    public void cleanupKnowledgeBase(KnowledgeBase knowledgeBase, List<KnowledgeDocument> documents) {
        try {
            deleteQdrantByKnowledgeBase(knowledgeBase);
            for (KnowledgeDocument document : documents) {
                deleteNeo4jByDocument(document);
            }
            cleanupNeo4jOrphanEntities(knowledgeBase.getCustomerId());
            log.warn("kb_external_index_deleted kbId={} customerId={} documentCount={}",
                    knowledgeBase.getId(), knowledgeBase.getCustomerId(), documents.size());
        } catch (RuntimeException ex) {
            throw cleanupFailed("知识库外部索引清理失败，数据库删除已回滚，请稍后重试", ex);
        }
    }

    /**
     * 按文档维度清理外部索引，所有删除请求都可以安全重试。
     */
    public void cleanupDocument(KnowledgeDocument document) {
        try {
            deleteQdrantByDocument(document);
            deleteNeo4jByDocument(document);
            cleanupNeo4jOrphanEntities(document.getCustomerId());
            log.warn("document_external_index_deleted docId={} kbId={} customerId={}",
                    document.getId(), document.getKbId(), document.getCustomerId());
        } catch (RuntimeException ex) {
            throw cleanupFailed("文档外部索引清理失败，数据库删除已回滚，请稍后重试", ex);
        }
    }

    private void deleteQdrantByKnowledgeBase(KnowledgeBase knowledgeBase) {
        List<Map<String, Object>> conditions = new ArrayList<>();
        addMatch(conditions, "kb_id", knowledgeBase.getId());
        addMatch(conditions, "customer_id", knowledgeBase.getCustomerId());
        postQdrantDelete(conditions);
    }

    private void deleteQdrantByDocument(KnowledgeDocument document) {
        List<Map<String, Object>> conditions = new ArrayList<>();
        addMatch(conditions, "doc_id", String.valueOf(document.getId()));
        addMatch(conditions, "kb_id", document.getKbId());
        addMatch(conditions, "customer_id", document.getCustomerId());
        postQdrantDelete(conditions);
    }

    private void postQdrantDelete(List<Map<String, Object>> conditions) {
        Map<String, Object> payload = Map.of("filter", Map.of("must", conditions));
        HttpResponse<String> response = sendJson(
                "POST",
                qdrantBaseUrl() + "/collections/" + property("agent.qdrant-collection", "kb_chunks") + "/points/delete?wait=true",
                payload,
                Map.of()
        );
        if (response.statusCode() / 100 != 2 || !qdrantResultOk(response.body())) {
            throw new IllegalStateException("Qdrant 删除失败 status=" + response.statusCode() + " body=" + response.body());
        }
    }

    private void deleteNeo4jByDocument(KnowledgeDocument document) {
        String cypher = """
                MATCH (d:Document {doc_id: $doc_id})
                WHERE $customer_id IS NULL OR d.customer_id = $customer_id
                OPTIONAL MATCH (d)-[:HAS_CHUNK]->(c:Chunk)
                WITH d, collect(DISTINCT c) AS chunks
                DETACH DELETE d
                WITH chunks
                UNWIND chunks AS c
                DETACH DELETE c
                """;
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("doc_id", String.valueOf(document.getId()));
        parameters.put("customer_id", document.getCustomerId());
        postNeo4j(cypher, parameters);
    }

    private void cleanupNeo4jOrphanEntities(Long customerId) {
        String cypher = """
                MATCH (e:Entity)
                WHERE ($customer_id IS NULL OR e.customer_id = $customer_id)
                  AND NOT (()-[:MENTIONS]->(e))
                DELETE e
                """;
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("customer_id", customerId);
        postNeo4j(cypher, parameters);
    }

    private void postNeo4j(String cypher, Map<String, Object> parameters) {
        Map<String, Object> statement = new LinkedHashMap<>();
        statement.put("statement", cypher);
        statement.put("parameters", parameters);
        Map<String, Object> payload = Map.of("statements", List.of(statement));
        HttpResponse<String> response = sendJson(
                "POST",
                neo4jBaseUrl() + "/db/" + property("agent.neo4j-database", "neo4j") + "/tx/commit",
                payload,
                Map.of("Authorization", basicAuthHeader())
        );
        if (response.statusCode() / 100 != 2 || !neo4jResultOk(response.body())) {
            throw new IllegalStateException("Neo4j 删除失败 status=" + response.statusCode() + " body=" + response.body());
        }
    }

    private HttpResponse<String> sendJson(String method,
                                          String url,
                                          Map<String, Object> payload,
                                          Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(propertyLong("agent.index-cleanup-read-timeout-ms", 10000)))
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
            headers.forEach(builder::header);
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new IllegalStateException("外部索引 HTTP 调用失败 url=" + url, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("外部索引 HTTP 调用被中断 url=" + url, ex);
        }
    }

    private boolean qdrantResultOk(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String status = root.path("status").asText("");
            return status.isBlank() || "ok".equalsIgnoreCase(status);
        } catch (IOException ex) {
            throw new IllegalStateException("Qdrant 响应解析失败 body=" + body, ex);
        }
    }

    private boolean neo4jResultOk(String body) {
        try {
            JsonNode errors = objectMapper.readTree(body).path("errors");
            return errors.isArray() && errors.isEmpty();
        } catch (IOException ex) {
            throw new IllegalStateException("Neo4j 响应解析失败 body=" + body, ex);
        }
    }

    private static void addMatch(List<Map<String, Object>> conditions, String key, Object value) {
        if (value != null) {
            conditions.add(Map.of("key", key, "match", Map.of("value", value)));
        }
    }

    private String qdrantBaseUrl() {
        return property("agent.qdrant-url", "http://localhost:6333").replaceAll("/+$", "");
    }

    private String neo4jBaseUrl() {
        return property("agent.neo4j-http-url", "http://localhost:7474").replaceAll("/+$", "");
    }

    private String basicAuthHeader() {
        String user = property("agent.neo4j-user", "neo4j");
        String password = property("agent.neo4j-password", "agent123456");
        String token = Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    private BusinessException cleanupFailed(String message, RuntimeException ex) {
        log.error("knowledge_index_cleanup_failed message={}", message, ex);
        return new BusinessException(ErrorCode.KB_INDEX_CLEANUP_FAILED, message);
    }

    private String property(String key, String fallback) {
        return environment.getProperty(key, fallback);
    }

    private long propertyLong(String key, long fallback) {
        Long value = environment.getProperty(key, Long.class);
        return value == null ? fallback : value;
    }
}
