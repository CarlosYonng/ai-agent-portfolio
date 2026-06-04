package com.yonng.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yonng.agent.domain.KnowledgeBase;

/**
 * 知识库空间 Mapper。
 *
 * <p>kb_space 是租户级知识库入口，文档和 chunk 会挂在它下面。</p>
 */
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {
}
