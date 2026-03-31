package com.tiny.platform.core.oauth.config;

import com.tiny.platform.core.oauth.tenant.TenantContextContract;
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
        // 使用端口通配：Spring 的 allowedOriginPatterns 中 "[*]" 不能可靠匹配任意端口。
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*", // 任意端口（含 Playwright 动态 Vite 端口）
                "http://127.0.0.1:*",
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
                // 多租户请求头
                TenantContextContract.ACTIVE_TENANT_ID_HEADER,
                "x-active-tenant-id",
                // TRACE_ID 相关 headers
                "X-Trace-Id",
                "X-Request-Id",
                "trace-id",
                "traceparent",
                "x-b3-traceid",
                "x-trace-id",
                "x-b3-spanid",
                "span-id",
                "X-Idempotency-Key",
                "x-idempotency-key",
                "X-XSRF-TOKEN",
                "x-xsrf-token"
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
