package com.yonng.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 聊天消息。
 *
 * <p>user/assistant 消息统一存在这张表，traceId 用来关联 AI 服务的 Agent 执行链路。</p>
 */
@TableName("chat_message")
public class ChatMessage {

    /** 消息主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 消息所属租户。 */
    private Long tenantId;

    /** 消息所属会话。 */
    private Long sessionId;

    /** 消息角色：user 或 assistant。 */
    private String role;

    /** 消息正文；用户问题和 AI 回答都统一写在这里。 */
    private String content;

    /** assistant 消息关联的 Agent Trace；用户消息通常为空。 */
    private String traceId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
