package com.yonng.agent.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonng.agent.domain.knowledge.KnowledgeBase;
import com.yonng.agent.domain.knowledge.KnowledgeDocument;
import com.yonng.agent.dto.knowledge.DocumentCreateRequest;
import com.yonng.agent.dto.knowledge.DocumentDownloadInfo;
import com.yonng.agent.dto.knowledge.DocumentUpdateRequest;
import com.yonng.agent.dto.knowledge.KnowledgeBaseResponse;
import com.yonng.agent.dto.knowledge.KnowledgeBaseRequest;
import com.yonng.agent.dto.knowledge.KnowledgeBaseUpdateRequest;
import com.yonng.agent.dto.knowledge.KnowledgeDocumentResponse;
import com.yonng.agent.exception.BusinessException;
import com.yonng.agent.exception.ErrorCode;
import com.yonng.agent.mapper.knowledge.KnowledgeBaseMapper;
import com.yonng.agent.mapper.knowledge.KnowledgeDocumentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
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

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private static final Set<String> VISIBILITIES = Set.of("PRIVATE", "TEAM", "PUBLIC");
    private static final Set<String> DOCUMENT_STATUSES = Set.of("PENDING", "INDEXED", "FAILED");

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeIndexCleanupService indexCleanupService;
    private final Environment environment;

    public KnowledgeBaseService(KnowledgeBaseMapper knowledgeBaseMapper,
                                KnowledgeDocumentMapper knowledgeDocumentMapper,
                                KnowledgeIndexCleanupService indexCleanupService,
                                Environment environment) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.indexCleanupService = indexCleanupService;
        this.environment = environment;
    }

    /**
     * 创建知识库，返回新生成的主键。
     *
     * <p>权限控制：{@code USER} 角色无权限创建。所有有权限的用户创建的 KB 均归属其所属客户，
     * 数据范围通过 {@code RoleAccess} 在控制器层控制。</p>
     *
     * @param request        创建请求，包含名称、描述、可见性
     * @param userCustomerId 当前登录用户的客户 ID
     * @param role           当前登录用户的角色编码
     * @return 新知识库的主键 ID
     * @throws BusinessException 当角色为 {@code USER} 时抛出 {@link ErrorCode#USER_CANNOT_CREATE_KB}；
     *                           当同一客户下名称重复时抛出 {@link ErrorCode#KB_NAME_EXISTS}
     */
    public Long createKnowledgeBase(KnowledgeBaseRequest request, Long userCustomerId, String role) {
        // 1. 角色检查：USER 不能创建
        if ("USER".equals(role)) {
            throw new BusinessException(ErrorCode.USER_CANNOT_CREATE_KB);
        }
        // 2. 名称唯一性校验
        if (userCustomerId != null) {
            Long count = knowledgeBaseMapper.selectCount(new LambdaQueryWrapper<KnowledgeBase>()
                    .eq(KnowledgeBase::getCustomerId, userCustomerId)
                    .eq(KnowledgeBase::getName, request.getName()));
            if (count > 0) {
                throw new BusinessException(ErrorCode.KB_NAME_EXISTS);
            }
        }
        // 3. 创建
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setCustomerId(userCustomerId);
        knowledgeBase.setName(request.getName());
        knowledgeBase.setDescription(request.getDescription());
        knowledgeBase.setVisibility(normalizeVisibility(request.getVisibility()));
        knowledgeBaseMapper.insert(knowledgeBase);
        log.info("kb_created kbId={} customerId={} role={} name={} visibility={}",
                knowledgeBase.getId(), userCustomerId, role, request.getName(), knowledgeBase.getVisibility());
        return Objects.requireNonNull(knowledgeBase.getId());
    }

    /**
     * 查询知识库列表，按创建时间倒序。
     *
     * @param customerId 客户 ID；为空时返回全部（超级管理员场景），非空时按客户隔离
     * @return 知识库响应 DTO 列表，不会返回 {@code null}
     */
    public List<KnowledgeBaseResponse> listKnowledgeBases(Long customerId) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<KnowledgeBase>()
                .orderByDesc(KnowledgeBase::getId);
        if (customerId != null) {
            wrapper.eq(KnowledgeBase::getCustomerId, customerId);
        }
        return knowledgeBaseMapper.selectList(wrapper)
                .stream()
                .map(KnowledgeBaseResponse::from)
                .toList();
    }

    // ==================== 知识库单条操作 ====================

    /**
     * 根据 ID 查询单个知识库。
     *
     * @param id 知识库主键
     * @return 知识库响应 DTO
     * @throws BusinessException 当知识库不存在时抛出 {@link ErrorCode#KB_NOT_FOUND}
     */
    public KnowledgeBaseResponse getKnowledgeBase(Long id) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(id);
        if (knowledgeBase == null) {
            throw new BusinessException(ErrorCode.KB_NOT_FOUND);
        }
        return KnowledgeBaseResponse.from(knowledgeBase);
    }

    /**
     * 更新知识库信息。
     *
     * <p>只传非 {@code null} 的字段才会被更新，未传字段保持原值不变。</p>
     *
     * @param id      知识库主键
     * @param request 更新请求，包含名称、描述、可见性（均可为空）
     * @throws BusinessException 当知识库不存在时抛出 {@link ErrorCode#KB_NOT_FOUND}
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
        log.info("kb_updated kbId={} nameChanged={} descriptionChanged={} visibilityChanged={}",
                id, request.getName() != null, request.getDescription() != null, request.getVisibility() != null);
    }

    /**
     * 删除知识库及其下所有文档。
     *
     * <p>先同步清理 Qdrant/Neo4j 外部索引，再删除 MySQL 元数据。外部索引删除失败会抛出业务异常，
     * 当前事务回滚，避免页面上元数据消失但检索仍能召回旧内容。</p>
     *
     * @param id 知识库主键
     * @throws BusinessException 当知识库不存在时抛出 {@link ErrorCode#KB_NOT_FOUND}
     */
    @Transactional
    public void deleteKnowledgeBase(Long id) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(id);
        if (knowledgeBase == null) {
            throw new BusinessException(ErrorCode.KB_NOT_FOUND);
        }
        List<KnowledgeDocument> documents = knowledgeDocumentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKbId, id));
        indexCleanupService.cleanupKnowledgeBase(knowledgeBase, documents);
        knowledgeDocumentMapper.delete(new LambdaQueryWrapper<KnowledgeDocument>().eq(KnowledgeDocument::getKbId, id));
        ensureUpdated(knowledgeBaseMapper.deleteById(id), "知识库不存在");
        log.warn("kb_deleted kbId={} documentCount={}", id, documents.size());
    }

    // ==================== 文档操作 ====================

    /**
     * 登记文档元数据。
     *
     * <p>仅写入 MySQL 元数据行，实际解析和入向量库由 Python 入库脚本或 AI 服务异步处理。</p>
     *
     * @param request    文档创建请求，包含所属知识库 ID、标题、来源类型和 URI
     * @param customerId 当前登录用户的客户 ID
     * @return 新文档的主键 ID
     */
    public Long createDocument(DocumentCreateRequest request, Long customerId) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setCustomerId(customerId);
        document.setKbId(request.getKbId());
        document.setTitle(request.getTitle());
        document.setSourceType(normalizeSourceType(request.getSourceType()));
        document.setSourceUri(request.getSourceUri());
        document.setStatus("PENDING");
        document.setIngestStage("REGISTERED");
        document.setProgressPercent(0);
        document.setChunkDone(0);
        document.setErrorMessage("");
        knowledgeDocumentMapper.insert(document);
        log.info("document_created docId={} kbId={} customerId={} title={} sourceType={} status=PENDING",
                document.getId(), request.getKbId(), customerId, request.getTitle(), document.getSourceType());
        return Objects.requireNonNull(document.getId());
    }

    /**
     * 查询指定知识库下的文档列表。
     *
     * @param customerId 客户 ID；为空时查全部（管理员场景），非空时按客户隔离
     * @param kbId       知识库主键
     * @return 文档响应 DTO 列表，按 ID 降序排列
     */
    public List<KnowledgeDocumentResponse> listDocuments(Long customerId, Long kbId) {
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<KnowledgeDocument>()
                        .select(KnowledgeDocument::getId,
                                KnowledgeDocument::getCustomerId,
                                KnowledgeDocument::getKbId,
                                KnowledgeDocument::getTitle,
                                KnowledgeDocument::getSourceType,
                                KnowledgeDocument::getSourceUri,
                                KnowledgeDocument::getStatus,
                                KnowledgeDocument::getIngestStage,
                                KnowledgeDocument::getProgressPercent,
                                KnowledgeDocument::getChunkTotal,
                                KnowledgeDocument::getChunkDone,
                                KnowledgeDocument::getErrorMessage,
                                KnowledgeDocument::getIndexedAt,
                                KnowledgeDocument::getVersion,
                                KnowledgeDocument::getCreatedAt,
                                KnowledgeDocument::getUpdatedAt)
                        .eq(KnowledgeDocument::getKbId, kbId)
                        .orderByDesc(KnowledgeDocument::getId);
        if (customerId != null) {
            wrapper.eq(KnowledgeDocument::getCustomerId, customerId);
        }
        return knowledgeDocumentMapper.selectList(wrapper)
                .stream()
                .map(KnowledgeDocumentResponse::from)
                .toList();
    }

    /**
     * 根据 ID 查询单个文档。
     *
     * @param id 文档主键
     * @return 文档响应 DTO
     * @throws BusinessException 当文档不存在时抛出 {@link ErrorCode#DOCUMENT_NOT_FOUND}
     */
    public KnowledgeDocumentResponse getDocument(Long id) {
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        return KnowledgeDocumentResponse.from(document);
    }

    /**
     * 获取文档源文件下载信息。
     *
     * <p>只允许下载本地上传落盘的源文件；URL、对象存储地址或缺失文件会返回明确业务错误。
     * 下载路径必须位于配置的 {@code agent.kb-upload-dir} 目录下，防路径穿越。</p>
     *
     * @param id         文档主键
     * @param customerId 客户 ID；非空时按客户隔离校验，为空时跳过隔离检查（平台管理员场景）
     * @return 下载信息 DTO，包含文件名和绝对路径
     * @throws BusinessException 文档不存在或无权限时抛出 {@link ErrorCode#DOCUMENT_NOT_FOUND}；
     *                           源文件缺失时抛出 {@link ErrorCode#DOCUMENT_SOURCE_FILE_MISSING}
     */
    public DocumentDownloadInfo getDocumentDownloadInfo(Long id, Long customerId) {
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(id);
        if (document == null || (customerId != null && !customerId.equals(document.getCustomerId()))) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        String sourceUri = document.getSourceUri();
        if (sourceUri == null || sourceUri.isBlank()) {
            throw new BusinessException(ErrorCode.DOCUMENT_SOURCE_FILE_MISSING);
        }
        Path path;
        try {
            path = Path.of(sourceUri).toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.DOCUMENT_SOURCE_FILE_MISSING);
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new BusinessException(ErrorCode.DOCUMENT_SOURCE_FILE_MISSING);
        }
        Path uploadRoot = resolvePath(environment.getProperty("agent.kb-upload-dir", "data/uploads/kb-docs"));
        if (!path.startsWith(uploadRoot)) {
            log.warn("document_download_rejected docId={} sourceUri={} reason=outside_upload_root", id, sourceUri);
            throw new BusinessException(ErrorCode.DOCUMENT_SOURCE_FILE_MISSING);
        }
        String filename = path.getFileName() == null ? safeDownloadName(document) : path.getFileName().toString();
        log.info("document_download_prepared docId={} kbId={} customerId={} filename={}",
                document.getId(), document.getKbId(), document.getCustomerId(), filename);
        return new DocumentDownloadInfo(document.getId(), filename, path);
    }

    /**
     * 更新文档元数据字段。
     *
     * <p>只传非 {@code null} 的字段才会被更新，未传字段保持原值不变。</p>
     *
     * @param id      文档主键
     * @param request 更新请求，包含标题、来源类型、来源 URI（均可为空）
     * @throws BusinessException 当文档不存在时抛出 {@link ErrorCode#DOCUMENT_NOT_FOUND}
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
        log.info("document_updated docId={} titleChanged={} sourceTypeChanged={} sourceUriChanged={}",
                id, request.getTitle() != null, request.getSourceType() != null, request.getSourceUri() != null);
    }

    /**
     * 更新文档处理状态（由 Python 入库脚本回调）。
     *
     * <p>状态为 {@code INDEXED} 时自动回写入库完成时间和进度 100%；状态为 {@code FAILED} 时自动标记入库阶段为失败。</p>
     *
     * @param id     文档主键
     * @param status 目标状态，支持 {@code PENDING}、{@code INDEXED}、{@code FAILED}
     * @throws BusinessException 当文档不存在时抛出 {@link ErrorCode#DOCUMENT_NOT_FOUND}；
     *                           状态值不合法时抛出 {@link ErrorCode#DOCUMENT_STATUS_INVALID}
     */
    public void updateDocumentStatus(Long id, String status) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(id);
        doc.setStatus(normalizeDocumentStatus(status));
        if ("INDEXED".equals(doc.getStatus())) {
            doc.setIngestStage("INDEXED");
            doc.setProgressPercent(100);
            doc.setIndexedAt(OffsetDateTime.now());
            doc.setErrorMessage("");
        } else if ("FAILED".equals(doc.getStatus())) {
            doc.setIngestStage("FAILED");
        }
        ensureUpdated(knowledgeDocumentMapper.updateById(doc), "文档不存在");
        log.info("document_status_updated docId={} status={}", id, doc.getStatus());
    }

    /**
     * 回写文档入库进度（由 Python 入库脚本异步回调）。
     *
     * <p>脚本按阶段写入该信息，管理台据此展示更细的处理进度；{@code status} 仍保持
     * {@code PENDING/INDEXED/FAILED} 三态，避免影响既有判断逻辑。状态为
     * {@code INDEXED} 时自动回写 100% 进度和完成时间。</p>
     *
     * @param id              文档主键
     * @param status          文档状态（可为空，不传则不更新）
     * @param ingestStage     入库阶段标识（自动截断至 64 字符）
     * @param progressPercent 进度百分比（自动钳制到 0-100）
     * @param chunkDone       已处理切片数
     * @param chunkTotal      切片总数
     * @param errorMessage    失败原因（自动截断至 1000 字符）
     * @throws BusinessException 当文档不存在时抛出 {@link ErrorCode#DOCUMENT_NOT_FOUND}
     */
    public void updateDocumentIngestProgress(Long id,
                                             String status,
                                             String ingestStage,
                                             Integer progressPercent,
                                             Integer chunkDone,
                                             Integer chunkTotal,
                                             String errorMessage) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(id);
        if (status != null) {
            doc.setStatus(normalizeDocumentStatus(status));
        }
        if (ingestStage != null && !ingestStage.isBlank()) {
            doc.setIngestStage(limit(ingestStage.trim().toUpperCase(Locale.ROOT), 64));
        }
        if (progressPercent != null) {
            doc.setProgressPercent(Math.max(0, Math.min(100, progressPercent)));
        }
        if (chunkDone != null) {
            doc.setChunkDone(Math.max(0, chunkDone));
        }
        if (chunkTotal != null) {
            doc.setChunkTotal(Math.max(0, chunkTotal));
        }
        if (errorMessage != null) {
            doc.setErrorMessage(limit(errorMessage, 1000));
        }
        if ("INDEXED".equals(doc.getStatus())) {
            doc.setIngestStage("INDEXED");
            doc.setProgressPercent(100);
            doc.setIndexedAt(OffsetDateTime.now());
            doc.setErrorMessage("");
        } else if ("FAILED".equals(doc.getStatus())) {
            doc.setIngestStage("FAILED");
        }
        ensureUpdated(knowledgeDocumentMapper.updateById(doc), "文档不存在");
        log.info("document_ingest_progress_updated docId={} status={} stage={} progress={} chunks={}/{}",
                id, doc.getStatus(), doc.getIngestStage(), doc.getProgressPercent(), doc.getChunkDone(), doc.getChunkTotal());
    }

    /**
     * 删除文档及其外部索引。
     *
     * <p>Qdrant/Neo4j 清理成功后才删除 MySQL 元数据。失败时抛出异常并回滚，
     * 让用户看到文档仍然存在，可稍后重试删除。</p>
     *
     * @param id 文档主键
     * @throws BusinessException 当文档不存在时抛出 {@link ErrorCode#DOCUMENT_NOT_FOUND}
     */
    @Transactional
    public void deleteDocument(Long id) {
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        indexCleanupService.cleanupDocument(document);
        ensureUpdated(knowledgeDocumentMapper.deleteById(id), "文档不存在");
        log.warn("document_deleted docId={}", id);
    }

    private static String normalizeVisibility(String visibility) {
        String normalized = normalizeOrDefault(visibility, "PRIVATE");
        if (!VISIBILITIES.contains(normalized)) {
            throw new BusinessException(ErrorCode.KB_VISIBILITY_INVALID);
        }
        return normalized;
    }

    private static String normalizeSourceType(String sourceType) {
        return normalizeOrDefault(sourceType, "MARKDOWN");
    }

    private static String normalizeDocumentStatus(String status) {
        String normalized = normalizeOrDefault(status, null);
        if (normalized == null || !DOCUMENT_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.DOCUMENT_STATUS_INVALID);
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
            ErrorCode code = "文档不存在".equals(message) ? ErrorCode.DOCUMENT_NOT_FOUND : ErrorCode.KB_NOT_FOUND;
            throw new BusinessException(code);
        }
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static String safeDownloadName(KnowledgeDocument document) {
        String title = document.getTitle() == null || document.getTitle().isBlank() ? "document" : document.getTitle();
        return title.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static Path resolvePath(String value) {
        Path path = Path.of(value);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return Path.of(System.getProperty("user.dir")).resolve(path).toAbsolutePath().normalize();
    }
}
