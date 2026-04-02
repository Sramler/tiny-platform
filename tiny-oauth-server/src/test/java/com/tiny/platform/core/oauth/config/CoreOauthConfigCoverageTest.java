package com.tiny.platform.core.oauth.config;

import com.tiny.platform.core.oauth.filter.HttpRequestLoggingFilter;
import com.tiny.platform.core.oauth.interceptor.HttpRequestLoggingInterceptor;
import com.tiny.platform.core.oauth.service.HttpRequestLogService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CoreOauthConfigCoverageTest {

    @Test
    void shouldConfigureAsyncTaskExecutorWithMdcDecoratorAndCallerRuns() {
        AsyncExecutionConfig config = new AsyncExecutionConfig();
        TaskDecorator decorator = runnable -> runnable;

        ThreadPoolTaskExecutor executor = config.taskExecutor(
                decorator,
                2,
                4,
                16,
                Duration.ofSeconds(5),
                "trace-");

        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(4);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("trace-");
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
            assertThat(ReflectionTestUtils.getField(executor, "taskDecorator")).isSameAs(decorator);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void shouldBuildCorsConfigurationWithTraceHeadersExposed() {
        CorsConfig config = new CorsConfig();
        CorsConfigurationSource source = config.corsConfigurationSource();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader("Origin", "http://localhost:5173");
        CorsConfiguration cors = source.getCorsConfiguration(request);

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOriginPatterns())
                .contains("http://localhost:*", "http://127.0.0.1:*", "https://*.yourdomain.com");
        assertThat(cors.getAllowedMethods()).contains("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH");
        assertThat(cors.getAllowedHeaders())
                .contains("X-Trace-Id", "X-Request-Id", "traceparent", "x-b3-traceid", "x-b3-spanid");
        assertThat(cors.getExposedHeaders()).contains("X-Request-Id", "X-Trace-Id");
        assertThat(cors.getAllowCredentials()).isTrue();
        assertThat(cors.getMaxAge()).isEqualTo(3600L);
    }

    @Test
    void shouldCreateAndRegisterHttpRequestLoggingFilter() {
        HttpRequestLoggingFilterConfig config = new HttpRequestLoggingFilterConfig();
        HttpRequestLoggingProperties properties = new HttpRequestLoggingProperties();
        HttpRequestLogService logService = mock(HttpRequestLogService.class);
        Environment environment = new MockEnvironment().withProperty("spring.profiles.active", "test");

        HttpRequestLoggingFilter filter = config.httpRequestLoggingFilter(properties, logService, environment, "svc");
        FilterRegistrationBean<Filter> registration = config.httpRequestLoggingFilterRegistration(filter);

        assertThat(filter).isNotNull();
        assertThat(ReflectionTestUtils.getField(filter, "serviceName")).isEqualTo("svc");
        assertThat(registration.getFilter()).isSameAs(filter);
        assertThat(ReflectionTestUtils.getField(registration, "name")).isEqualTo("httpRequestLoggingFilter");
        assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        assertThat(registration.getUrlPatterns()).containsExactly("/*");
    }

    @Test
    void shouldRegisterMvcInterceptorAndAsyncTaskExecutor() {
        HttpRequestLoggingInterceptor interceptor = mock(HttpRequestLoggingInterceptor.class);
        AsyncTaskExecutor executor = mock(AsyncTaskExecutor.class);
        HttpRequestLoggingMvcConfig config = new HttpRequestLoggingMvcConfig(interceptor, executor);

        InterceptorRegistry registry = new InterceptorRegistry();
        config.addInterceptors(registry);

        @SuppressWarnings("unchecked")
        List<Object> registrations = (List<Object>) ReflectionTestUtils.getField(registry, "registrations");
        assertThat(registrations).hasSize(1);

        AsyncSupportConfigurer asyncSupportConfigurer = new AsyncSupportConfigurer();
        config.configureAsyncSupport(asyncSupportConfigurer);
        assertThat(ReflectionTestUtils.getField(asyncSupportConfigurer, "taskExecutor")).isSameAs(executor);
    }
}
