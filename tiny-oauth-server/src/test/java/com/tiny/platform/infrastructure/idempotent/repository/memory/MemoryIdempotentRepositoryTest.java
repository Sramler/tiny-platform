package com.tiny.platform.infrastructure.idempotent.repository.memory;

import com.tiny.platform.infrastructure.idempotent.core.key.IdempotentKey;
import com.tiny.platform.infrastructure.idempotent.core.record.IdempotentRecord;
import com.tiny.platform.infrastructure.idempotent.core.record.IdempotentState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MemoryIdempotentRepositoryTest {

    @Test
    void checkAndSet_should_handle_duplicate_and_expired_records() {
        MemoryIdempotentRepository repository = new MemoryIdempotentRepository();
        IdempotentKey key = IdempotentKey.of("http", "order", "1");

        assertThat(repository.checkAndSet(key, 60)).isTrue();
        assertThat(repository.checkAndSet(key, 60)).isFalse();

        IdempotentRecord record = repository.getRecord(key);
        assertThat(record).isNotNull();
        record.setExpireAt(LocalDateTime.now().minusSeconds(1));

        assertThat(repository.exists(key)).isFalse();
        assertThat(repository.checkAndSet(key, 60)).isTrue();
        assertThat(repository.exists(key)).isTrue();
    }

    @Test
    void state_and_ttl_operations_should_work_and_ignore_missing_keys() {
        MemoryIdempotentRepository repository = new MemoryIdempotentRepository();
        IdempotentKey key = IdempotentKey.of("http", "pay", "2");
        IdempotentKey missing = IdempotentKey.of("http", "pay", "missing");

        repository.checkAndSet(key, 30);
        assertThat(repository.getState(key)).isEqualTo(IdempotentState.PENDING);

        repository.updateState(key, IdempotentState.SUCCESS);
        assertThat(repository.getState(key)).isEqualTo(IdempotentState.SUCCESS);

        LocalDateTime before = repository.getRecord(key).getExpireAt();
        repository.expire(key, 120);
        assertThat(repository.getRecord(key).getExpireAt()).isAfter(before);

        assertThatCode(() -> repository.updateState(missing, IdempotentState.FAILED)).doesNotThrowAnyException();
        assertThatCode(() -> repository.expire(missing, 1)).doesNotThrowAnyException();
        assertThat(repository.getRecord(missing)).isNull();
        assertThat(repository.getState(missing)).isNull();

        repository.delete(key);
        assertThat(repository.exists(key)).isFalse();

        repository.checkAndSet(key, 10);
        repository.clear();
        assertThat(repository.getRecord(key)).isNull();
    }
}
