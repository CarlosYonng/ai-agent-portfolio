package com.yonng.agent.dto;

import java.util.List;
import java.util.Map;

/**
 * SSE 聊天事件。
 *
 * <p>type 用于前端区分元数据、答案片段、引用证据和结束事件。</p>
 */
public class ChatStreamEvent {

    private String type;
    private Long sessionId;
    private String traceId;
    private String content;
    private List<Map<String, Object>> citations;

    public static ChatStreamEvent metadata(Long sessionId, String traceId) {
        ChatStreamEvent event = new ChatStreamEvent();
        event.setType("metadata");
        event.setSessionId(sessionId);
        event.setTraceId(traceId);
        return event;
    }

    public static ChatStreamEvent delta(Long sessionId, String traceId, String content) {
        ChatStreamEvent event = new ChatStreamEvent();
        event.setType("delta");
        event.setSessionId(sessionId);
        event.setTraceId(traceId);
        event.setContent(content);
        return event;
    }

    public static ChatStreamEvent citations(Long sessionId, String traceId, List<Map<String, Object>> citations) {
        ChatStreamEvent event = new ChatStreamEvent();
        event.setType("citations");
        event.setSessionId(sessionId);
        event.setTraceId(traceId);
        event.setCitations(citations);
        return event;
    }

    public static ChatStreamEvent done(Long sessionId, String traceId, String content) {
        ChatStreamEvent event = new ChatStreamEvent();
        event.setType("done");
        event.setSessionId(sessionId);
        event.setTraceId(traceId);
        event.setContent(content);
        return event;
    }

    public static ChatStreamEvent error(String message) {
        ChatStreamEvent event = new ChatStreamEvent();
        event.setType("error");
        event.setContent(message);
        return event;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<Map<String, Object>> getCitations() {
        return citations;
    }

    public void setCitations(List<Map<String, Object>> citations) {
        this.citations = citations;
    }
}
