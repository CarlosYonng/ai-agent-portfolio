package com.yonng.agent.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。
 */
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 128, message = "用户名长度 2~128 个字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 128, message = "密码长度 6~128 个字符")
    private String password;

    @NotBlank(message = "显示名称不能为空")
    @Size(max = 128, message = "显示名称最长 128 个字符")
    private String displayName;

    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "邀请码不能为空")
    @Size(max = 64, message = "邀请码最长 64 个字符")
    private String inviteCode;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }
}
