package com.yonng.agent.dto.user;

/**
 * 认证响应。
 *
 * <p>登录或注册成功后返回 JWT 令牌和用户基本信息。</p>
 */
public class AuthResponse {

    private String token;
    private String tokenType;
    private UserInfo user;

    public AuthResponse() {}

    public AuthResponse(String token, UserInfo user) {
        this.token = token;
        this.tokenType = "Bearer";
        this.user = user;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

    public UserInfo getUser() { return user; }
    public void setUser(UserInfo user) { this.user = user; }
}
