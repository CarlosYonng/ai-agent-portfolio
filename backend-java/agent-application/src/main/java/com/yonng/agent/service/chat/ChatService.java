package com.yonng.agent.service.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonng.agent.domain.chat.ChatMessage;
import com.yonng.agent.domain.chat.ChatSession;
import com.yonng.agent.dto.chat.ChatRequest;
import com.yonng.agent.dto.chat.ChatMessageResponse;
import com.yonng.agent.dto.chat.ChatResponse;
import com.yonng.agent.dto.chat.ChatSessionResponse;
import com.yonng.agent.exception.BusinessException;
import com.yonng.agent.exception.ErrorCode;
import com.yonng.agent.mapper.chat.ChatMessageMapper;
import com.yonng.agent.mapper.chat.ChatSessionMapper;
import com.yonng.agent.service.system.ChatCacheService;
import com.yonng.agent.service.system.ContentCheckService;
import com.yonng.agent.service.system.HistorySchemaService;
import com.yonng.agent.service.system.RequestDedupService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 会话服务。
 *
 * <p>保存用户问题并调用 Python AI 服务，AI 返回后保存助手答案。
 * 对话历史通过 Redis 缓存加速（ChatCacheService），相同问题 5 秒内自动去重。</p>
 */
@Service
public class ChatService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final RestClient aiRestClient;
    private final ObjectMapper objectMapper;
    private final HistorySchemaService historySchemaService;
    private final ContentCheckService contentCheckService;
    private final ChatCacheService chatCacheService;
    private final RequestDedupService requestDedupService;

    public ChatService(ChatSessionMapper chatSessionMapper,
                       ChatMessageMapper chatMessageMapper,
                       RestClient aiRestClient,
                       ObjectMapper objectMapper,
                       HistorySchemaService historySchemaService,
                       ContentCheckService contentCheckService,
                       ChatCacheService chatCacheService,
                       RequestDedupService requestDedupService) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.aiRestClient = aiRestClient;
        this.objectMapper = objectMapper;
        this.historySchemaService = historySchemaService;
        this.contentCheckService = contentCheckService;
        this.chatCacheService = chatCacheService;
        this.requestDedupService = requestDedupService;
    }

    /**
     * 发送问题到 Agent。支持多轮对话：自动读取历史消息传递给 AI 服务。
     * 对话历史通过 Redis 缓存加速，相同问题 5 秒内自动去重。
     */
    public ChatResponse ask(ChatRequest request, Long customerId, Long userId) {
        // 内容安全审核
        contentCheckService.check(request.getQuestion(), "RAG 问答");

        // Redis 请求去重：同一用户 5 秒内提交相同问题直接拦截
        if (requestDedupService.isDuplicate(userId, request.getQuestion())) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "请勿重复提交相同问题");
        }

        Long sessionId = ensureSession(request, customerId, userId);

        // 读取历史消息（优先 Redis 缓存，缓存 miss 时回源 MySQL）
        List<Map<String, String>> messageHistory = new ArrayList<>();
        if (request.getSessionId() != null) {
            messageHistory = chatCacheService.getHistory(sessionId);
        }

        Long messageId = insertMessage(customerId, sessionId, "user", request.getQuestion(), null, null);

        Map<String, Object> aiRequest = new HashMap<>();
        aiRequest.put("customer_id", customerId);
        aiRequest.put("user_id", userId);
        aiRequest.put("session_id", sessionId);
        aiRequest.put("message_id", messageId);
        aiRequest.put("kb_id", request.getKbId());
        aiRequest.put("question", request.getQuestion());
        aiRequest.put("message_history", messageHistory);

        try {
            String jsonBody = objectMapper.writeValueAsString(aiRequest);
            ChatResponse response = aiRestClient.post()
                    .uri("/api/agent/ask")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .body(ChatResponse.class);

            ChatResponse safeResponse = Objects.requireNonNullElseGet(response, ChatResponse::new);
            safeResponse.setSessionId(sessionId);
            String citationsJson = null;
            if (safeResponse.getCitations() != null && !safeResponse.getCitations().isEmpty()) {
                citationsJson = objectMapper.writeValueAsString(safeResponse.getCitations());
            }
            insertMessage(customerId, sessionId, "assistant", safeResponse.getAnswer(), safeResponse.getTraceId(), citationsJson);
            // 写入完成后刷新 Redis 缓存，下次读取时直接从缓存命中
            chatCacheService.getHistory(sessionId);
            return safeResponse;
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_REQUEST_SERIALIZATION_FAILED, "序列化 AI 请求失败");
        }
    }

    /**
     * 查询会话历史。支持按租户、用户、知识库、关键字、时间范围筛选。
     *
     * <p>customerId/userId 为空时不参与过滤；管理员默认可查看全部客户、全部用户的会话。</p>
     */
    public List<ChatSessionResponse> listSessions(Long customerId, Long userId, Long kbId,
                                                  String keyword, String startDate, String endDate) {
        historySchemaService.ensureSchema();
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<ChatSession>()
                .select(ChatSession::getId,
                        ChatSession::getCustomerId,
                        ChatSession::getUserId,
                        ChatSession::getKbId,
                        ChatSession::getTitle,
                        ChatSession::getCreatedAt,
                        ChatSession::getUpdatedAt)
                .orderByDesc(ChatSession::getUpdatedAt);
        if (customerId != null) {
            wrapper.eq(ChatSession::getCustomerId, customerId);
        }
        if (userId != null) {
            wrapper.eq(ChatSession::getUserId, userId);
        }
        if (kbId != null) {
            wrapper.eq(ChatSession::getKbId, kbId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(ChatSession::getTitle, keyword);
        }
        if (startDate != null && !startDate.isBlank()) {
            wrapper.ge(ChatSession::getUpdatedAt, startDate);
        }
        if (endDate != null && !endDate.isBlank()) {
            wrapper.le(ChatSession::getUpdatedAt, endDate + " 23:59:59");
        }
        return chatSessionMapper.selectList(wrapper)
                .stream()
                .map(ChatSessionResponse::from)
                .toList();
    }

    /**
     * 查询某个会话下的问答消息，用于前端回看历史答案和 Trace。
     */
    public List<ChatMessageResponse> listMessages(Long customerId, Long userId, Long sessionId) {
        ensureSessionVisible(customerId, userId, sessionId);
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
                        .select(ChatMessage::getId,
                                ChatMessage::getCustomerId,
                                ChatMessage::getSessionId,
                                ChatMessage::getRole,
                                ChatMessage::getContent,
                                ChatMessage::getTraceId,
                                ChatMessage::getCitations,
                                ChatMessage::getCreatedAt)
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getId);
        if (customerId != null) {
            wrapper.eq(ChatMessage::getCustomerId, customerId);
        }
        return chatMessageMapper.selectList(wrapper)
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    private void ensureSessionVisible(Long customerId, Long userId, Long sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.CHAT_SESSION_NOT_FOUND);
        }
        if (customerId != null && !customerId.equals(session.getCustomerId())) {
            throw new BusinessException(ErrorCode.CHAT_SESSION_NOT_FOUND);
        }
        if (userId != null && !userId.equals(session.getUserId())) {
            throw new BusinessException(ErrorCode.CHAT_SESSION_NOT_FOUND);
        }
    }

    private Long ensureSession(ChatRequest request, Long customerId, Long userId) {
        if (request.getSessionId() != null) {
            return request.getSessionId();
        }
        historySchemaService.ensureSchema();
        ChatSession session = new ChatSession();
        session.setCustomerId(customerId);
        session.setUserId(userId);
        session.setKbId(request.getKbId());
        session.setTitle(titleFromQuestion(request.getQuestion()));
        chatSessionMapper.insert(session);
        return Objects.requireNonNull(session.getId());
    }

    private Long insertMessage(Long customerId, Long sessionId, String role, String content, String traceId, String citations) {
        ChatMessage message = new ChatMessage();
        message.setCustomerId(customerId);
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        message.setTraceId(traceId);
        message.setCitations(citations);
        chatMessageMapper.insert(message);
        return Objects.requireNonNull(message.getId());
    }

    /**
     * 从首个问题生成会话标题，避免长问题直接撑开列表。
     */
    static String titleFromQuestion(String question) {
        if (question == null || question.isBlank()) {
            return "New Session";
        }
        // 会话标题只取前 30 个字符，避免长问题撑开会话列表 UI。
        return question.length() > 30 ? question.substring(0, 30) : question;
    }
}
