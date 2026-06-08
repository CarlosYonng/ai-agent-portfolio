package com.yonng.agent.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

/**
 * 聊天响应 DTO。
 *
 * <p>citations 用来展示答案证据，traceId 用来回看 Agent 每个节点的执行情况。</p>
 */
public class ChatResponse {

    /** 会话 ID；Java 后端会在调用 AI 服务前创建或复用会话。 */
    @JsonAlias("session_id")
    private Long sessionId;

    /** AI 服务生成的调用链 ID，可用于查询 Agent Trace。 */
    @JsonAlias("trace_id")
    private String traceId;

    /** AI 服务生成的最终答案。 */
    private String answer;

    /** RAG 引用证据，前端可展示标题、预览和分数。 */
    private List<Citation> citations;

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

    public List<Citation> getCitations() {
        return citations;
    }

    public void setCitations(List<Citation> citations) {
        this.citations = citations;
    }
}
