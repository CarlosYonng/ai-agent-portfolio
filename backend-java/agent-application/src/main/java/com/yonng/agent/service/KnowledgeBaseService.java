package com.yonng.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonng.agent.domain.KbDocChunk;
import com.yonng.agent.domain.KgRelation;
import com.yonng.agent.domain.KnowledgeBase;
import com.yonng.agent.domain.KnowledgeDocument;
import com.yonng.agent.dto.DocumentCreateRequest;
import com.yonng.agent.dto.DocumentUpdateRequest;
import com.yonng.agent.dto.KnowledgeBaseResponse;
import com.yonng.agent.dto.KnowledgeBaseRequest;
import com.yonng.agent.dto.KnowledgeBaseUpdateRequest;
import com.yonng.agent.dto.KnowledgeDocumentResponse;
import com.yonng.agent.mapper.KbDocChunkMapper;
import com.yonng.agent.mapper.KgRelationMapper;
import com.yonng.agent.mapper.KnowledgeBaseMapper;
import com.yonng.agent.mapper.KnowledgeDocumentMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 知识库服务。
 *
 * <p>使用 MyBatis Plus Mapper 操作知识库和文档元数据。</p>
 */
@Service
public class KnowledgeBaseService {

    private static final Set<String> VISIBILITIES = Set.of("PRIVATE", "TEAM", "PUBLIC");
    private static final Set<String> DOCUMENT_STATUSES = Set.of("PENDING", "INDEXED", "FAILED");

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KbDocChunkMapper kbDocChunkMapper;
    private final KgRelationMapper kgRelationMapper;

    public KnowledgeBaseService(KnowledgeBaseMapper knowledgeBaseMapper,
                                KnowledgeDocumentMapper knowledgeDocumentMapper,
                                KbDocChunkMapper kbDocChunkMapper,
                                KgRelationMapper kgRelationMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.kbDocChunkMapper = kbDocChunkMapper;
        this.kgRelationMapper = kgRelationMapper;
    }

