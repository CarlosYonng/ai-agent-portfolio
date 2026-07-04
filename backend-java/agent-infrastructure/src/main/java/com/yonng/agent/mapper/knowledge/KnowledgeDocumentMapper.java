package com.yonng.agent.mapper.knowledge;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yonng.agent.domain.knowledge.KnowledgeDocument;

/**
 * 知识库文档 Mapper。
 *
 * <p>这里仅管理文档元数据；切片、向量和图谱写入由导入脚本处理。</p>
 */
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {
}
