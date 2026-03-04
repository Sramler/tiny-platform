package com.tiny.platform.infrastructure.idempotent.repository.redis;

import com.tiny.platform.infrastructure.idempotent.core.key.IdempotentKey;
import com.tiny.platform.infrastructure.idempotent.core.record.IdempotentRecord;
import com.tiny.platform.infrastructure.idempotent.core.record.IdempotentState;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisIdempotentRepositoryTest {

    @Test
    void checkAndSet_should_cover_success_and_duplicate_paths() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        RedisIdempotentRepository repository = new RedisIdempotentRepository(redisTemplate);
        IdempotentKey key = IdempotentKey.of("http", "orders", "1");

        String redisKey = "idempotent:http:orders:1";
        String stateKey = redisKey + ":state";

        when(valueOps.setIfAbsent(eq(redisKey), eq("PENDING"), eq(Duration.ofSeconds(30)))).thenReturn(true);
        assertThat(repository.checkAndSet(key, 30)).isTrue();
        verify(valueOps).set(eq(stateKey), eq("PENDING"), eq(Duration.ofSeconds(30)));

        when(valueOps.setIfAbsent(eq(redisKey), eq("PENDING"), eq(Duration.ofSeconds(10)))).thenReturn(false);
        assertThat(repository.checkAndSet(key, 10)).isFalse();
        verify(valueOps, never()).set(eq(stateKey), eq("PENDING"), eq(Duration.ofSeconds(10)));
    }

    @Test
    void delete_exists_and_expire_should_delegate_to_redis_template() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        RedisIdempotentRepository repository = new RedisIdempotentRepository(redisTemplate);
        IdempotentKey key = IdempotentKey.of("http", "orders", "2");
        String redisKey = "idempotent:http:orders:2";
        String stateKey = redisKey + ":state";

        when(redisTemplate.hasKey(redisKey)).thenReturn(true);
        assertThat(repository.exists(key)).isTrue();
        when(redisTemplate.hasKey(redisKey)).thenReturn(null);
        assertThat(repository.exists(key)).isFalse();

        repository.delete(key);
        verify(redisTemplate).delete(redisKey);
        verify(redisTemplate).delete(stateKey);

        repository.expire(key, 15);
        verify(redisTemplate).expire(redisKey, Duration.ofSeconds(15));
        verify(redisTemplate).expire(stateKey, Duration.ofSeconds(15));
    }

    @Test
    void getRecord_should_cover_valid_invalid_and_null_state() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        RedisIdempotentRepository repository = new RedisIdempotentRepository(redisTemplate);
        IdempotentKey key = IdempotentKey.of("http", "orders", "3");
        String stateKey = "idempotent:http:orders:3:state";

        when(valueOps.get(stateKey)).thenReturn("SUCCESS");
        IdempotentRecord valid = repository.getRecord(key);
        assertThat(valid).isNotNull();
        assertThat(valid.getKey()).isEqualTo("http:orders:3");
        assertThat(valid.getState()).isEqualTo(IdempotentState.SUCCESS);

        when(valueOps.get(stateKey)).thenReturn("INVALID_STATE");
        assertThat(repository.getRecord(key)).isNull();

        when(valueOps.get(stateKey)).thenReturn(null);
        assertThat(repository.getRecord(key)).isNull();
    }

    @Test
    void getState_and_updateState_should_cover_valid_invalid_and_ttl_branches() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        RedisIdempotentRepository repository = new RedisIdempotentRepository(redisTemplate);
        IdempotentKey key = IdempotentKey.of("http", "orders", "4");
        String stateKey = "idempotent:http:orders:4:state";

        when(valueOps.get(stateKey)).thenReturn("FAILED");
        assertThat(repository.getState(key)).isEqualTo(IdempotentState.FAILED);

        when(valueOps.get(stateKey)).thenReturn("BAD");
        assertThat(repository.getState(key)).isEqualTo(IdempotentState.PENDING);

        when(valueOps.get(stateKey)).thenReturn(null);
        assertThat(repository.getState(key)).isNull();

        when(redisTemplate.getExpire(stateKey)).thenReturn(120L, 0L);
        repository.updateState(key, IdempotentState.SUCCESS);
        repository.updateState(key, IdempotentState.FAILED);

        verify(valueOps).set(stateKey, "SUCCESS", Duration.ofSeconds(120));
        verify(valueOps).set(stateKey, "FAILED", Duration.ofSeconds(60));
    }
}
