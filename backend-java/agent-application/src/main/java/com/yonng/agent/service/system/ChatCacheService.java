package com.yonng.agent.service.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonng.agent.domain.chat.ChatMessage;
import com.yonng.agent.mapper.chat.ChatMessageMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话历史缓存服务。
 *
 * <p>多轮对话时，历史消息从 Redis 读取，减少 MySQL 压力。
 * 失效策略：会话最后活动时间 + 24 小时。</p>
 */
@Service
public class ChatCacheService {

    private static final String HISTORY_PREFIX = "chat:session:";
    private static final long CACHE_TTL = 86400; // 24 小时

    private final RedisCacheService cacheService;
    private final ChatMessageMapper chatMessageMapper;

    public ChatCacheService(RedisCacheService cacheService, ChatMessageMapper chatMessageMapper) {
        this.cacheService = cacheService;
        this.chatMessageMapper = chatMessageMapper;
    }

    /** 获取会话历史（优先 Redis，回源 MySQL） */
    @SuppressWarnings("unchecked")
    public List<Map<String, String>> getHistory(Long sessionId) {
        if (sessionId == null) return List.of();
        String key = HISTORY_PREFIX + sessionId;

        // 1. 尝试从 Redis 读取
        List<Map<String, String>> cached = cacheService.get(key, List.class);
        if (cached != null) return cached;

        // 2. 回源 MySQL
        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getId));

        List<Map<String, String>> history = new ArrayList<>();
        for (ChatMessage msg : messages) {
            Map<String, String> entry = new HashMap<>();
            entry.put("role", "user".equals(msg.getRole()) ? "user" : "assistant");
            entry.put("content", msg.getContent());
            history.add(entry);
        }

        // 3. 写入 Redis
        if (!history.isEmpty()) {
            cacheService.set(key, history, CACHE_TTL);
        }
        return history;
    }

    /** 删除会话缓存（数据更新后使缓存失效，下次读取回源 MySQL） */
    public void evictSession(Long sessionId) {
        if (sessionId == null) return;
        cacheService.delete(HISTORY_PREFIX + sessionId);
    }
}
