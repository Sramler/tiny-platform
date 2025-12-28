package com.tiny.platform.infrastructure.idempotent.core.context;

import com.tiny.platform.infrastructure.idempotent.core.key.IdempotentKey;
import com.tiny.platform.infrastructure.idempotent.core.record.IdempotentState;
import com.tiny.platform.infrastructure.idempotent.core.strategy.IdempotentStrategy;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 幂等性执行上下文
 * 
 * <p>封装幂等性处理过程中的上下文信息</p>
 * 
 * @author Auto Generated
 * @since 1.0.0
 */
public class IdempotentContext {
    
    /**
     * 幂等性 Key
     */
    private IdempotentKey key;
    
    /**
     * 当前状态
     */
    private IdempotentState state;
    
    /**
     * 策略
     */
    private IdempotentStrategy strategy;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 过期时间
     */
    private LocalDateTime expireAt;
    
    /**
     * TTL（秒）
     */
    private long ttlSeconds;
    
    /**
     * 扩展属性
     */
    private Map<String, Object> attributes;
    
    public IdempotentContext() {
    }
    
    public IdempotentContext(IdempotentKey key, IdempotentStrategy strategy) {
        this.key = key;
        this.strategy = strategy;
        this.ttlSeconds = strategy.getTtlSeconds();
        this.state = IdempotentState.PENDING;
        this.createdAt = LocalDateTime.now();
        this.expireAt = LocalDateTime.now().plusSeconds(ttlSeconds);
    }
    
    public IdempotentKey getKey() {
        return key;
    }
    
    public void setKey(IdempotentKey key) {
        this.key = key;
    }
    
    public IdempotentState getState() {
        return state;
    }
    
    public void setState(IdempotentState state) {
        this.state = state;
    }
    
    public IdempotentStrategy getStrategy() {
        return strategy;
    }
    
    public void setStrategy(IdempotentStrategy strategy) {
        this.strategy = strategy;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getExpireAt() {
        return expireAt;
    }
    
    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }
    
    public long getTtlSeconds() {
        return ttlSeconds;
    }
    
    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }
    
    public Map<String, Object> getAttributes() {
        return attributes;
    }
    
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
    
    /**
     * 检查是否已过期
     */
    public boolean isExpired() {
        return expireAt != null && LocalDateTime.now().isAfter(expireAt);
    }
}
