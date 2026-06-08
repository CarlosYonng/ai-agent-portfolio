package com.yonng.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yonng.agent.domain.ChatMessage;
import com.yonng.agent.domain.ChatSession;
import com.yonng.agent.dto.ChatRequest;
import com.yonng.agent.dto.ChatResponse;
import com.yonng.agent.dto.ChatStreamEvent;
import com.yonng.agent.mapper.ChatMessageMapper;
import com.yonng.agent.mapper.ChatSessionMapper;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
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

    public ChatService(ChatSessionMapper chatSessionMapper, ChatMessageMapper chatMessageMapper, RestClient aiRestClient, ObjectMapper objectMapper) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.aiRestClient = aiRestClient;
        this.objectMapper = objectMapper;
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
     * 将问答结果转换成 SSE 事件流。
     *
     * <p>当前 Python AI 服务仍返回完整 JSON，因此这里先把完整答案切成 delta 事件；
     * 后续下游支持 token 流时，只需要替换 AI 调用部分。</p>
     */
    public Flux<ServerSentEvent<ChatStreamEvent>> askStream(ChatRequest request) {
        return Mono.fromCallable(() -> ask(request))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(this::toStreamEvents)
                .onErrorResume(error -> Flux.just(sse("error", ChatStreamEvent.error(error.getMessage()))));
    }

    private Flux<ServerSentEvent<ChatStreamEvent>> toStreamEvents(ChatResponse response) {
        Long sessionId = response.getSessionId();
        String traceId = response.getTraceId();
        String answer = response.getAnswer() == null ? "" : response.getAnswer();

        Flux<ServerSentEvent<ChatStreamEvent>> metadata = Flux.just(
                sse("metadata", ChatStreamEvent.metadata(sessionId, traceId))
        );
        Flux<ServerSentEvent<ChatStreamEvent>> deltas = Flux.fromIterable(splitAnswer(answer, 24))
                .delayElements(Duration.ofMillis(25))
                .map(chunk -> sse("delta", ChatStreamEvent.delta(sessionId, traceId, chunk)));
        Flux<ServerSentEvent<ChatStreamEvent>> citations = Flux.just(
                sse("citations", ChatStreamEvent.citations(sessionId, traceId, response.getCitations()))
        );
        Flux<ServerSentEvent<ChatStreamEvent>> done = Flux.just(
                sse("done", ChatStreamEvent.done(sessionId, traceId, answer))
        );

        return Flux.concat(metadata, deltas, citations, done);
    }

    /**
     * 把完整答案拆成 SSE delta 片段。
     */
    static List<String> splitAnswer(String answer, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        if (answer == null || answer.isEmpty()) {
            chunks.add("");
            return chunks;
        }
        int offset = 0;
        while (offset < answer.length()) {
            int next = Math.min(answer.length(), offset + chunkSize);
            chunks.add(answer.substring(offset, next));
            offset = next;
        }
        return chunks;
    }

    private static ServerSentEvent<ChatStreamEvent> sse(String eventName, ChatStreamEvent data) {
        return ServerSentEvent.builder(data)
                .event(eventName)
                .build();
    }

    private Long ensureSession(ChatRequest request) {
        if (request.getSessionId() != null) {
            return request.getSessionId();
        }
        ChatSession session = new ChatSession();
        session.setTenantId(request.getTenantId());
        session.setUserId(request.getUserId());
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
