package com.yonng.agent.service.system;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * JWT 黑名单服务。
 *
 * <p>用户退出、被禁用时，将 token 的 jti 加入 Redis 黑名单。
 * JwtAuthenticationFilter 每次请求检查是否在黑名单中。
 * TTL = JWT 过期时间，黑名单到期自动清除。</p>
 */
@Service
public class JwtBlacklistService {

    private static final String BLACKLIST_PREFIX = "blacklist:jwt:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${jwt.expiration-hours:24}")
    private long jwtExpirationHours;

    public JwtBlacklistService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 将 token 加入黑名单 */
    public void blacklist(String jti) {
        if (jti == null || jti.isBlank()) return;
        String key = BLACKLIST_PREFIX + jti;
        redisTemplate.opsForValue().set(key, "1", jwtExpirationHours, TimeUnit.HOURS);
    }

    /** 检查 token 是否在黑名单中 */
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) return false;
        String key = BLACKLIST_PREFIX + jti;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
