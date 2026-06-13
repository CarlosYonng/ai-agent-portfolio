package com.yonng.agent.api;

import com.yonng.agent.dto.ChatRequest;
import com.yonng.agent.dto.ChatMessageResponse;
import com.yonng.agent.dto.ChatResponse;
import com.yonng.agent.dto.ChatSessionResponse;
import com.yonng.agent.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 聊天问答接口。
 *
 * <p>当前使用普通 JSON 返回，便于统一错误处理和历史入库。</p>
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
     * 查询用户历史会话。传 kbId 时只返回该知识库下的 RAG 会话。
     */
    @GetMapping("/sessions")
    public List<ChatSessionResponse> listSessions(@RequestParam Long tenantId,
                                                   @RequestParam Long userId,
                                                   @RequestParam(required = false) Long kbId) {
        return chatService.listSessions(tenantId, userId, kbId);
    }

    /**
     * 查询某个会话的消息明细，用于前端回看历史问答。
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public List<ChatMessageResponse> listMessages(@RequestParam Long tenantId,
                                                  @PathVariable Long sessionId) {
        return chatService.listMessages(tenantId, sessionId);
    }

}
