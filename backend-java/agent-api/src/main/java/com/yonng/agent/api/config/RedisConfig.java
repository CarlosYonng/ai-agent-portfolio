package com.yonng.agent.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisConnectionException;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.lang.reflect.Proxy;

/**
 * Redis 配置。
 *
 * <p>
 * 安全策略：
 * - 序列化使用 JSON，避免 JDK 序列化漏洞
 * - Key 统一前缀，避免与外部系统冲突
 * - 连接池限制，防止资源耗尽
 * - Redisson 用于分布式锁
 * </p>
 *
 * <p>
 * 降级策略：
 * - Redisson 连接失败时返回兜底代理，不阻塞应用启动
 * - 所有 Redis 操作由 {@code RedisCacheService} 的 try-catch 接管
 * - 缓存不可用时自动回源数据库
 * </p>
 */
@Configuration
@EnableCaching
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.username:}")
    private String redisUsername;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Key 用 String 序列化
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);

        // Value 用 JSON 序列化（比 JDK 序列化安全、可读）
        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTyping(mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL);
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(mapper);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 创建 Redisson 客户端。
     * <p>连接失败时自动降级，返回一个兜底代理避免阻塞启动，
     * 后续操作由 {@code RedisCacheService} 的异常捕获接管。</p>
     */
    @Bean
    public RedissonClient redissonClient() {
        String address = "redis://" + redisHost + ":" + redisPort;
        try {
            Config config = new Config();
            config.useSingleServer()
                    .setAddress(address)
                    .setConnectionPoolSize(10)
                    .setConnectionMinimumIdleSize(2)
                    .setRetryAttempts(3)
                    .setRetryInterval(1500)
                    .setTimeout(3000);
            if (redisUsername != null && !redisUsername.isEmpty()) {
                config.useSingleServer().setUsername(redisUsername);
            }
            if (redisPassword != null && !redisPassword.isEmpty()) {
                config.useSingleServer().setPassword(redisPassword);
            }
            return Redisson.create(config);
        } catch (Exception e) {
            log.warn("Redis {}:{} 连接失败，启动降级模式（缓存不可用时自动回源数据库）",
                    redisHost, redisPort, e);
            return createDegradedClient();
        }
    }

    /**
     * 创建兜底 RedissonClient 代理。
     * 所有方法抛出异常，由 RedisCacheService 的 try-catch 统一捕获降级。
     */
    private static RedissonClient createDegradedClient() {
        return (RedissonClient) Proxy.newProxyInstance(
                RedissonClient.class.getClassLoader(),
                new Class<?>[]{RedissonClient.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("toString".equals(name)) return "DegradedRedissonClient(Redis unavailable)";
                    if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                    if ("equals".equals(name)) return proxy == args[0];
                    // 上游 RedisCacheService 统一 catch Exception 处理
                    throw new RedisConnectionException("Redis 不可用，工作于降级模式");
                });
    }
}
