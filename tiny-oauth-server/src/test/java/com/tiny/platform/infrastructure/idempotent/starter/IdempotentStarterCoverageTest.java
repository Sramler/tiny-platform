package com.tiny.platform.infrastructure.idempotent.starter;

import com.tiny.platform.infrastructure.idempotent.console.IdempotentBlacklistChecker;
import com.tiny.platform.infrastructure.idempotent.core.context.IdempotentContext;
import com.tiny.platform.infrastructure.idempotent.core.engine.IdempotentEngine;
import com.tiny.platform.infrastructure.idempotent.core.key.IdempotentKey;
import com.tiny.platform.infrastructure.idempotent.core.repository.IdempotentRepository;
import com.tiny.platform.infrastructure.idempotent.core.strategy.IdempotentStrategy;
import com.tiny.platform.infrastructure.idempotent.metrics.DatabaseIdempotentMetricsRepository;
import com.tiny.platform.infrastructure.idempotent.metrics.IdempotentMetricsService;
import com.tiny.platform.infrastructure.idempotent.repository.database.DatabaseIdempotentRepository;
import com.tiny.platform.infrastructure.idempotent.repository.memory.MemoryIdempotentRepository;
import com.tiny.platform.infrastructure.idempotent.repository.redis.RedisIdempotentRepository;
import com.tiny.platform.infrastructure.idempotent.sdk.annotation.Idempotent;
import com.tiny.platform.infrastructure.idempotent.sdk.aspect.IdempotentAspect;
import com.tiny.platform.infrastructure.idempotent.sdk.facade.IdempotentFacade;
import com.tiny.platform.infrastructure.idempotent.starter.autoconfigure.IdempotentAutoConfiguration;
import com.tiny.platform.infrastructure.idempotent.starter.autoconfigure.RedisIdempotentRepositoryConfiguration;
import com.tiny.platform.infrastructure.idempotent.starter.properties.IdempotentProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class IdempotentStarterCoverageTest {

    @Test
    void properties_should_cover_defaults_and_mutators() {
        IdempotentProperties properties = new IdempotentProperties();
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getStore()).isEqualTo("database");
        assertThat(properties.getTtl()).isEqualTo(300);
        assertThat(properties.isFailOpen()).isTrue();
        assertThat(properties.getHttpApi()).isNotNull();
        assertThat(properties.getHttpApi().isEnabled()).isFalse();
        assertThat(properties.getOps()).isNotNull();
        assertThat(properties.getOps().getPlatformTenantCode()).isNull();
        assertThat(properties.getOps().getMetricsWindowMinutes()).isEqualTo(60);
        assertThat(properties.getOps().getMetricsStore()).isEqualTo("database");
        assertThat(properties.getOps().getMetricsRetentionDays()).isEqualTo(7);
        assertThat(properties.getOps().getMetricsCleanupFixedDelayMs()).isEqualTo(600_000);

        IdempotentProperties.HttpApi httpApi = new IdempotentProperties.HttpApi();
        httpApi.setEnabled(true);
        assertThat(httpApi.isEnabled()).isTrue();

        IdempotentProperties.Ops ops = new IdempotentProperties.Ops();
        ops.setPlatformTenantCode("platform-main");
        ops.setMetricsWindowMinutes(120);
        ops.setMetricsStore("memory");
        ops.setMetricsRetentionDays(14);
        ops.setMetricsCleanupFixedDelayMs(120_000);

        properties.setEnabled(false);
        properties.setStore("memory");
        properties.setTtl(600);
        properties.setFailOpen(false);
        properties.setHttpApi(httpApi);
        properties.setOps(ops);

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getStore()).isEqualTo("memory");
        assertThat(properties.getTtl()).isEqualTo(600);
        assertThat(properties.isFailOpen()).isFalse();
        assertThat(properties.getHttpApi()).isSameAs(httpApi);
        assertThat(properties.getOps()).isSameAs(ops);
        assertThat(properties.getOps().getPlatformTenantCode()).isEqualTo("platform-main");
        assertThat(properties.getOps().getMetricsWindowMinutes()).isEqualTo(120);
        assertThat(properties.getOps().getMetricsStore()).isEqualTo("memory");
        assertThat(properties.getOps().getMetricsRetentionDays()).isEqualTo(14);
        assertThat(properties.getOps().getMetricsCleanupFixedDelayMs()).isEqualTo(120_000);
    }

    @Test
    void auto_configuration_factory_methods_should_return_expected_types() {
        IdempotentAutoConfiguration configuration = new IdempotentAutoConfiguration();
        IdempotentRepository repository = mock(IdempotentRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        IdempotentEngine engine = mock(IdempotentEngine.class);
        IdempotentMetricsService metricsService = new IdempotentMetricsService(new SimpleMeterRegistry());
        IdempotentProperties properties = new IdempotentProperties();
        @SuppressWarnings("unchecked")
        ObjectProvider<MeterRegistry> meterRegistryProvider = mock(ObjectProvider.class);
        when(meterRegistryProvider.getIfAvailable()).thenReturn(new SimpleMeterRegistry());
        @SuppressWarnings("unchecked")
        ObjectProvider<DatabaseIdempotentMetricsRepository> metricsRepositoryProvider = mock(ObjectProvider.class);
        when(metricsRepositoryProvider.getIfAvailable()).thenReturn(null);

        assertThat(configuration.idempotentEngine(repository, metricsService)).isInstanceOf(IdempotentEngine.class);
        assertThat(configuration.idempotentMetricsService(meterRegistryProvider, properties, metricsRepositoryProvider))
            .isInstanceOf(IdempotentMetricsService.class);
        assertThat(configuration.databaseIdempotentMetricsRepository(jdbcTemplate, properties))
            .isInstanceOf(DatabaseIdempotentMetricsRepository.class);
        assertThat(configuration.memoryIdempotentRepository()).isInstanceOf(MemoryIdempotentRepository.class);
        assertThat(configuration.databaseIdempotentRepository(jdbcTemplate)).isInstanceOf(DatabaseIdempotentRepository.class);
        verifyNoInteractions(jdbcTemplate);

        @SuppressWarnings("unchecked")
        ObjectProvider<IdempotentBlacklistChecker> blacklistProvider = mock(ObjectProvider.class);
        when(blacklistProvider.getIfAvailable()).thenReturn(null);
        IdempotentAspect aspect = configuration.idempotentAspect(engine, List.of(), metricsService, blacklistProvider);
        assertThat(aspect).isInstanceOf(IdempotentAspect.class);

        IdempotentFacade facade = configuration.idempotentFacade(engine);
        assertThat(facade).isInstanceOf(IdempotentFacade.class);
    }

    @Test
    void redis_auto_configuration_should_create_repository_reflectively() throws Exception {
        RedisIdempotentRepositoryConfiguration configuration = new RedisIdempotentRepositoryConfiguration();

        IdempotentRepository repository = configuration.redisIdempotentRepository(new StringRedisTemplate());
        assertThat(repository).isInstanceOf(RedisIdempotentRepository.class);

        assertThatThrownBy(() -> configuration.redisIdempotentRepository(new Object()))
            .isInstanceOf(Exception.class);
    }

    @Test
    void facade_should_delegate_supplier_and_runnable_overloads() throws Throwable {
        IdempotentEngine engine = mock(IdempotentEngine.class);
        IdempotentFacade facade = new IdempotentFacade(engine);
        IdempotentContext context = new IdempotentContext(
            IdempotentKey.of("http", "demo", "1"),
            new IdempotentStrategy(10, true)
        );

        doAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        }).when(engine).execute(eq(context), anySupplier());

        assertThat(facade.execute(context, () -> "OK")).isEqualTo("OK");

        AtomicBoolean invoked = new AtomicBoolean(false);
        facade.execute(context, () -> invoked.set(true));
        assertThat(invoked).isTrue();
    }

    @Test
    void idempotent_annotation_should_expose_default_and_custom_values() throws Exception {
        Method defaultMethod = AnnotatedSamples.class.getDeclaredMethod("defaultAnnotated");
        Method customMethod = AnnotatedSamples.class.getDeclaredMethod("customAnnotated");

        Idempotent defaultAnn = defaultMethod.getAnnotation(Idempotent.class);
        assertThat(defaultAnn).isNotNull();
        assertThat(defaultAnn.key()).isEmpty();
        assertThat(defaultAnn.timeout()).isEqualTo(300);
        assertThat(defaultAnn.message()).isEqualTo("请勿重复提交");
        assertThat(defaultAnn.failOpen()).isTrue();

        Idempotent customAnn = customMethod.getAnnotation(Idempotent.class);
        assertThat(customAnn.key()).isEqualTo("#id");
        assertThat(customAnn.timeout()).isEqualTo(5);
        assertThat(customAnn.message()).isEqualTo("dup");
        assertThat(customAnn.failOpen()).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static Supplier<Object> anySupplier() {
        return (Supplier<Object>) any(Supplier.class);
    }

    static class AnnotatedSamples {
        @Idempotent
        void defaultAnnotated() {
        }

        @Idempotent(key = "#id", timeout = 5, message = "dup", failOpen = false)
        void customAnnotated() {
        }
    }
}
