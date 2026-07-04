package com.yonng.agent.service.system;

import org.springframework.stereotype.Service;

/**
 * 请求去重服务。
 *
 * <p>同一用户 5 秒内提交相同问题，自动去重。
 * Key = dedup:{userId}:{questionMd5}，TTL = 5s</p>
 */
@Service
public class RequestDedupService {

    private final RedisCacheService cacheService;

    public RequestDedupService(RedisCacheService cacheService) {
        this.cacheService = cacheService;
    }

    /** 检查是否重复提交。true = 已存在（重复），false = 新请求 */
    public boolean isDuplicate(Long userId, String question) {
        if (userId == null || question == null) return false;
        String key = "dedup:" + userId + ":" + md5(question);
        // 原子 SET NX EX 5s：设置成功表示新请求，失败表示已在处理中
        return !cacheService.setIfAbsent(key, "1", 5);
    }

    private String md5(String text) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return text; // 降级：直接用原文
        }
    }
}
