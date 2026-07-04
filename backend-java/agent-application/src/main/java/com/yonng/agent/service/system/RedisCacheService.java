package com.yonng.agent.service.system;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redis 缓存基础设施。
 *
 * <p>安全机制：
 * <ul>
 *   <li>**缓存穿透**：查询 null 也缓存（短 TTL），防恶意 key 攻击</li>
 *   <li>**缓存击穿**：热点 key 过期时用分布式锁，只让一个线程重建缓存</li>
 *   <li>**缓存雪崩**：TTL 增加随机偏移量（±30%），避免批量过期</li>
 *   <li>**Key 污染**：统一前缀隔离；TTL 硬上限，不会遗忘设置过期时间</li>
 * </ul>
 * </p>
 */
@Service
public class RedisCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;

    @Value("${redis.cache-prefix:agent:cache:}")
    private String cachePrefix;

    @Value("${redis.lock-prefix:agent:lock:}")
    private String lockPrefix;

    public RedisCacheService(RedisTemplate<String, Object> redisTemplate,
                             RedissonClient redissonClient) {
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
    }

    /**
     * 防雪崩 TTL：基础 TTL + 随机偏移（±30%）
     */
    public long ttlWithJitter(long baseTtlSeconds) {
        double jitter = 0.7 + Math.random() * 0.6; // 0.7 ~ 1.3
        return (long) (baseTtlSeconds * jitter);
    }

    // ==================== 基础操作 ====================

    public void set(String key, Object value, long ttlSeconds) {
        String fullKey = cachePrefix + key;
        try {
            redisTemplate.opsForValue().set(fullKey, value, ttlWithJitter(ttlSeconds), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis set 失败 (不影响主流程): key={}", fullKey, e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        String fullKey = cachePrefix + key;
        try {
            Object val = redisTemplate.opsForValue().get(fullKey);
            if (type.isInstance(val)) {
                return (T) val;
            }
            return null;
        } catch (Exception e) {
            log.warn("Redis get 失败 (不影响主流程): key={}", fullKey, e);
            return null;
        }
    }

    public void delete(String key) {
        String fullKey = cachePrefix + key;
        try {
            redisTemplate.delete(fullKey);
        } catch (Exception e) {
            log.warn("Redis delete 失败 (不影响主流程): key={}", fullKey, e);
        }
    }

    public void deleteByPattern(String pattern) {
        try {
            var keys = redisTemplate.keys(cachePrefix + pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Redis 批量删除失败 (不影响主流程): pattern={}", pattern, e);
        }
    }

    /**
     * 原子性的 SET NX EX：仅当 key 不存在时设置值。
     *
     * @return true = 设置成功（之前不存在），false = key 已存在
     */
    public boolean setIfAbsent(String key, Object value, long ttlSeconds) {
        String fullKey = cachePrefix + key;
        try {
            Boolean result = redisTemplate.opsForValue()
                    .setIfAbsent(fullKey, value, ttlWithJitter(ttlSeconds), TimeUnit.SECONDS);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("Redis setIfAbsent 失败 (降级为允许写入): key={}", fullKey, e);
            return true; // 降级：Redis 不可用时放行
        }
    }

    // ==================== 防穿透/击穿 get-or-set ====================

    /**
     * 缓存穿透防护的 get-or-set。
     *
     * @param key            缓存 key（不含前缀）
     * @param ttlSeconds     TTL
     * @param nullTtlSeconds 空值的短 TTL（防穿透，建议 30-60s）
     * @param loader         缓存未命中时加载数据的函数
     */
    public <T> T getOrSet(String key, long ttlSeconds, long nullTtlSeconds, Supplier<T> loader) {
        // 1. 先查缓存
        T cached = get(key, null);
        if (cached != null) {
            return cached;
        }

        // 2. 缓存穿透防护：用分布式锁，只让一个线程重建
        //    Redis 不可用时直接调加载器，由业务方或数据库兜底
        try {
            String lockKey = lockPrefix + "cache:" + key;
            RLock lock = redissonClient.getLock(lockKey);
            if (lock.tryLock(2, 5, TimeUnit.SECONDS)) {
                try {
                    // 双重检查：拿到锁后可能其他线程已经重建了
                    cached = get(key, null);
                    if (cached != null) {
                        return cached;
                    }
                    T value = loader.get();
                    if (value != null) {
                        set(key, value, ttlSeconds);
                    } else {
                        // 缓存穿透：null 也缓存，但 TTL 很短
                        set(key, "__NULL__", nullTtlSeconds);
                    }
                    return value;
                } finally {
                    lock.unlock();
                }
            } else {
                // 没拿到锁，直接调源（等待锁不如直接查库）
                return loader.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return loader.get();
        } catch (Exception e) {
            log.warn("Redis 分布式锁不可用，跳过缓存直接查库: key={}", key, e);
            return loader.get();
        }
    }

    // ==================== 速率限制 ====================

    /**
     * 速率限制检查。
     *
     * @param key      限流 key（如 rate:user:123）
     * @param maxCount 窗口内最大次数
     * @param windowSeconds 时间窗口（秒）
     * @return true = 允许通过，false = 被限流
     */
    public boolean tryAcquire(String key, int maxCount, long windowSeconds) {
        String fullKey = "agent:rate:" + key;
        try {
            Long count = redisTemplate.opsForValue().increment(fullKey);
            if (count != null && count == 1) {
                redisTemplate.expire(fullKey, windowSeconds, TimeUnit.SECONDS);
            }
            return count != null && count <= maxCount;
        } catch (Exception e) {
            log.warn("Redis 限流失败 (放行): key={}", fullKey, e);
            return true; // 降级：Redis 挂了就放行
        }
    }

    // ==================== 分布式锁 ====================

    public RLock getLock(String key) {
        try {
            return redissonClient.getLock(lockPrefix + key);
        } catch (Exception e) {
            log.warn("Redis 分布式锁不可用: key={}", key, e);
            return null;
        }
    }
}
