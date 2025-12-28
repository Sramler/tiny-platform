package com.tiny.platform.core.oauth.config;

import com.tiny.platform.core.oauth.interceptor.HttpRequestLoggingInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class HttpRequestLoggingMvcConfig implements WebMvcConfigurer {

    private final HttpRequestLoggingInterceptor interceptor;

    public HttpRequestLoggingMvcConfig(HttpRequestLoggingInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/**");
    }
}
