package com.tiny.platform.infrastructure.idempotent.core;

import com.tiny.platform.infrastructure.idempotent.core.context.IdempotentContext;
import com.tiny.platform.infrastructure.idempotent.core.engine.IdempotentEngine;
import com.tiny.platform.infrastructure.idempotent.core.key.IdempotentKey;
import com.tiny.platform.infrastructure.idempotent.core.mq.IdempotentMqHandler;
import com.tiny.platform.infrastructure.idempotent.core.record.IdempotentRecord;
import com.tiny.platform.infrastructure.idempotent.core.record.IdempotentState;
import com.tiny.platform.infrastructure.idempotent.core.repository.IdempotentRepository;
import com.tiny.platform.infrastructure.idempotent.core.strategy.IdempotentStrategy;
import com.tiny.platform.infrastructure.idempotent.sdk.resolver.IdempotentKeyResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotentCoreAndEngineTest {

    @Test
    void idempotentKey_should_parse_update_and_compare() {
        IdempotentKey key = new IdempotentKey("http", "scope", "u1");
        assertThat(key.getFullKey()).isEqualTo("http:scope:u1");
        assertThat(key.toString()).isEqualTo("http:scope:u1");

        IdempotentKey parsed = IdempotentKey.parse("mq:topic:offset:1");
        assertThat(parsed.getNamespace()).isEqualTo("mq");
        assertThat(parsed.getScope()).isEqualTo("topic");
        assertThat(parsed.getUniqueKey()).isEqualTo("offset:1");
        assertThat(parsed.getFullKey()).isEqualTo("mq:topic:offset:1");

        IdempotentKey mutable = new IdempotentKey();
        mutable.setNamespace("job");
        mutable.setScope("sync");
        mutable.setUniqueKey("20260226");
        assertThat(mutable.getFullKey()).isEqualTo("job:sync:20260226");

        mutable.setScope("sync2");
        assertThat(mutable.getFullKey()).isEqualTo("job:sync2:20260226");

        assertThat(IdempotentKey.of("job", "sync2", "20260226")).isEqualTo(mutable);
        assertThat(mutable.hashCode()).isEqualTo(IdempotentKey.of("job", "sync2", "20260226").hashCode());
        assertThat(mutable).isNotEqualTo(IdempotentKey.of("job", "sync2", "x"));
        assertThat(mutable).isNotEqualTo("x");
        assertThat(mutable).isNotEqualTo(null);

        assertThatThrownBy(() -> IdempotentKey.parse("bad-format"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid idempotent key format");
    }

    @Test
    void strategy_context_and_record_should_cover_basic_behaviour() {
        IdempotentStrategy defaultStrategy = new IdempotentStrategy();
        assertThat(defaultStrategy.getTtlSeconds()).isEqualTo(300);
        assertThat(defaultStrategy.isFailOpen()).isTrue();
        assertThat(defaultStrategy.isEnabled()).isTrue();

        defaultStrategy.setTtlSeconds(90);
        defaultStrategy.setFailOpen(false);
        defaultStrategy.setEnabled(false);
        assertThat(defaultStrategy.getTtlSeconds()).isEqualTo(90);
        assertThat(defaultStrategy.isFailOpen()).isFalse();
        assertThat(defaultStrategy.isEnabled()).isFalse();

        IdempotentStrategy strategy = new IdempotentStrategy(120, true);
        IdempotentKey key = IdempotentKey.of("http", "order.create", "u1");
        IdempotentContext context = new IdempotentContext(key, strategy);
        assertThat(context.getKey()).isEqualTo(key);
        assertThat(context.getStrategy()).isEqualTo(strategy);
        assertThat(context.getState()).isEqualTo(IdempotentState.PENDING);
        assertThat(context.getTtlSeconds()).isEqualTo(120);
        assertThat(context.getCreatedAt()).isNotNull();
        assertThat(context.getExpireAt()).isNotNull();
        assertThat(context.isExpired()).isFalse();

        context.setState(IdempotentState.SUCCESS);
        context.setTtlSeconds(10);
        context.setExpireAt(LocalDateTime.now().minusSeconds(1));
        context.setCreatedAt(LocalDateTime.now().minusSeconds(2));
        context.setAttributes(java.util.Map.of("k", "v"));
        context.setKey(IdempotentKey.of("mq", "topic", "1"));
        context.setStrategy(new IdempotentStrategy(10, false));
        assertThat(context.getState()).isEqualTo(IdempotentState.SUCCESS);
        assertThat(context.getTtlSeconds()).isEqualTo(10);
        assertThat(context.isExpired()).isTrue();
        assertThat(context.getAttributes()).containsEntry("k", "v");
        assertThat(context.getKey().getNamespace()).isEqualTo("mq");
        assertThat(context.getStrategy().isFailOpen()).isFalse();

        IdempotentContext blank = new IdempotentContext();
        blank.setExpireAt(null);
        assertThat(blank.isExpired()).isFalse();

        IdempotentRecord record = new IdempotentRecord("k1", 30);
        assertThat(record.getKey()).isEqualTo("k1");
        assertThat(record.getState()).isEqualTo(IdempotentState.PENDING);
        assertThat(record.getTtlSeconds()).isEqualTo(30);
        assertThat(record.isExpired()).isFalse();

        record.setState(IdempotentState.FAILED);
        record.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        record.setExpireAt(LocalDateTime.now().minusSeconds(1));
        record.setTtlSeconds(99);
        record.setKey("k2");
        assertThat(record.getKey()).isEqualTo("k2");
        assertThat(record.getState()).isEqualTo(IdempotentState.FAILED);
        assertThat(record.getTtlSeconds()).isEqualTo(99);
        assertThat(record.isExpired()).isTrue();

        IdempotentRecord emptyRecord = new IdempotentRecord();
        emptyRecord.setExpireAt(null);
        assertThat(emptyRecord.isExpired()).isFalse();

        assertThat(IdempotentState.values()).containsExactly(
            IdempotentState.PENDING,
            IdempotentState.SUCCESS,
            IdempotentState.FAILED,
            IdempotentState.EXPIRED
        );
    }

    @Test
    void small_types_should_cover_exceptions_mq_results_and_resolver_default_order() {
        RuntimeException cause = new RuntimeException("boom");
        com.tiny.platform.infrastructure.idempotent.core.exception.IdempotentException coreEx =
            new com.tiny.platform.infrastructure.idempotent.core.exception.IdempotentException("dup", cause);
        assertThat(coreEx.getMessage()).isEqualTo("dup");
        assertThat(coreEx.getCause()).isEqualTo(cause);
        assertThat(new com.tiny.platform.infrastructure.idempotent.core.exception.IdempotentException("only-message"))
            .hasMessage("only-message");

        com.tiny.platform.infrastructure.idempotent.sdk.exception.IdempotentException sdkEx =
            new com.tiny.platform.infrastructure.idempotent.sdk.exception.IdempotentException("sdk", cause);
        assertThat(sdkEx).isInstanceOf(com.tiny.platform.infrastructure.idempotent.core.exception.IdempotentException.class);
        assertThat(sdkEx.getMessage()).isEqualTo("sdk");
        assertThat(sdkEx.getCause()).isEqualTo(cause);
        assertThat(new com.tiny.platform.infrastructure.idempotent.sdk.exception.IdempotentException("sdk-only"))
            .hasMessage("sdk-only");

        IdempotentMqHandler.IdempotentMqResult success = IdempotentMqHandler.IdempotentMqResult.success();
        assertThat(success.isSuccess()).isTrue();
        assertThat(success.isDuplicate()).isFalse();
        assertThat(success.getState()).isEqualTo(IdempotentState.SUCCESS);
        assertThat(success.getError()).isNull();

        IdempotentMqHandler.IdempotentMqResult duplicate = IdempotentMqHandler.IdempotentMqResult.duplicate();
        assertThat(duplicate.isSuccess()).isFalse();
        assertThat(duplicate.isDuplicate()).isTrue();
        assertThat(duplicate.getState()).isEqualTo(IdempotentState.PENDING);

        Throwable err = new IllegalStateException("mq");
        IdempotentMqHandler.IdempotentMqResult failed = IdempotentMqHandler.IdempotentMqResult.failed(err);
        assertThat(failed.isSuccess()).isFalse();
        assertThat(failed.isDuplicate()).isFalse();
        assertThat(failed.getState()).isEqualTo(IdempotentState.FAILED);
        assertThat(failed.getError()).isEqualTo(err);

        assertThat(IdempotentMqHandler.MqType.values()).containsExactly(
            IdempotentMqHandler.MqType.KAFKA,
            IdempotentMqHandler.MqType.RABBITMQ,
            IdempotentMqHandler.MqType.ROCKETMQ
        );

        IdempotentKeyResolver resolver = new IdempotentKeyResolver() {
            @Override
            public IdempotentKey resolve(ProceedingJoinPoint joinPoint, Method method, Object[] args) {
                return null;
            }
        };
        assertThat(resolver.getOrder()).isZero();

        IdempotentMqHandler.MqMessageHandler<String> handler = message -> { };
        assertThatCode(() -> handler.handle("payload")).doesNotThrowAnyException();
    }

    @Test
    void engine_process_should_handle_first_duplicate_and_fail_modes() {
        IdempotentKey key = IdempotentKey.of("http", "s", "u");

        IdempotentRepository repository1 = mock(IdempotentRepository.class);
        when(repository1.checkAndSet(key, 60)).thenReturn(true);
        IdempotentContext first = new IdempotentEngine(repository1).process(key, 60, true);
        assertThat(first.getState()).isEqualTo(IdempotentState.PENDING);

        IdempotentRepository repository2 = mock(IdempotentRepository.class);
        when(repository2.checkAndSet(key, 60)).thenReturn(false);
        when(repository2.getState(key)).thenReturn(IdempotentState.SUCCESS);
        IdempotentContext duplicate = new IdempotentEngine(repository2).process(key, 60, true);
        assertThat(duplicate.getState()).isEqualTo(IdempotentState.SUCCESS);

        IdempotentRepository repository3 = mock(IdempotentRepository.class);
        when(repository3.checkAndSet(key, 60)).thenThrow(new RuntimeException("store-down"));
        IdempotentContext failOpen = new IdempotentEngine(repository3).process(key, 60, true);
        assertThat(failOpen.getState()).isEqualTo(IdempotentState.PENDING);

        IdempotentRepository repository4 = mock(IdempotentRepository.class);
        when(repository4.checkAndSet(key, 60)).thenThrow(new RuntimeException("store-down"));
        assertThatThrownBy(() -> new IdempotentEngine(repository4).process(key, 60, false))
            .isInstanceOf(com.tiny.platform.infrastructure.idempotent.core.exception.IdempotentException.class)
            .hasMessageContaining("幂等性服务不可用");
    }

    @Test
    void engine_execute_should_cover_success_duplicate_and_repo_failure_paths() throws Throwable {
        IdempotentKey key = IdempotentKey.of("http", "scope", "x");

        IdempotentRepository successRepo = mock(IdempotentRepository.class);
        when(successRepo.checkAndSet(key, 30)).thenReturn(true);
        IdempotentEngine successEngine = new IdempotentEngine(successRepo);
        IdempotentContext successContext = context(key, 30, false);
        String result = successEngine.execute(successContext, () -> "OK");
        assertThat(result).isEqualTo("OK");
        assertThat(successContext.getState()).isEqualTo(IdempotentState.SUCCESS);
        verify(successRepo).updateState(key, IdempotentState.SUCCESS);

        IdempotentRepository duplicateSuccessRepo = mock(IdempotentRepository.class);
        when(duplicateSuccessRepo.checkAndSet(key, 30)).thenReturn(false);
        when(duplicateSuccessRepo.getState(key)).thenReturn(IdempotentState.SUCCESS);
        IdempotentEngine duplicateSuccessEngine = new IdempotentEngine(duplicateSuccessRepo);
        assertThatThrownBy(() -> duplicateSuccessEngine.execute(context(key, 30, false), () -> "ignored"))
            .isInstanceOf(com.tiny.platform.infrastructure.idempotent.core.exception.IdempotentException.class)
            .hasMessageContaining("重复请求");

        IdempotentRepository duplicatePendingRepo = mock(IdempotentRepository.class);
        when(duplicatePendingRepo.checkAndSet(key, 30)).thenReturn(false);
        when(duplicatePendingRepo.getState(key)).thenReturn(IdempotentState.PENDING);
        IdempotentEngine duplicatePendingEngine = new IdempotentEngine(duplicatePendingRepo);
        assertThatThrownBy(() -> duplicatePendingEngine.execute(context(key, 30, false), () -> "ignored"))
            .isInstanceOf(com.tiny.platform.infrastructure.idempotent.core.exception.IdempotentException.class)
            .hasMessageContaining("请勿重复提交");

        IdempotentRepository failOpenRepo = mock(IdempotentRepository.class);
        when(failOpenRepo.checkAndSet(key, 30)).thenThrow(new RuntimeException("down"));
        IdempotentEngine failOpenEngine = new IdempotentEngine(failOpenRepo);
        String failOpenResult = failOpenEngine.execute(context(key, 30, true), () -> "PASS");
        assertThat(failOpenResult).isEqualTo("PASS");

        IdempotentRepository failCloseRepo = mock(IdempotentRepository.class);
        when(failCloseRepo.checkAndSet(key, 30)).thenThrow(new RuntimeException("down"));
        IdempotentEngine failCloseEngine = new IdempotentEngine(failCloseRepo);
        assertThatThrownBy(() -> failCloseEngine.execute(context(key, 30, false), () -> "ignored"))
            .isInstanceOf(com.tiny.platform.infrastructure.idempotent.core.exception.IdempotentException.class)
            .hasMessageContaining("幂等性服务不可用");
    }

    @Test
    void engine_execute_should_mark_failed_and_rethrow_business_exception() {
        IdempotentKey key = IdempotentKey.of("http", "scope", "x");
        IdempotentRepository repository = mock(IdempotentRepository.class);
        when(repository.checkAndSet(key, 30)).thenReturn(true);
        IdempotentEngine engine = new IdempotentEngine(repository);
        IdempotentContext context = context(key, 30, false);
        IllegalStateException boom = new IllegalStateException("biz");

        assertThatThrownBy(() -> engine.execute(context, () -> { throw boom; }))
            .isEqualTo(boom);

        assertThat(context.getState()).isEqualTo(IdempotentState.FAILED);
        verify(repository).updateState(key, IdempotentState.FAILED);
        verify(repository).delete(key);
    }

    @Test
    void engine_exists_and_delete_should_delegate() {
        IdempotentKey key = IdempotentKey.of("http", "scope", "x");
        IdempotentRepository repository = mock(IdempotentRepository.class);
        when(repository.exists(key)).thenReturn(true);
        IdempotentEngine engine = new IdempotentEngine(repository);

        assertThat(engine.exists(key)).isTrue();
        engine.delete(key);

        verify(repository).exists(key);
        verify(repository).delete(key);
    }

    private static IdempotentContext context(IdempotentKey key, long ttl, boolean failOpen) {
        return new IdempotentContext(key, new IdempotentStrategy(ttl, failOpen));
    }
}
