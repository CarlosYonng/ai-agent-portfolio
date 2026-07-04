package com.yonng.agent.domain.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 聊天会话。
 *
 * <p>会话是多轮问答的容器，第一版只保存标题和归属用户。</p>
 */
@TableName("chat_session")
public class ChatSession {

    /** 会话主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话所属租户。 */
    private Long customerId;

    /** 会话所属用户。 */
    private Long userId;

    /** 会话绑定的知识库；为空表示租户全局问答。 */
    private Long kbId;

    /** 会话标题，通常由首个问题截断生成。 */
    private String title;

    /** 会话创建时间。 */
    private OffsetDateTime createdAt;

    /** 会话最后更新时间。 */
    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getKbId() {
        return kbId;
    }

    public void setKbId(Long kbId) {
        this.kbId = kbId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
