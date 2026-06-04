package com.yonng.agent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Spring Bean 配置。
 *
 * <p>RestClient 用来调用 Python AI 服务，第一版保持简单；生产环境可以扩展重试和熔断。</p>
 */
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AppConfig {

    @Bean
    public RestClient aiRestClient(AgentProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getAiServiceBaseUrl())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
    }
}