    /**
     * 创建知识库，并返回新生成的主键。
     */
    public Long createKnowledgeBase(KnowledgeBaseRequest request) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setTenantId(request.getTenantId());
        knowledgeBase.setName(request.getName());
        knowledgeBase.setDescription(request.getDescription());
        knowledgeBase.setVisibility(normalizeVisibility(request.getVisibility()));
        knowledgeBaseMapper.insert(knowledgeBase);
        return Objects.requireNonNull(knowledgeBase.getId());
    }

    /**
     * 查询租户下的知识库列表。
     */
    public List<KnowledgeBaseResponse> listKnowledgeBases(Long tenantId) {
        return knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getTenantId, tenantId)
                        .orderByDesc(KnowledgeBase::getId))
                .stream()
                .map(KnowledgeBaseResponse::from)
                .toList();
    }

    // ==================== 知识库单条操作 ====================

    /**
     * 根据 ID 查询单个知识库。
     */
    public KnowledgeBaseResponse getKnowledgeBase(Long id) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(id);
        if (knowledgeBase == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "知识库不存在");
        }
        return KnowledgeBaseResponse.from(knowledgeBase);
    }

    /**
     * 更新知识库信息。只传非 null 的字段才会被更新。
     */
    public void updateKnowledgeBase(Long id, KnowledgeBaseUpdateRequest request) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(id);
        if (request.getName() != null) {
            kb.setName(request.getName());
        }
        if (request.getDescription() != null) {
            kb.setDescription(request.getDescription());
        }
        if (request.getVisibility() != null) {
            kb.setVisibility(normalizeVisibility(request.getVisibility()));
        }
        ensureUpdated(knowledgeBaseMapper.updateById(kb), "知识库不存在");
    }

    /**
     * 删除知识库及其下所有文档。
     */
    @Transactional
    public void deleteKnowledgeBase(Long id) {
        // MySQL 负责可见的管理台元数据清理；Qdrant/Neo4j 在线索引由重建脚本或运维任务继续兜底清理。
        List<KnowledgeDocument> documents = knowledgeDocumentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKbId, id));
        documents.forEach(document -> deleteDocumentMetadata(document.getId()));
        ensureUpdated(knowledgeBaseMapper.deleteById(id), "知识库不存在");
    }

    // ==================== 文档操作 ====================

    /**
     * 登记文档元数据。实际解析和入向量库由 Python 脚本或 AI 服务处理。
     */
    public Long createDocument(DocumentCreateRequest request) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setTenantId(request.getTenantId());
        document.setKbId(request.getKbId());
        document.setTitle(request.getTitle());
        document.setSourceType(normalizeSourceType(request.getSourceType()));
        document.setSourceUri(request.getSourceUri());
        document.setStatus("PENDING");
        knowledgeDocumentMapper.insert(document);
        return Objects.requireNonNull(document.getId());
    }

    /**
     * 查询文档列表，返回稳定 DTO，避免 API 层暴露 MyBatis 实体或 Map。
     */
    public List<KnowledgeDocumentResponse> listDocuments(Long tenantId, Long kbId) {
        return knowledgeDocumentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                        .select(KnowledgeDocument::getId,
                                KnowledgeDocument::getTenantId,
                                KnowledgeDocument::getKbId,
                                KnowledgeDocument::getTitle,
                                KnowledgeDocument::getSourceType,
                                KnowledgeDocument::getSourceUri,
                                KnowledgeDocument::getStatus,
                                KnowledgeDocument::getVersion,
                                KnowledgeDocument::getCreatedAt)
                        .eq(KnowledgeDocument::getTenantId, tenantId)
                        .eq(KnowledgeDocument::getKbId, kbId)
                        .orderByDesc(KnowledgeDocument::getId))
                .stream()
                .map(KnowledgeDocumentResponse::from)
                .toList();
    }

    /**
     * 根据 ID 查询单个文档。
     */
    public KnowledgeDocumentResponse getDocument(Long id) {
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(id);
        if (document == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在");
        }
        return KnowledgeDocumentResponse.from(document);
    }

    /**
     * 更新文档元数据。只传非 null 的字段才会被更新。
     */
    public void updateDocument(Long id, DocumentUpdateRequest request) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(id);
        if (request.getTitle() != null) {
            doc.setTitle(request.getTitle());
        }
        if (request.getSourceType() != null) {
            doc.setSourceType(normalizeSourceType(request.getSourceType()));
        }
        if (request.getSourceUri() != null) {
            doc.setSourceUri(request.getSourceUri());
        }
        ensureUpdated(knowledgeDocumentMapper.updateById(doc), "文档不存在");
    }

    /**
     * 更新文档处理状态。
     */
    public void updateDocumentStatus(Long id, String status) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(id);
        doc.setStatus(normalizeDocumentStatus(status));
        ensureUpdated(knowledgeDocumentMapper.updateById(doc), "文档不存在");
    }

    /**
     * 删除文档。MySQL 侧先清理关系证据和分片，外部 Qdrant 向量和 Neo4j 图谱由索引脚本按 doc_id 兜底清理。
     */
    @Transactional
    public void deleteDocument(Long id) {
        deleteDocumentMetadata(id);
    }

    private void deleteDocumentMetadata(Long docId) {
        // 查出该文档的所有 chunk ID
        List<Long> chunkIds = kbDocChunkMapper.selectList(
                new LambdaQueryWrapper<KbDocChunk>()
                        .select(KbDocChunk::getId)
                        .eq(KbDocChunk::getDocId, docId))
                .stream()
                .map(KbDocChunk::getId)
                .toList();
        if (!chunkIds.isEmpty()) {
            // kg_relation 证据引用 chunk，必须先删除，否则外键阻断删除。
            kgRelationMapper.delete(new LambdaQueryWrapper<KgRelation>()
                    .in(KgRelation::getEvidenceChunkId, chunkIds));
            kbDocChunkMapper.delete(new LambdaQueryWrapper<KbDocChunk>()
                    .in(KbDocChunk::getId, chunkIds));
        }
        ensureUpdated(knowledgeDocumentMapper.deleteById(docId), "文档不存在");
    }

    private static String normalizeVisibility(String visibility) {
        String normalized = normalizeOrDefault(visibility, "PRIVATE");
        if (!VISIBILITIES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "visibility 仅支持 PRIVATE/TEAM/PUBLIC");
        }
        return normalized;
    }

    private static String normalizeSourceType(String sourceType) {
        return normalizeOrDefault(sourceType, "MARKDOWN");
    }

    private static String normalizeDocumentStatus(String status) {
        String normalized = normalizeOrDefault(status, null);
        if (normalized == null || !DOCUMENT_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status 仅支持 PENDING/INDEXED/FAILED");
        }
        return normalized;
    }

    private static String normalizeOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static void ensureUpdated(int rows, String message) {
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }
    }
}
