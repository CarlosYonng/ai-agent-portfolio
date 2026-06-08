package com.yonng.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Java 后端启动类。
 *
 * <p>这个服务负责承接前端/API 请求，并把真正的大模型编排工作转发给 ai-service。
 * 这种拆分能体现你的 Java 后端能力，也方便把 Python AI 能力独立迭代。</p>
 */
@SpringBootApplication
@MapperScan("com.yonng.agent.mapper")
public class AgentBackendApplication {

    /**
     * 启动 Spring Boot Java 后端服务。
     */
    public static void main(String[] args) {
        SpringApplication.run(AgentBackendApplication.class, args);
    }
}
