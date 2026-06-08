package com.yonng.agent.api;

import com.yonng.agent.dto.DocumentCreateRequest;
import com.yonng.agent.dto.DocumentStatusUpdateRequest;
import com.yonng.agent.dto.DocumentUpdateRequest;
import com.yonng.agent.dto.IdResponse;
import com.yonng.agent.dto.KnowledgeBaseResponse;
import com.yonng.agent.dto.KnowledgeBaseRequest;
import com.yonng.agent.dto.KnowledgeBaseUpdateRequest;
import com.yonng.agent.dto.KnowledgeDocumentResponse;
import com.yonng.agent.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库管理接口。
 *
 * <p>这些接口支撑项目 A 的管理后台，也便于脚本导入数据后检查结果。</p>
 */
@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    // ==================== 知识库 CRUD ====================

    /**
     * 创建租户级知识库空间。
     */
    @PostMapping
    public IdResponse createKnowledgeBase(@Valid @RequestBody KnowledgeBaseRequest request) {
        return new IdResponse(knowledgeBaseService.createKnowledgeBase(request));
    }

    /**
     * 查看租户下已创建的知识库，方便导入脚本执行后核对数据。
     */
    @GetMapping
    public List<KnowledgeBaseResponse> listKnowledgeBases(@RequestParam Long tenantId) {
        return knowledgeBaseService.listKnowledgeBases(tenantId);
    }

    /**
     * 查询单个知识库详情，供管理台切换空间时回填编辑表单。
     */
    @GetMapping("/{id}")
    public KnowledgeBaseResponse getKnowledgeBase(@PathVariable Long id) {
        return knowledgeBaseService.getKnowledgeBase(id);
    }

    /**
     * 更新知识库信息（名称、描述、可见性），不触发索引重建。
     */
    @PutMapping("/{id}")
    public void updateKnowledgeBase(@PathVariable Long id,
                                    @Valid @RequestBody KnowledgeBaseUpdateRequest request) {
        knowledgeBaseService.updateKnowledgeBase(id, request);
    }

    /**
     * 删除知识库及其下所有文档元数据，外部向量和图谱索引由脚本兜底清理。
     */
    @DeleteMapping("/{id}")
    public void deleteKnowledgeBase(@PathVariable Long id) {
        knowledgeBaseService.deleteKnowledgeBase(id);
    }

    // ==================== 文档 CRUD ====================

    /**
     * 登记文档元数据；正文切片和索引由导入脚本负责。
     */
    @PostMapping("/documents")
    public IdResponse createDocument(@Valid @RequestBody DocumentCreateRequest request) {
        return new IdResponse(knowledgeBaseService.createDocument(request));
    }

    /**
     * 返回知识库下的文档元数据列表。
     */
    @GetMapping("/{kbId}/documents")
    public List<KnowledgeDocumentResponse> listDocuments(@RequestParam Long tenantId, @PathVariable Long kbId) {
        return knowledgeBaseService.listDocuments(tenantId, kbId);
    }

    /**
     * 查询单个文档详情，供管理台回填来源信息。
     */
    @GetMapping("/documents/{id}")
    public KnowledgeDocumentResponse getDocument(@PathVariable Long id) {
        return knowledgeBaseService.getDocument(id);
    }

    /**
     * 更新文档元数据（标题、来源类型、来源地址），正文切片仍由导入链路负责。
     */
    @PutMapping("/documents/{id}")
    public void updateDocument(@PathVariable Long id,
                               @Valid @RequestBody DocumentUpdateRequest request) {
        knowledgeBaseService.updateDocument(id, request);
    }

    /**
     * 删除文档。注意：Qdrant 向量和 Neo4j 图谱的清理需要额外脚本处理。
     */
    @DeleteMapping("/documents/{id}")
    public void deleteDocument(@PathVariable Long id) {
        knowledgeBaseService.deleteDocument(id);
    }

    /**
     * 更新文档处理状态（PENDING / INDEXED / FAILED）。
     */
    @PatchMapping("/documents/{id}/status")
    public void updateDocumentStatus(@PathVariable Long id,
                                     @Valid @RequestBody DocumentStatusUpdateRequest request) {
        knowledgeBaseService.updateDocumentStatus(id, request.getStatus());
    }
}
