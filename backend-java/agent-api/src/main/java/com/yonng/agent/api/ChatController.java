package com.yonng.agent.api;

import com.yonng.agent.dto.ChatRequest;
import com.yonng.agent.dto.ChatResponse;
import com.yonng.agent.dto.ChatStreamEvent;
import com.yonng.agent.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 聊天问答接口。
 *
 * <p>第一版使用普通 JSON 返回，后续可以增加 SSE 流式返回接口。</p>
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 接收用户问题，创建/复用会话后转发给 AI 服务。
     */
    @PostMapping("/messages")
    public ChatResponse ask(@Valid @RequestBody ChatRequest request) {
        return chatService.ask(request);
    }

    /**
     * SSE 流式接口，供浏览器聊天页面逐段渲染答案。
     */
    @PostMapping(value = "/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEvent>> askStream(@Valid @RequestBody ChatRequest request) {
        return chatService.askStream(request);
    }
}
