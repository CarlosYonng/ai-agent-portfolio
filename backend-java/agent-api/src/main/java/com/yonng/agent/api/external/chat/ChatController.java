package com.yonng.agent.api.external.chat;

import com.yonng.agent.api.config.CurrentUser;
import com.yonng.agent.api.config.RoleAccess;
import com.yonng.agent.dto.chat.ChatRequest;
import com.yonng.agent.dto.chat.ChatMessageResponse;
import com.yonng.agent.dto.chat.ChatResponse;
import com.yonng.agent.dto.chat.ChatSessionResponse;
import com.yonng.agent.dto.system.ApiResult;
import com.yonng.agent.dto.user.UserInfo;
import com.yonng.agent.service.chat.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 外部聊天问答接口。
 *
 * <p>调用方：浏览器 RAG 控制台。Java 负责鉴权、会话历史和普通 JSON 问答，
 * 真正的 Agent 推理通过 ChatService 转发给 Python AI 服务。</p>
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/messages")
    public ApiResult<ChatResponse> ask(@Valid @RequestBody ChatRequest request,
                                        @CurrentUser UserInfo user) {
        return ApiResult.ok(chatService.ask(request, user.getCustomerId(), user.getUserId()));
    }

    /**
     * 查询用户历史会话。传 kbId 时只返回该知识库下的 RAG 会话。
     * 管理员可跨租户查看，通过 filterCustomerId 指定租户。
     */
    @GetMapping("/sessions")
    public ApiResult<List<ChatSessionResponse>> listSessions(@CurrentUser UserInfo user,
                                                              @RequestParam(required = false) Long kbId,
                                                              @RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) String startDate,
                                                              @RequestParam(required = false) String endDate,
                                                              @RequestParam(required = false) Long filterCustomerId) {
        Long customerId = RoleAccess.customerScope(user, filterCustomerId);
        Long userId = RoleAccess.userScope(user);
        return ApiResult.ok(chatService.listSessions(customerId, userId, kbId, keyword, startDate, endDate));
    }

    /**
     * 查询某个会话的消息明细，用于前端回看历史问答。
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResult<List<ChatMessageResponse>> listMessages(@CurrentUser UserInfo user,
                                                              @PathVariable Long sessionId) {
        return ApiResult.ok(chatService.listMessages(RoleAccess.customerScope(user), RoleAccess.userScope(user), sessionId));
    }

}
