package com.yonng.agent.service.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonng.agent.domain.user.SysCustomer;
import com.yonng.agent.domain.user.SysUser;
import com.yonng.agent.dto.user.AuthResponse;
import com.yonng.agent.dto.user.LoginRequest;
import com.yonng.agent.dto.user.RegisterRequest;
import com.yonng.agent.dto.user.UserInfo;
import com.yonng.agent.exception.BusinessException;
import com.yonng.agent.exception.ErrorCode;
import com.yonng.agent.mapper.user.SysCustomerMapper;
import com.yonng.agent.mapper.user.SysUserMapper;
import com.yonng.agent.service.system.JwtBlacklistService;
import com.yonng.agent.service.system.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 认证服务。
 *
 * <p>处理注册、登录、登出和 JWT 令牌发放。
 * 登录频率受 Redis 限流保护，登出 token 加入 Redis 黑名单。</p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final SysCustomerMapper customerMapper;
    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RateLimitService rateLimitService;
    private final JwtBlacklistService jwtBlacklistService;

    public AuthService(SysCustomerMapper customerMapper, SysUserMapper sysUserMapper,
                       PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider,
                       RateLimitService rateLimitService,
                       JwtBlacklistService jwtBlacklistService) {
        this.customerMapper = customerMapper;
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.rateLimitService = rateLimitService;
        this.jwtBlacklistService = jwtBlacklistService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        SysCustomer customer = customerMapper.selectOne(new LambdaQueryWrapper<SysCustomer>()
                .eq(SysCustomer::getInviteCode, normalizeInviteCode(request.getInviteCode())));
        if (customer == null || !"ACTIVE".equals(customer.getStatus())) {
            throw new BusinessException(ErrorCode.AUTH_INVITE_CODE_INVALID);
        }
        // 禁止通过平台客户邀请码注册外部用户
        if ("PLATFORM".equals(customer.getInviteCode())) {
            throw new BusinessException(ErrorCode.AUTH_PLATFORM_INVITE_REJECTED);
        }
        if (usernameExists(request.getUsername())) {
            throw new BusinessException(ErrorCode.USER_USERNAME_EXISTS, "用户名已被注册");
        }

        SysUser user = new SysUser();
        user.setCustomerId(customer.getId());
        user.setUsername(request.getUsername());
        user.setDisplayName(request.getDisplayName());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setRoleCode("USER");
        user.setStatus("ACTIVE");

        try {
            sysUserMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.USER_USERNAME_EXISTS, "用户名已被注册");
        }

        log.info("user_registered userId={} customerId={} username={}", user.getId(), user.getCustomerId(), user.getUsername());
        String token = jwtTokenProvider.generateToken(user);
        UserInfo userInfo = new UserInfo(user.getId(), user.getCustomerId(),
                user.getUsername(), user.getDisplayName(), user.getRoleCode());
        return new AuthResponse(token, userInfo);
    }

    public AuthResponse login(LoginRequest request, String clientIp) {
        // Redis 限流：同一 IP 每分钟最多 5 次登录尝试
        if (clientIp != null && !clientIp.isBlank()) {
            rateLimitService.checkLogin(clientIp);
        }
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.getUsername()));

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("login_failed username={} reason=invalid_credentials", request.getUsername());
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            log.warn("login_failed userId={} username={} reason=account_disabled", user.getId(), user.getUsername());
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }

        user.setLastLoginAt(LocalDateTime.now());
        sysUserMapper.updateById(user);

        String token = jwtTokenProvider.generateToken(user);
        UserInfo userInfo = new UserInfo(user.getId(), user.getCustomerId(),
                user.getUsername(), user.getDisplayName(), user.getRoleCode());
        log.info("login_success userId={} customerId={} username={} role={}",
                user.getId(), user.getCustomerId(), user.getUsername(), user.getRoleCode());
        return new AuthResponse(token, userInfo);
    }

    private boolean usernameExists(String username) {
        return sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)) > 0;
    }

    /** 登出：将当前 token 加入 JWT 黑名单，后续请求自动失效 */
    public void logout(String token) {
        String jti = jwtTokenProvider.getJtiFromToken(token);
        if (jti != null) {
            jwtBlacklistService.blacklist(jti);
            log.info("user_logout jti={}", jti);
        }
    }

    private String normalizeInviteCode(String inviteCode) {
        return inviteCode == null ? null : inviteCode.trim().toUpperCase();
    }
}
