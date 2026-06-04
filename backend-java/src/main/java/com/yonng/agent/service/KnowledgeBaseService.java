package com.yonng.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonng.agent.domain.KnowledgeBase;
import com.yonng.agent.domain.KnowledgeDocument;
import com.yonng.agent.dto.DocumentCreateRequest;
import com.yonng.agent.dto.KnowledgeBaseRequest;
import com.yonng.agent.mapper.KnowledgeBaseMapper;
import com.yonng.agent.mapper.KnowledgeDocumentMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 知识库服务。
 *
 * <p>使用 MyBatis Plus Mapper 操作知识库和文档元数据。</p>
 */
@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    public KnowledgeBaseService(KnowledgeBaseMapper knowledgeBaseMapper, KnowledgeDocumentMapper knowledgeDocumentMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
    }

    /**
     * 创建知识库，并返回新生成的主键。
     */
    public Long createKnowledgeBase(KnowledgeBaseRequest request) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setTenantId(request.getTenantId());
        knowledgeBase.setName(request.getName());
        knowledgeBase.setDescription(request.getDescription());
        knowledgeBase.setVisibility(request.getVisibility());
        knowledgeBaseMapper.insert(knowledgeBase);
        return Objects.requireNonNull(knowledgeBase.getId());
    }

    /**
     * 查询租户下的知识库列表。
     */
    public List<KnowledgeBase> listKnowledgeBases(Long tenantId) {
        return knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getTenantId, tenantId)
                .orderByDesc(KnowledgeBase::getId));
    }

    /**
     * 登记文档元数据。实际解析和入向量库由 Python 脚本或 AI 服务处理。
     */
    public Long createDocument(DocumentCreateRequest request) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setTenantId(request.getTenantId());
        document.setKbId(request.getKbId());
        document.setTitle(request.getTitle());
        document.setSourceType(request.getSourceType());
        document.setSourceUri(request.getSourceUri());
        document.setStatus("PENDING");
        knowledgeDocumentMapper.insert(document);
        return Objects.requireNonNull(document.getId());
    }

    /**
     * 查询文档列表，返回 Map 是为了先保持接口灵活，后续可增加 Document 领域对象。
     */
    public List<Map<String, Object>> listDocuments(Long tenantId, Long kbId) {
        return knowledgeDocumentMapper.selectMaps(new LambdaQueryWrapper<KnowledgeDocument>()
                .select(KnowledgeDocument::getId,
                        KnowledgeDocument::getTitle,
                        KnowledgeDocument::getSourceType,
                        KnowledgeDocument::getSourceUri,
                        KnowledgeDocument::getStatus,
                        KnowledgeDocument::getVersion,
                        KnowledgeDocument::getCreatedAt)
                .eq(KnowledgeDocument::getTenantId, tenantId)
                .eq(KnowledgeDocument::getKbId, kbId)
                .orderByDesc(KnowledgeDocument::getId));
    }
}
