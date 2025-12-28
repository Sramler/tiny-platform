package com.tiny.platform.core.oauth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
@Configuration
public class CorsConfig {

    // 提供自定义 Cors 配置
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 允许的源，可以根据环境配置
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:[*]",  // 本地开发环境
                "http://127.0.0.1:[*]",  // 本地开发环境
                "https://*.yourdomain.com" // 生产环境域名
        ));

        // 允许的 HTTP 方法
        config.setAllowedMethods(Arrays.asList(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name(),
                HttpMethod.PATCH.name()
        ));

        // 允许的请求头
        config.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers",
                // TRACE_ID 相关 headers
                "X-Trace-Id",
                "X-Request-Id",
                "trace-id",
                "traceparent",
                "x-b3-traceid",
                "x-trace-id",
                "x-b3-spanid",
                "span-id"
        ));

        // 允许暴露的响应头
        config.setExposedHeaders(Arrays.asList(
                "Access-Control-Allow-Origin",
                "Access-Control-Allow-Credentials",
                // TRACE_ID 相关 headers，允许前端读取
                "X-Request-Id",
                "X-Trace-Id"
        ));

        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
