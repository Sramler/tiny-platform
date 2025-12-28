package com.tiny.platform.infrastructure.idempotent.repository.database;

import com.tiny.platform.infrastructure.idempotent.core.key.IdempotentKey;
import com.tiny.platform.infrastructure.idempotent.core.record.IdempotentRecord;
import com.tiny.platform.infrastructure.idempotent.core.record.IdempotentState;
import com.tiny.platform.infrastructure.idempotent.core.repository.IdempotentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 基于数据库的幂等性存储实现
 * 
 * <p>使用数据库表存储幂等性 token，适合没有 Redis 的环境。</p>
 * 
 * @author Auto Generated
 * @since 1.0.0
 */
public class DatabaseIdempotentRepository implements IdempotentRepository {
    
    private static final Logger log = LoggerFactory.getLogger(DatabaseIdempotentRepository.class);
    
    private static final String TABLE_NAME = "sys_idempotent_token";
    
    private final JdbcTemplate jdbcTemplate;
    
    public DatabaseIdempotentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        initTable();
    }
    
    /**
     * 初始化幂等性表（如果不存在）
     */
    private void initTable() {
        try {
            String sql = String.format("""
                CREATE TABLE IF NOT EXISTS %s (
                    id VARCHAR(512) PRIMARY KEY,
                    state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                    expire_time DATETIME NOT NULL,
                    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_expire_time (expire_time),
                    INDEX idx_state (state)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='幂等性token表'
                """, TABLE_NAME);
            jdbcTemplate.execute(sql);
            log.info("幂等性表初始化成功");
        } catch (Exception e) {
            log.warn("幂等性表可能已存在，跳过初始化: {}", e.getMessage());
        }
    }
    
    @Override
    @Transactional
    public boolean checkAndSet(IdempotentKey key, long ttlSeconds) {
        String keyStr = key.getFullKey();
        // 先清理过期数据
        cleanupExpiredTokens();
        
        // 尝试插入
        try {
            LocalDateTime expireTime = LocalDateTime.now().plusSeconds(ttlSeconds);
            String insertSql = String.format(
                "INSERT INTO %s (id, state, expire_time, created_time) VALUES (?, ?, ?, ?)",
                TABLE_NAME);
            jdbcTemplate.update(insertSql, keyStr, IdempotentState.PENDING.name(), 
                              expireTime, LocalDateTime.now());
            log.debug("幂等性token设置成功: key={}, ttl={}秒", keyStr, ttlSeconds);
            return true;
        } catch (Exception e) {
            // 主键冲突，说明 token 已存在
            log.debug("幂等性token已存在: key={}", keyStr);
            return false;
        }
    }
    
    @Override
    public void delete(IdempotentKey key) {
        String keyStr = key.getFullKey();
        try {
            String deleteSql = String.format("DELETE FROM %s WHERE id = ?", TABLE_NAME);
            jdbcTemplate.update(deleteSql, keyStr);
            log.debug("幂等性token删除成功: key={}", keyStr);
        } catch (Exception e) {
            log.warn("删除幂等性token失败: key={}, error={}", keyStr, e.getMessage());
        }
    }
    
    @Override
    public boolean exists(IdempotentKey key) {
        String keyStr = key.getFullKey();
        try {
            String sql = String.format("SELECT COUNT(*) FROM %s WHERE id = ?", TABLE_NAME);
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, keyStr);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("检查幂等性token是否存在失败: key={}, error={}", keyStr, e.getMessage());
            return false;
        }
    }
    
    @Override
    public IdempotentRecord getRecord(IdempotentKey key) {
        String keyStr = key.getFullKey();
        try {
            String sql = String.format(
                "SELECT id, state, expire_time, created_time FROM %s WHERE id = ?", TABLE_NAME);
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                IdempotentRecord record = new IdempotentRecord();
                record.setKey(rs.getString("id"));
                record.setState(IdempotentState.valueOf(rs.getString("state")));
                record.setExpireAt(rs.getTimestamp("expire_time").toLocalDateTime());
                record.setCreatedAt(rs.getTimestamp("created_time").toLocalDateTime());
                return record;
            }, keyStr);
        } catch (Exception e) {
            log.debug("获取幂等性记录失败: key={}, error={}", keyStr, e.getMessage());
            return null;
        }
    }
    
    @Override
    public IdempotentState getState(IdempotentKey key) {
        String keyStr = key.getFullKey();
        try {
            String sql = String.format("SELECT state FROM %s WHERE id = ?", TABLE_NAME);
            String state = jdbcTemplate.queryForObject(sql, String.class, keyStr);
            return state != null ? IdempotentState.valueOf(state) : null;
        } catch (Exception e) {
            log.debug("获取幂等性token状态失败: key={}, error={}", keyStr, e.getMessage());
            return null;
        }
    }
    
    @Override
    public void updateState(IdempotentKey key, IdempotentState state) {
        String keyStr = key.getFullKey();
        try {
            String sql = String.format("UPDATE %s SET state = ? WHERE id = ?", TABLE_NAME);
            jdbcTemplate.update(sql, state.name(), keyStr);
            log.debug("更新幂等性token状态成功: key={}, state={}", keyStr, state);
        } catch (Exception e) {
            log.warn("更新幂等性token状态失败: key={}, state={}, error={}", keyStr, state, e.getMessage());
        }
    }
    
    @Override
    public void expire(IdempotentKey key, long ttlSeconds) {
        String keyStr = key.getFullKey();
        try {
            LocalDateTime expireTime = LocalDateTime.now().plusSeconds(ttlSeconds);
            String sql = String.format("UPDATE %s SET expire_time = ? WHERE id = ?", TABLE_NAME);
            jdbcTemplate.update(sql, expireTime, keyStr);
            log.debug("延长幂等性token TTL成功: key={}, ttl={}秒", keyStr, ttlSeconds);
        } catch (Exception e) {
            log.warn("延长幂等性token TTL失败: key={}, error={}", keyStr, e.getMessage());
        }
    }
    
    /**
     * 清理过期的 token
     */
    private void cleanupExpiredTokens() {
        try {
            String cleanupSql = String.format("DELETE FROM %s WHERE expire_time < ?", TABLE_NAME);
            int deleted = jdbcTemplate.update(cleanupSql, LocalDateTime.now());
            if (deleted > 0) {
                log.debug("清理过期幂等性token: {} 条", deleted);
            }
        } catch (Exception e) {
            log.warn("清理过期幂等性token失败: {}", e.getMessage());
        }
    }
}
