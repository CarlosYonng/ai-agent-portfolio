package com.yonng.agent.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yonng.agent.dto.system.ApiStatus;
import com.yonng.agent.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.UUID;

/**
 * Spring Security 配置。
 *
 * <p>禁用 CSRF、无状态 Session。API 按调用方分层：
 * /api/auth/** 面向匿名用户，/api/admin/** 面向平台管理台，
 * /api/internal/** 面向受控脚本或内部服务，其他 /api/** 面向已登录业务用户。</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig implements WebMvcConfigurer {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          CurrentUserArgumentResolver currentUserArgumentResolver,
                          ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.currentUserArgumentResolver = currentUserArgumentResolver;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/api/docs/**").permitAll()
                .requestMatchers("/api/internal/**").hasAnyRole("SUPER_ADMIN", "PLATFORM_ADMIN")
                .requestMatchers("/api/admin/**").hasAnyRole("SUPER_ADMIN", "PLATFORM_ADMIN")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, error) ->
                        writeSecurityError(response, ErrorCode.AUTH_REQUIRED, request.getRequestURI()))
                .accessDeniedHandler((request, response, error) ->
                        writeSecurityError(response, ErrorCode.PERMISSION_DENIED, request.getRequestURI()))
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    private void writeSecurityError(HttpServletResponse response, ErrorCode errorCode, String path) throws java.io.IOException {
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiStatus body = ApiStatus.error(errorCode.name(), errorCode.message(), traceId());
        objectMapper.writeValue(response.getWriter(), body);
    }

    private static String traceId() {
        String current = MDC.get("traceId");
        if (current != null && !current.isBlank()) {
            return current;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
