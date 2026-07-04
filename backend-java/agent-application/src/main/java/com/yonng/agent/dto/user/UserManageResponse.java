package com.yonng.agent.dto.user;

import com.yonng.agent.domain.user.SysUser;

/**
 * 用户管理列表响应。
 */
public class UserManageResponse {

    private Long id;
    private Long customerId;
    private String username;
    private String displayName;
    private String email;
    private String roleCode;
    private String status;
    private String createdAt;

    public static UserManageResponse from(SysUser user) {
        UserManageResponse r = new UserManageResponse();
        r.id = user.getId();
        r.customerId = user.getCustomerId();
        r.username = user.getUsername();
        r.displayName = user.getDisplayName();
        r.email = user.getEmail();
        r.roleCode = user.getRoleCode();
        r.status = user.getStatus();
        r.createdAt = user.getCreatedAt() != null ? user.getCreatedAt().toString() : null;
        return r;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
