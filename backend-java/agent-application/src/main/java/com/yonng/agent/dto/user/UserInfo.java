package com.yonng.agent.dto.user;

/**
 * 当前用户信息，由 JWT 解析后通过 @CurrentUser 注入。
 */
public class UserInfo {

    private Long userId;
    private Long customerId;
    private String username;
    private String displayName;
    private String role;

    public UserInfo() {}

    public UserInfo(Long userId, Long customerId, String username, String displayName, String role) {
        this.userId = userId;
        this.customerId = customerId;
        this.username = username;
        this.displayName = displayName;
        this.role = role;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
