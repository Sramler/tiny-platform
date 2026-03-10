package com.tiny.platform.infrastructure.idempotent.repository.database;

import com.tiny.platform.infrastructure.idempotent.core.key.IdempotentKey;
import com.tiny.platform.infrastructure.idempotent.core.record.IdempotentRecord;
import com.tiny.platform.infrastructure.idempotent.core.record.IdempotentState;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseIdempotentRepositoryTest {

    @Test
    void constructor_should_not_execute_runtime_ddl() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DatabaseIdempotentRepository repository = new DatabaseIdempotentRepository(jdbcTemplate);

        assertThat(repository).isNotNull();
        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void checkAndSet_should_cover_success_duplicate_and_cleanup_failure() {
        IdempotentKey key = IdempotentKey.of("http", "orders", "1");
        String keyStr = key.getFullKey();

        JdbcTemplate successJdbc = mock(JdbcTemplate.class);
        DatabaseIdempotentRepository successRepo = new DatabaseIdempotentRepository(successJdbc);
        when(successJdbc.update(startsWith("DELETE FROM sys_idempotent_token WHERE expire_time < ?"), any(LocalDateTime.class)))
            .thenReturn(1);
        when(successJdbc.update(startsWith("INSERT INTO sys_idempotent_token"), eq(keyStr), eq("PENDING"),
            any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);
        assertThat(successRepo.checkAndSet(key, 30)).isTrue();

        JdbcTemplate duplicateJdbc = mock(JdbcTemplate.class);
        DatabaseIdempotentRepository duplicateRepo = new DatabaseIdempotentRepository(duplicateJdbc);
        when(duplicateJdbc.update(startsWith("DELETE FROM sys_idempotent_token WHERE expire_time < ?"), any(LocalDateTime.class)))
            .thenReturn(0);
        when(duplicateJdbc.update(startsWith("INSERT INTO sys_idempotent_token"), eq(keyStr), eq("PENDING"),
            any(LocalDateTime.class), any(LocalDateTime.class))).thenThrow(new RuntimeException("duplicate"));
        assertThat(duplicateRepo.checkAndSet(key, 30)).isFalse();

        JdbcTemplate cleanupFailJdbc = mock(JdbcTemplate.class);
        DatabaseIdempotentRepository cleanupFailRepo = new DatabaseIdempotentRepository(cleanupFailJdbc);
        when(cleanupFailJdbc.update(startsWith("DELETE FROM sys_idempotent_token WHERE expire_time < ?"), any(LocalDateTime.class)))
            .thenThrow(new RuntimeException("cleanup-fail"));
        when(cleanupFailJdbc.update(startsWith("INSERT INTO sys_idempotent_token"), eq(keyStr), eq("PENDING"),
            any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);
        assertThat(cleanupFailRepo.checkAndSet(key, 30)).isTrue();
    }

    @Test
    void delete_exists_getState_updateState_and_expire_should_cover_success_and_failure_paths() {
        IdempotentKey key = IdempotentKey.of("http", "orders", "2");
        String keyStr = key.getFullKey();
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DatabaseIdempotentRepository repository = new DatabaseIdempotentRepository(jdbcTemplate);

        when(jdbcTemplate.queryForObject(startsWith("SELECT COUNT(*) FROM sys_idempotent_token"), eq(Integer.class), eq(keyStr)))
            .thenReturn(1, null);
        assertThat(repository.exists(key)).isTrue();
        assertThat(repository.exists(key)).isFalse();
        when(jdbcTemplate.queryForObject(startsWith("SELECT COUNT(*) FROM sys_idempotent_token"), eq(Integer.class), eq(keyStr)))
            .thenThrow(new RuntimeException("exists-fail"));
        assertThat(repository.exists(key)).isFalse();

        when(jdbcTemplate.queryForObject(startsWith("SELECT state FROM sys_idempotent_token"), eq(String.class), eq(keyStr)))
            .thenReturn("SUCCESS", null);
        assertThat(repository.getState(key)).isEqualTo(IdempotentState.SUCCESS);
        assertThat(repository.getState(key)).isNull();
        when(jdbcTemplate.queryForObject(startsWith("SELECT state FROM sys_idempotent_token"), eq(String.class), eq(keyStr)))
            .thenThrow(new RuntimeException("state-fail"));
        assertThat(repository.getState(key)).isNull();

        when(jdbcTemplate.update(startsWith("DELETE FROM sys_idempotent_token WHERE id = ?"), eq(keyStr)))
            .thenReturn(1)
            .thenThrow(new RuntimeException("delete-fail"));
        assertThatCode(() -> repository.delete(key)).doesNotThrowAnyException();
        assertThatCode(() -> repository.delete(key)).doesNotThrowAnyException();

        when(jdbcTemplate.update(startsWith("UPDATE sys_idempotent_token SET state = ?"), eq("FAILED"), eq(keyStr)))
            .thenReturn(1)
            .thenThrow(new RuntimeException("update-fail"));
        assertThatCode(() -> repository.updateState(key, IdempotentState.FAILED)).doesNotThrowAnyException();
        assertThatCode(() -> repository.updateState(key, IdempotentState.FAILED)).doesNotThrowAnyException();

        when(jdbcTemplate.update(startsWith("UPDATE sys_idempotent_token SET expire_time = ?"), any(LocalDateTime.class), eq(keyStr)))
            .thenReturn(1)
            .thenThrow(new RuntimeException("expire-fail"));
        assertThatCode(() -> repository.expire(key, 20)).doesNotThrowAnyException();
        assertThatCode(() -> repository.expire(key, 20)).doesNotThrowAnyException();
    }

    @Test
    void getRecord_should_map_result_and_return_null_when_query_fails() throws Exception {
        IdempotentKey key = IdempotentKey.of("http", "orders", "3");
        String keyStr = key.getFullKey();
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DatabaseIdempotentRepository repository = new DatabaseIdempotentRepository(jdbcTemplate);

        when(jdbcTemplate.queryForObject(
            startsWith("SELECT id, state, expire_time, created_time FROM sys_idempotent_token"),
            ArgumentMatchers.<RowMapper<IdempotentRecord>>any(),
            eq(keyStr)
        )).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<IdempotentRecord> mapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            LocalDateTime createdAt = LocalDateTime.of(2026, 2, 26, 12, 0);
            LocalDateTime expireAt = createdAt.plusMinutes(5);
            when(rs.getString("id")).thenReturn(keyStr);
            when(rs.getString("state")).thenReturn("PENDING");
            when(rs.getTimestamp("expire_time")).thenReturn(Timestamp.valueOf(expireAt));
            when(rs.getTimestamp("created_time")).thenReturn(Timestamp.valueOf(createdAt));
            return mapper.mapRow(rs, 0);
        });

        IdempotentRecord record = repository.getRecord(key);
        assertThat(record).isNotNull();
        assertThat(record.getKey()).isEqualTo(keyStr);
        assertThat(record.getState()).isEqualTo(IdempotentState.PENDING);
        assertThat(record.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 2, 26, 12, 0));
        assertThat(record.getExpireAt()).isEqualTo(LocalDateTime.of(2026, 2, 26, 12, 5));

        when(jdbcTemplate.queryForObject(
            startsWith("SELECT id, state, expire_time, created_time FROM sys_idempotent_token"),
            ArgumentMatchers.<RowMapper<IdempotentRecord>>any(),
            eq(keyStr)
        )).thenThrow(new RuntimeException("record-fail"));
        assertThat(repository.getRecord(key)).isNull();
    }
}
