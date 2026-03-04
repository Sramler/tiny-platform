package com.tiny.platform.core.oauth.config;

import com.tiny.platform.core.oauth.interceptor.HttpRequestLoggingInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class HttpRequestLoggingMvcConfig implements WebMvcConfigurer {

    private final HttpRequestLoggingInterceptor interceptor;
    private final AsyncTaskExecutor asyncTaskExecutor;

    public HttpRequestLoggingMvcConfig(HttpRequestLoggingInterceptor interceptor,
                                       @Qualifier("taskExecutor") AsyncTaskExecutor asyncTaskExecutor) {
        this.interceptor = interceptor;
        this.asyncTaskExecutor = asyncTaskExecutor;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/**");
    }

    @Override
    public void configureAsyncSupport(@NonNull AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(asyncTaskExecutor);
    }
}
