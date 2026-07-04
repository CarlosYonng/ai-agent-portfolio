package com.yonng.agent.api.external.auth;

import com.yonng.agent.dto.system.ApiResult;
import com.yonng.agent.dto.system.ApiStatus;
import com.yonng.agent.dto.user.AuthResponse;
import com.yonng.agent.dto.user.LoginRequest;
import com.yonng.agent.dto.user.RegisterRequest;
import com.yonng.agent.service.user.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 外部认证接口。
 *
 * <p>调用方：浏览器登录页和注册页。负责签发/注销用户 JWT，不承载内部服务鉴权。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResult.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResult<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                         HttpServletRequest servletRequest) {
        String clientIp = resolveClientIp(servletRequest);
        return ApiResult.ok(authService.login(request, clientIp));
    }

    @PostMapping("/logout")
    public ApiStatus logout(HttpServletRequest request) {
        String token = resolveBearerToken(request);
        if (token != null) {
            authService.logout(token);
        }
        return ApiStatus.ok();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能包含逗号分隔的多个 IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
