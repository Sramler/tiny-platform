package com.tiny.platform.infrastructure.idempotent.repository.redis;

import com.tiny.platform.infrastructure.idempotent.core.key.IdempotentKey;
import com.tiny.platform.infrastructure.idempotent.core.record.IdempotentRecord;
import com.tiny.platform.infrastructure.idempotent.core.record.IdempotentState;
import com.tiny.platform.infrastructure.idempotent.core.repository.IdempotentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * 基于 Redis 的幂等性存储实现
 * 
 * <p>使用 Redis 存储幂等性 token，性能更好，适合高并发场景。</p>
 * 
 * @author Auto Generated
 * @since 1.0.0
 */
public class RedisIdempotentRepository implements IdempotentRepository {
    
    private static final Logger log = LoggerFactory.getLogger(RedisIdempotentRepository.class);
    
    private static final String KEY_PREFIX = "idempotent:";
    private static final String STATE_SUFFIX = ":state";
    
    private final StringRedisTemplate redisTemplate;
    
    public RedisIdempotentRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @Override
    public boolean checkAndSet(IdempotentKey key, long ttlSeconds) {
        String redisKey = KEY_PREFIX + key.getFullKey();
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, IdempotentState.PENDING.name(), 
                           Duration.ofSeconds(ttlSeconds));
        if (Boolean.TRUE.equals(success)) {
            // 设置状态
            String stateKey = redisKey + STATE_SUFFIX;
            redisTemplate.opsForValue().set(stateKey, IdempotentState.PENDING.name(), 
                                          Duration.ofSeconds(ttlSeconds));
            log.debug("幂等性token设置成功: key={}, ttl={}秒", key.getFullKey(), ttlSeconds);
        } else {
            log.debug("幂等性token已存在: key={}", key.getFullKey());
        }
        return Boolean.TRUE.equals(success);
    }
    
    @Override
    public void delete(IdempotentKey key) {
        String redisKey = KEY_PREFIX + key.getFullKey();
        redisTemplate.delete(redisKey);
        redisTemplate.delete(redisKey + STATE_SUFFIX);
        log.debug("幂等性token删除成功: key={}", key.getFullKey());
    }
    
    @Override
    public boolean exists(IdempotentKey key) {
        String redisKey = KEY_PREFIX + key.getFullKey();
        return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
    }
    
    @Override
    public IdempotentRecord getRecord(IdempotentKey key) {
        String redisKey = KEY_PREFIX + key.getFullKey();
        String stateKey = redisKey + STATE_SUFFIX;
        String state = redisTemplate.opsForValue().get(stateKey);
        if (state != null) {
            try {
                IdempotentRecord record = new IdempotentRecord(key.getFullKey(), 0);
                record.setState(IdempotentState.valueOf(state));
                return record;
            } catch (IllegalArgumentException e) {
                log.warn("无效的状态值: {}", state);
                return null;
            }
        }
        return null;
    }
    
    @Override
    public IdempotentState getState(IdempotentKey key) {
        String redisKey = KEY_PREFIX + key.getFullKey() + STATE_SUFFIX;
        String state = redisTemplate.opsForValue().get(redisKey);
        if (state != null) {
            try {
                return IdempotentState.valueOf(state);
            } catch (IllegalArgumentException e) {
                log.warn("无效的状态值: {}", state);
                return IdempotentState.PENDING;
            }
        }
        return null;
    }
    
    @Override
    public void updateState(IdempotentKey key, IdempotentState state) {
        String redisKey = KEY_PREFIX + key.getFullKey() + STATE_SUFFIX;
        // 获取原有 TTL
        Long ttl = redisTemplate.getExpire(redisKey);
        if (ttl != null && ttl > 0) {
            redisTemplate.opsForValue().set(redisKey, state.name(), Duration.ofSeconds(ttl));
        } else {
            // 如果没有 TTL，使用默认值（60秒）
            redisTemplate.opsForValue().set(redisKey, state.name(), Duration.ofSeconds(60));
        }
        log.debug("更新幂等性token状态成功: key={}, state={}", key.getFullKey(), state);
    }
    
    @Override
    public void expire(IdempotentKey key, long ttlSeconds) {
        String redisKey = KEY_PREFIX + key.getFullKey();
        redisTemplate.expire(redisKey, Duration.ofSeconds(ttlSeconds));
        redisTemplate.expire(redisKey + STATE_SUFFIX, Duration.ofSeconds(ttlSeconds));
        log.debug("延长幂等性token TTL成功: key={}, ttl={}秒", key.getFullKey(), ttlSeconds);
    }
}
