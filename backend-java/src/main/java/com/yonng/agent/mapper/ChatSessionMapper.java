package com.yonng.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yonng.agent.domain.ChatSession;

/**
 * 聊天会话 Mapper。
 *
 * <p>会话用于承载多轮问答，消息明细保存在 chat_message。</p>
 */
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
