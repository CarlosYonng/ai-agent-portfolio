package com.yonng.agent.domain.user;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;

import java.time.LocalDateTime;

/**
 * 租户实体，映射 sys_tenant 表。
 *
 * <p>租户是数据隔离的基本单位，每个外部用户（USER）归属一个租户，
 * 超级管理员跨租户管理。</p>
 */
@TableName("sys_customer")
public class SysCustomer {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String contactName;
    private String contactEmail;
    private String inviteCode;
    private String status;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
