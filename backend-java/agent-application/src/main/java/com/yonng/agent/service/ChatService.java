package com.yonng.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonng.agent.domain.ChatMessage;
import com.yonng.agent.domain.ChatSession;
import com.yonng.agent.dto.ChatRequest;
import com.yonng.agent.dto.ChatMessageResponse;
import com.yonng.agent.dto.ChatResponse;
import com.yonng.agent.dto.ChatSessionResponse;
import com.yonng.agent.mapper.ChatMessageMapper;
import com.yonng.agent.mapper.ChatSessionMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 会话服务。
 *
 * <p>这里负责保存用户问题，并调用 Python AI 服务。AI 服务返回后，再保存助手答案。</p>
 */
@Service
public class ChatService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final RestClient aiRestClient;
    private final ObjectMapper objectMapper;
    private final HistorySchemaService historySchemaService;

    public ChatService(ChatSessionMapper chatSessionMapper,
                       ChatMessageMapper chatMessageMapper,
                       RestClient aiRestClient,
                       ObjectMapper objectMapper,
                       HistorySchemaService historySchemaService) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.aiRestClient = aiRestClient;
        this.objectMapper = objectMapper;
        this.historySchemaService = historySchemaService;
    }

    /**
     * 发送问题到 Agent。第一版不是流式返回，方便先把端到端链路打通。
     */
    public ChatResponse ask(ChatRequest request) {
        Long sessionId = ensureSession(request);
        Long messageId = insertMessage(request.getTenantId(), sessionId, "user", request.getQuestion(), null);

        Map<String, Object> aiRequest = new HashMap<>();
        aiRequest.put("tenant_id", request.getTenantId());
        aiRequest.put("user_id", request.getUserId());
        aiRequest.put("session_id", sessionId);
        aiRequest.put("message_id", messageId);
        aiRequest.put("kb_id", request.getKbId());
        aiRequest.put("question", request.getQuestion());

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
            insertMessage(request.getTenantId(), sessionId, "assistant", safeResponse.getAnswer(), safeResponse.getTraceId());
            return safeResponse;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化 AI 请求失败", e);
        }
    }

    /**
     * 查询用户在指定知识库下的历史会话。
     */
    public List<ChatSessionResponse> listSessions(Long tenantId, Long userId, Long kbId) {
        historySchemaService.ensureSchema();
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<ChatSession>()
                .select(ChatSession::getId,
                        ChatSession::getTenantId,
                        ChatSession::getUserId,
                        ChatSession::getKbId,
                        ChatSession::getTitle,
                        ChatSession::getCreatedAt,
                        ChatSession::getUpdatedAt)
                .eq(ChatSession::getTenantId, tenantId)
                .eq(ChatSession::getUserId, userId)
                .orderByDesc(ChatSession::getUpdatedAt);
        if (kbId != null) {
            wrapper.eq(ChatSession::getKbId, kbId);
        }
        try {
            return chatSessionMapper.selectList(wrapper)
                    .stream()
                    .map(ChatSessionResponse::from)
                    .toList();
        } catch (RuntimeException error) {
            return listSessionsWithoutKbId(tenantId, userId);
        }
    }

    /**
     * 查询某个会话下的问答消息，用于前端回看历史答案和 Trace。
     */
    public List<ChatMessageResponse> listMessages(Long tenantId, Long sessionId) {
        try {
            return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                            .select(ChatMessage::getId,
                                    ChatMessage::getTenantId,
                                    ChatMessage::getSessionId,
                                    ChatMessage::getRole,
                                    ChatMessage::getContent,
                                    ChatMessage::getTraceId,
                                    ChatMessage::getCreatedAt)
                            .eq(ChatMessage::getTenantId, tenantId)
                            .eq(ChatMessage::getSessionId, sessionId)
                            .orderByAsc(ChatMessage::getId))
                    .stream()
                    .map(ChatMessageResponse::from)
                    .toList();
        } catch (RuntimeException error) {
            return List.of();
        }
    }

    private List<ChatSessionResponse> listSessionsWithoutKbId(Long tenantId, Long userId) {
        try {
            return chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                            .select(ChatSession::getId,
                                    ChatSession::getTenantId,
                                    ChatSession::getUserId,
                                    ChatSession::getTitle,
                                    ChatSession::getCreatedAt,
                                    ChatSession::getUpdatedAt)
                            .eq(ChatSession::getTenantId, tenantId)
                            .eq(ChatSession::getUserId, userId)
                            .orderByDesc(ChatSession::getUpdatedAt))
                    .stream()
                    .map(ChatSessionResponse::from)
                    .toList();
        } catch (RuntimeException error) {
            return List.of();
        }
    }

    private Long ensureSession(ChatRequest request) {
        if (request.getSessionId() != null) {
            return request.getSessionId();
        }
        historySchemaService.ensureSchema();
        ChatSession session = new ChatSession();
        session.setTenantId(request.getTenantId());
        session.setUserId(request.getUserId());
        session.setKbId(request.getKbId());
        session.setTitle(titleFromQuestion(request.getQuestion()));
        chatSessionMapper.insert(session);
        return Objects.requireNonNull(session.getId());
    }

    private Long insertMessage(Long tenantId, Long sessionId, String role, String content, String traceId) {
        ChatMessage message = new ChatMessage();
        // chat_message 是审计和多轮上下文的事实表，user/assistant 两类消息都统一写入。
        message.setTenantId(tenantId);
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        message.setTraceId(traceId);
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
