package com.tiny.platform.infrastructure.scheduling.controller;

import com.tiny.platform.infrastructure.scheduling.security.SchedulingAccessGuard;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 最小配置：仅启用方法安全并注册 scheduling 权限守卫，供 RBAC 集成测试使用。
 * 不拉取完整应用安全链，仅验证 @PreAuthorize 是否生效。
 */
@TestConfiguration
@Profile("rbac-test")
@EnableWebSecurity
@EnableMethodSecurity
public class SchedulingControllerRbacTestConfig {

    @Bean
    public SchedulingAccessGuard schedulingAccessGuard() {
        return new SchedulingAccessGuard();
    }

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(c -> c.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public WebMvcConfigurer pageableConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new PageableHandlerMethodArgumentResolver());
            }
        };
    }
}
