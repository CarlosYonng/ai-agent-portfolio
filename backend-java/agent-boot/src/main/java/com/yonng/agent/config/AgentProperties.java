package com.yonng.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 相关配置。
 *
 * <p>把外部服务地址放进配置类，后续可以继续加入超时时间、租户开关等配置。</p>
 */
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /** Python AI 服务的基础地址，例如 http://localhost:8000。 */
    private String aiServiceBaseUrl;

    public String getAiServiceBaseUrl() {
        return aiServiceBaseUrl;
    }

    public void setAiServiceBaseUrl(String aiServiceBaseUrl) {
        this.aiServiceBaseUrl = aiServiceBaseUrl;
    }
}
