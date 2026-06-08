package com.yonng.agent.dto;

import java.util.List;

/**
 * SSE 聊天事件。
 *
 * <p>type 用于前端区分元数据、答案片段、引用证据和结束事件。</p>
 */
public class ChatStreamEvent {

    /** 事件类型：metadata/delta/citations/done/error。 */
    private String type;

    /** 当前聊天会话 ID。 */
    private Long sessionId;

    /** 本次 AI 调用链路 ID。 */
    private String traceId;

    /** delta 或 error/done 事件携带的文本内容。 */
    private String content;

    /** citations 事件携带的 RAG 证据列表。 */
    private List<Citation> citations;

    /**
     * 创建元数据事件。
     */
    public static ChatStreamEvent metadata(Long sessionId, String traceId) {
        ChatStreamEvent event = new ChatStreamEvent();
        event.setType("metadata");
        event.setSessionId(sessionId);
        event.setTraceId(traceId);
        return event;
    }

    /**
     * 创建答案增量事件。
     */
    public static ChatStreamEvent delta(Long sessionId, String traceId, String content) {
        ChatStreamEvent event = new ChatStreamEvent();
        event.setType("delta");
        event.setSessionId(sessionId);
        event.setTraceId(traceId);
        event.setContent(content);
        return event;
    }

    /**
     * 创建引用证据事件。
     */
    public static ChatStreamEvent citations(Long sessionId, String traceId, List<Citation> citations) {
        ChatStreamEvent event = new ChatStreamEvent();
        event.setType("citations");
        event.setSessionId(sessionId);
        event.setTraceId(traceId);
        event.setCitations(citations);
        return event;
    }

    /**
     * 创建结束事件。
     */
    public static ChatStreamEvent done(Long sessionId, String traceId, String content) {
        ChatStreamEvent event = new ChatStreamEvent();
        event.setType("done");
        event.setSessionId(sessionId);
        event.setTraceId(traceId);
        event.setContent(content);
        return event;
    }

    /**
     * 创建错误事件。
     */
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

    public List<Citation> getCitations() {
        return citations;
    }

    public void setCitations(List<Citation> citations) {
        this.citations = citations;
    }
}
