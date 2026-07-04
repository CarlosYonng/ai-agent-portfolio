package com.yonng.agent.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员创建/编辑用户的请求。
 */
public class UserManageRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 128, message = "用户名长度 2~128 个字符")
    private String username;

    @Size(min = 6, max = 128, message = "密码长度 6~128 个字符")
    private String password;

    @NotBlank(message = "显示名称不能为空")
    @Size(max = 128, message = "显示名称最长 128 个字符")
    private String displayName;

    private String email;

    @NotBlank(message = "角色不能为空")
    private String roleCode;

    private Long customerId;

    private String status;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
