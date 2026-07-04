package com.yonng.agent.api.internal.knowledge;

import com.yonng.agent.dto.knowledge.DocumentStatusUpdateRequest;
import com.yonng.agent.dto.system.ApiStatus;
import com.yonng.agent.service.knowledge.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部知识库文档状态接口。
 *
 * <p>调用方：后端受控脚本或内部服务。用于把离线入库、重试、补偿任务的处理结果回写到
 * MySQL 元数据；浏览器管理台应使用外部知识库接口查询状态，不直接依赖该内部路径。</p>
 */
@RestController
@RequestMapping("/api/internal/kb/documents")
public class KnowledgeDocumentInternalController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeDocumentInternalController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /**
     * 回写文档处理状态（PENDING / INDEXED / FAILED）。
     */
    @PatchMapping("/{id}/status")
    public ApiStatus updateDocumentStatus(@PathVariable Long id,
                                          @Valid @RequestBody DocumentStatusUpdateRequest request) {
        knowledgeBaseService.updateDocumentStatus(id, request.getStatus());
        return ApiStatus.ok();
    }
}
