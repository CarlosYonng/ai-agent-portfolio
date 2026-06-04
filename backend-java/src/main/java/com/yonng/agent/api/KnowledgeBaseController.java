package com.yonng.agent.api;

import com.yonng.agent.domain.KnowledgeBase;
import com.yonng.agent.dto.DocumentCreateRequest;
import com.yonng.agent.dto.KnowledgeBaseRequest;
import com.yonng.agent.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    /**
     * 创建租户级知识库空间。
     */
    @PostMapping
    public Map<String, Object> createKnowledgeBase(@Valid @RequestBody KnowledgeBaseRequest request) {
        Long id = knowledgeBaseService.createKnowledgeBase(request);
        return Map.of("id", id);
    }

    /**
     * 查看租户下已创建的知识库，方便导入脚本执行后核对数据。
     */
    @GetMapping
    public List<KnowledgeBase> listKnowledgeBases(@RequestParam Long tenantId) {
        return knowledgeBaseService.listKnowledgeBases(tenantId);
    }

    /**
     * 登记文档元数据；正文切片和索引由导入脚本负责。
     */
    @PostMapping("/documents")
    public Map<String, Object> createDocument(@Valid @RequestBody DocumentCreateRequest request) {
        Long id = knowledgeBaseService.createDocument(request);
        return Map.of("id", id);
    }

    /**
     * 返回知识库下的文档元数据列表。
     */
    @GetMapping("/{kbId}/documents")
    public List<Map<String, Object>> listDocuments(@RequestParam Long tenantId, @PathVariable Long kbId) {
        return knowledgeBaseService.listDocuments(tenantId, kbId);
    }
}
