package com.tiny.platform.infrastructure.idempotent.core.record;

import java.time.LocalDateTime;

/**
 * 幂等性记录模型
 * 
 * <p>表示一个幂等性操作的状态记录</p>
 * 
 * @author Auto Generated
 * @since 1.0.0
 */
public class IdempotentRecord {
    
    /**
     * 幂等性 Key
     */
    private String key;
    
    /**
     * 当前状态
     */
    private IdempotentState state;
    
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
    
    public IdempotentRecord() {
    }
    
    public IdempotentRecord(String key, long ttlSeconds) {
        this.key = key;
        this.ttlSeconds = ttlSeconds;
        this.state = IdempotentState.PENDING;
        this.createdAt = LocalDateTime.now();
        this.expireAt = LocalDateTime.now().plusSeconds(ttlSeconds);
    }
    
    public String getKey() {
        return key;
    }
    
    public void setKey(String key) {
        this.key = key;
    }
    
    public IdempotentState getState() {
        return state;
    }
    
    public void setState(IdempotentState state) {
        this.state = state;
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
    
    /**
     * 检查是否已过期
     */
    public boolean isExpired() {
        return expireAt != null && LocalDateTime.now().isAfter(expireAt);
    }
}

