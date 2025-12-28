package com.tiny.platform.core.oauth.config;

import com.tiny.platform.core.oauth.filter.HttpRequestLoggingFilter;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/**
 * HttpRequestLoggingFilter 配置
 * 使用 FilterRegistrationBean 手动注册过滤器，确保它在 Spring Security 之前执行
 * 这样可以实现完整的追踪闭环：从前端请求到最终返回，所有日志都包含 traceId
 */
@Configuration
public class HttpRequestLoggingFilterConfig {

    /**
     * 创建 HttpRequestLoggingFilter Bean
     */
    @Bean
    public HttpRequestLoggingFilter httpRequestLoggingFilter(
            HttpRequestLoggingProperties properties,
            Environment environment,
            @Value("${spring.application.name:oauth-server}") String serviceName) {
        return new HttpRequestLoggingFilter(properties, environment, serviceName);
    }

    /**
     * 注册 HttpRequestLoggingFilter，确保它在 Spring Security 过滤器链之前执行
     * 
     * 优先级设置：
     * - HIGHEST_PRECEDENCE = Integer.MIN_VALUE = -2147483648
     * - 确保在所有过滤器之前执行，包括 Spring Security
     */
    @Bean
    public FilterRegistrationBean<Filter> httpRequestLoggingFilterRegistration(
            HttpRequestLoggingFilter httpRequestLoggingFilter) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(httpRequestLoggingFilter);
        registration.addUrlPatterns("/*");
        registration.setName("httpRequestLoggingFilter");
        // 设置为最高优先级，确保在 Spring Security 之前执行
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
