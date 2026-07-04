package com.yonng.agent.api.external.knowledge;

import com.yonng.agent.api.config.CurrentUser;
import com.yonng.agent.api.config.RoleAccess;
import com.yonng.agent.dto.knowledge.DocumentCreateRequest;
import com.yonng.agent.dto.knowledge.DocumentUpdateRequest;
import com.yonng.agent.dto.knowledge.DocumentDownloadInfo;
import com.yonng.agent.dto.system.ApiResult;
import com.yonng.agent.dto.system.ApiStatus;
import com.yonng.agent.dto.knowledge.KnowledgeBaseResponse;
import com.yonng.agent.dto.knowledge.KnowledgeBaseRequest;
import com.yonng.agent.dto.knowledge.KnowledgeBaseUpdateRequest;
import com.yonng.agent.dto.knowledge.KnowledgeDocumentResponse;
import com.yonng.agent.dto.user.UserInfo;
import com.yonng.agent.service.knowledge.DocumentIngestionService;
import com.yonng.agent.service.knowledge.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;

import java.util.List;

/**
 * 外部知识库管理接口。
 *
 * <p>调用方：浏览器知识库管理台。这里保留用户可见的知识库和文档操作；
 * 脚本/服务内部状态回写应优先走 {@code /api/internal/**} 下的内部接口。</p>
 */
@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentIngestionService documentIngestionService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService,
                                   DocumentIngestionService documentIngestionService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.documentIngestionService = documentIngestionService;
    }

    // ==================== 知识库 CRUD ====================

    /**
     * 创建知识库。
     *
     * <p>仅管理员角色可创建。平台管理员创建的 KB 归属平台客户，
     * 客户管理员创建的 KB 归属所属客户，普通用户无权创建。</p>
     */
    @PostMapping
    public ApiStatus createKnowledgeBase(@Valid @RequestBody KnowledgeBaseRequest request,
                                                @CurrentUser UserInfo user) {
        knowledgeBaseService.createKnowledgeBase(request, user.getCustomerId(), user.getRole());
        return ApiStatus.ok();
    }

    /**
     * 查看租户下已创建的知识库，方便导入脚本执行后核对数据。
     */
    @GetMapping
    public ApiResult<List<KnowledgeBaseResponse>> listKnowledgeBases(@CurrentUser UserInfo user) {
        return ApiResult.ok(knowledgeBaseService.listKnowledgeBases(RoleAccess.customerScope(user)));
    }

    /**
     * 查询单个知识库详情，供管理台切换空间时回填编辑表单。
     */
    @GetMapping("/{id}")
    public ApiResult<KnowledgeBaseResponse> getKnowledgeBase(@PathVariable Long id) {
        return ApiResult.ok(knowledgeBaseService.getKnowledgeBase(id));
    }

    /**
     * 更新知识库信息（名称、描述、可见性），不触发索引重建。
     */
    @PutMapping("/{id}")
    public ApiStatus updateKnowledgeBase(@PathVariable Long id,
                                                @Valid @RequestBody KnowledgeBaseUpdateRequest request) {
        knowledgeBaseService.updateKnowledgeBase(id, request);
        return ApiStatus.ok();
    }

    /**
     * 删除知识库及其下所有文档，并同步清理 Qdrant 向量和 Neo4j 图谱索引。
     */
    @DeleteMapping("/{id}")
    public ApiStatus deleteKnowledgeBase(@PathVariable Long id) {
        knowledgeBaseService.deleteKnowledgeBase(id);
        return ApiStatus.ok();
    }

    // ==================== 文档 CRUD ====================

    /**
     * 登记文档元数据；正文切片和索引由导入脚本负责。
     */
    @PostMapping("/documents")
    public ApiStatus createDocument(@Valid @RequestBody DocumentCreateRequest request,
                                           @CurrentUser UserInfo user) {
        knowledgeBaseService.createDocument(request, user.getCustomerId());
        return ApiStatus.ok();
    }

    /**
     * 上传 Markdown/TXT 文档并自动触发入库，返回脚本执行后的最新文档状态。
     */
    @PostMapping(value = "/{kbId}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<KnowledgeDocumentResponse> uploadDocument(@PathVariable Long kbId,
                                                                @RequestParam(value = "title", required = false) String title,
                                                                @RequestParam("file") MultipartFile file,
                                                                @CurrentUser UserInfo user) throws IOException {
        return ApiResult.ok(documentIngestionService.uploadAndIngest(
                kbId,
                user.getCustomerId(),
                title,
                file.getOriginalFilename(),
                file.getInputStream()));
    }

    /**
     * 返回知识库下的文档元数据列表。
     */
    @GetMapping("/{kbId}/documents")
    public ApiResult<List<KnowledgeDocumentResponse>> listDocuments(@CurrentUser UserInfo user,
                                                                     @PathVariable Long kbId) {
        return ApiResult.ok(knowledgeBaseService.listDocuments(RoleAccess.customerScope(user), kbId));
    }

    /**
     * 查询单个文档详情，供管理台回填来源信息。
     */
    @GetMapping("/documents/{id}")
    public ApiResult<KnowledgeDocumentResponse> getDocument(@PathVariable Long id) {
        return ApiResult.ok(knowledgeBaseService.getDocument(id));
    }

    /**
     * 复用已上传源文件重新执行切片、向量和图谱写入，主要用于 FAILED 文档自助恢复。
     */
    @PostMapping("/documents/{id}/retry")
    public ApiResult<KnowledgeDocumentResponse> retryDocumentIngest(@PathVariable Long id,
                                                                     @CurrentUser UserInfo user) {
        return ApiResult.ok(documentIngestionService.retryIngest(id, RoleAccess.customerScope(user)));
    }

    /**
     * 下载文档上传时保留的原文件。
     *
     * <p>只返回后端受控上传目录中的文件，不暴露服务器绝对路径。</p>
     */
    @GetMapping("/documents/{id}/download")
    @SuppressWarnings("resource") // InputStreamResource 由 Spring MVC 在响应完成后关闭
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id,
                                                     @CurrentUser UserInfo user) throws IOException {
        DocumentDownloadInfo download = knowledgeBaseService.getDocumentDownloadInfo(id, RoleAccess.customerScope(user));
        InputStreamResource resource = new InputStreamResource(Files.newInputStream(download.path()));
        return ResponseEntity.ok()
                .contentLength(Files.size(download.path()))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(download.filename(), java.nio.charset.StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(resource);
    }

    /**
     * 更新文档元数据（标题、来源类型、来源地址），正文切片仍由导入链路负责。
     */
    @PutMapping("/documents/{id}")
    public ApiStatus updateDocument(@PathVariable Long id,
                                           @Valid @RequestBody DocumentUpdateRequest request) {
        knowledgeBaseService.updateDocument(id, request);
        return ApiStatus.ok();
    }

    /**
     * 删除文档，并同步清理 Qdrant 向量和 Neo4j 图谱索引。
     */
    @DeleteMapping("/documents/{id}")
    public ApiStatus deleteDocument(@PathVariable Long id) {
        knowledgeBaseService.deleteDocument(id);
        return ApiStatus.ok();
    }

}
