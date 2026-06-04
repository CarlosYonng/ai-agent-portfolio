package com.yonng.agent.dto;

import java.util.List;
import java.util.Map;

/**
 * 聊天响应 DTO。
 *
 * <p>citations 用来展示答案证据，traceId 用来回看 Agent 每个节点的执行情况。</p>
 */
public class ChatResponse {

    private Long sessionId;
    /** AI 服务生成的调用链 ID，可用于查询 Agent Trace。 */
    private String traceId;
    private String answer;
    /** RAG 引用证据，前端可展示标题、预览和分数。 */
    private List<Map<String, Object>> citations;

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

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<Map<String, Object>> getCitations() {
        return citations;
    }

    public void setCitations(List<Map<String, Object>> citations) {
        this.citations = citations;
    }
}
