package com.yonng.agent.domain.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 聊天消息。
 *
 * <p>user/assistant 消息统一存在这张表，traceId 用来关联 AI 服务的 Agent 执行链路，
 * citations 保存回答引用的知识库证据，便于历史回看。</p>
 */
@TableName("chat_message")
public class ChatMessage {

    /** 消息主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 消息所属客户。 */
    private Long customerId;

    /** 消息所属会话。 */
    private Long sessionId;

    /** 消息角色：user 或 assistant。 */
    private String role;

    /** 消息正文；用户问题和 AI 回答都统一写在这里。 */
    private String content;

    /** assistant 消息关联的 Agent Trace；用户消息通常为空。 */
    private String traceId;

    /** assistant 消息的引用证据 JSON 字符串。 */
    private String citations;

    /** 消息创建时间。 */
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getCitations() { return citations; }
    public void setCitations(String citations) { this.citations = citations; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
