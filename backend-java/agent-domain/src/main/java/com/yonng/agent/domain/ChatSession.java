package com.yonng.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

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
    private Long tenantId;

    /** 会话所属用户。 */
    private Long userId;

    /** 会话标题，通常由首个问题截断生成。 */
    private String title;

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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
