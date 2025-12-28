package com.tiny.platform.infrastructure.idempotent.repository.memory;

import com.tiny.platform.infrastructure.idempotent.core.key.IdempotentKey;
import com.tiny.platform.infrastructure.idempotent.core.record.IdempotentRecord;
import com.tiny.platform.infrastructure.idempotent.core.record.IdempotentState;
import com.tiny.platform.infrastructure.idempotent.core.repository.IdempotentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的幂等性存储实现（主要用于测试）
 * 
 * @author Auto Generated
 * @since 1.0.0
 */
public class MemoryIdempotentRepository implements IdempotentRepository {
    
    private static final Logger log = LoggerFactory.getLogger(MemoryIdempotentRepository.class);
    
    private final Map<String, IdempotentRecord> storage = new ConcurrentHashMap<>();
    
    @Override
    public synchronized boolean checkAndSet(IdempotentKey key, long ttlSeconds) {
        String keyStr = key.getFullKey();
        IdempotentRecord existing = storage.get(keyStr);
        if (existing != null) {
            if (existing.getExpireAt().isAfter(LocalDateTime.now())) {
                log.debug("幂等性token已存在: key={}", keyStr);
                return false; // 已存在且未过期
            }
            // 已过期，删除
            storage.remove(keyStr);
        }
        
        // 插入新记录
        IdempotentRecord record = new IdempotentRecord(keyStr, ttlSeconds);
        record.setState(IdempotentState.PENDING);
        storage.put(keyStr, record);
        log.debug("幂等性token设置成功: key={}, ttl={}秒", keyStr, ttlSeconds);
        return true;
    }
    
    @Override
    public void delete(IdempotentKey key) {
        String keyStr = key.getFullKey();
        storage.remove(keyStr);
        log.debug("幂等性token删除成功: key={}", keyStr);
    }
    
    @Override
    public boolean exists(IdempotentKey key) {
        String keyStr = key.getFullKey();
        IdempotentRecord record = storage.get(keyStr);
        return record != null && record.getExpireAt().isAfter(LocalDateTime.now());
    }
    
    @Override
    public IdempotentRecord getRecord(IdempotentKey key) {
        String keyStr = key.getFullKey();
        return storage.get(keyStr);
    }
    
    @Override
    public IdempotentState getState(IdempotentKey key) {
        String keyStr = key.getFullKey();
        IdempotentRecord record = storage.get(keyStr);
        return record != null ? record.getState() : null;
    }
    
    @Override
    public void updateState(IdempotentKey key, IdempotentState state) {
        String keyStr = key.getFullKey();
        IdempotentRecord record = storage.get(keyStr);
        if (record != null) {
            record.setState(state);
            log.debug("更新幂等性token状态成功: key={}, state={}", keyStr, state);
        }
    }
    
    @Override
    public void expire(IdempotentKey key, long ttlSeconds) {
        String keyStr = key.getFullKey();
        IdempotentRecord record = storage.get(keyStr);
        if (record != null) {
            record.setExpireAt(LocalDateTime.now().plusSeconds(ttlSeconds));
            log.debug("延长幂等性token TTL成功: key={}, ttl={}秒", keyStr, ttlSeconds);
        }
    }
    
    /**
     * 清空所有数据（测试用）
     */
    public void clear() {
        storage.clear();
    }
}
