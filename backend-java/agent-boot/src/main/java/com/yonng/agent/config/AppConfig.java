package com.yonng.agent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Primary;
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

    /**
     * 构造调用 Python AI 服务的 RestClient。
     */
    @Bean
    @Primary
    public RestClient aiRestClient(AgentProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getAiConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getAiReadTimeoutMs());
        return RestClient.builder()
                .baseUrl(properties.getAiServiceBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 构造诊断服务客户端；短超时保证异常推送不会拖慢主请求返回。
     */
    @Bean
    public RestClient diagnosisRestClient(AgentProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getDiagnosisConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getDiagnosisReadTimeoutMs());
        return RestClient.builder()
                .baseUrl(properties.getDiagnosisServiceBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
