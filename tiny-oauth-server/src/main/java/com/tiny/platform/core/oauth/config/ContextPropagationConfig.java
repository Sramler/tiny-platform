package com.tiny.platform.core.oauth.config;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.context.integration.Slf4jThreadLocalAccessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

/**
 * Spring 标准上下文传播配置，统一承载 MDC、Micrometer Observation 与 Tracing 上下文。
 * 项目执行器均为显式定义，因此按 Boot 4.1 官方约定直接注册标准 TaskDecorator，
 * 不启用仅面向 Boot 自动执行器的 spring.task.execution.propagate-context。
 */
@Configuration(proxyBeanMethods = false)
public class ContextPropagationConfig {

    @Bean("contextPropagatingTaskDecorator")
    public TaskDecorator contextPropagatingTaskDecorator() {
        // Context Propagation 不会隐式注册 SLF4J MDC；显式接入全局 registry，
        // 使标准装饰器同时传播项目的 traceId/requestId/userId/activeTenantId 等 MDC 字段。
        ContextRegistry registry = ContextRegistry.getInstance()
                .registerThreadLocalAccessor(new Slf4jThreadLocalAccessor());
        ContextSnapshotFactory snapshotFactory = ContextSnapshotFactory.builder()
                .contextRegistry(registry)
                // 提交线程无上下文时也要清理工作线程，避免复用线程泄漏上一个请求的 MDC。
                .clearMissing(true)
                .build();
        return new ContextPropagatingTaskDecorator(snapshotFactory);
    }
}
