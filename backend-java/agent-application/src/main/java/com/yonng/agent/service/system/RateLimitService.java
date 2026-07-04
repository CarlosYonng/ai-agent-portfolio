package com.yonng.agent.service.system;

import org.springframework.stereotype.Service;

/**
 * 速率限制服务。
 *
 * <p>各接口频率配置：
 * - 登录：每分钟 5 次 / IP
 * - 注册：每小时 3 次 / IP
 * - 聊天：每分钟 20 次 / 用户
 * - 故障诊断：每分钟 10 次 / 用户
 * - API 通用：每秒 50 次 / 用户
 * </p>
 */
@Service
public class RateLimitService {

    private final RedisCacheService cacheService;

    public RateLimitService(RedisCacheService cacheService) {
        this.cacheService = cacheService;
    }

    /** 登录限流：每分钟 5 次 */
    public boolean checkLogin(String ip) {
        return cacheService.tryAcquire("login:" + ip, 5, 60);
    }

    /** 注册限流：每小时 3 次 */
    public boolean checkRegister(String ip) {
        return cacheService.tryAcquire("register:" + ip, 3, 3600);
    }

    /** 聊天限流：每分钟 20 次 */
    public boolean checkChat(Long userId) {
        return cacheService.tryAcquire("chat:user:" + userId, 20, 60);
    }

    /** 故障诊断限流：每分钟 10 次 */
    public boolean checkDiagnose(Long userId) {
        return cacheService.tryAcquire("diagnose:user:" + userId, 10, 60);
    }
}
