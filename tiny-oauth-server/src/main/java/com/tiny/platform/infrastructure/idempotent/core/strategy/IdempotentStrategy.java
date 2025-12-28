package com.tiny.platform.infrastructure.idempotent.core.strategy;

/**
 * 幂等性策略
 * 
 * <p>定义幂等性处理的策略配置</p>
 * 
 * @author Auto Generated
 * @since 1.0.0
 */
public class IdempotentStrategy {
    
    /**
     * TTL（秒）
     */
    private long ttlSeconds;
    
    /**
     * 失败策略：true 表示 fail-open（失败时继续执行），false 表示 fail-close（失败时抛出异常）
     */
    private boolean failOpen;
    
    /**
     * 是否启用
     */
    private boolean enabled;
    
    public IdempotentStrategy() {
        this.ttlSeconds = 300; // 默认 5 分钟
        this.failOpen = true;  // 默认 fail-open
        this.enabled = true;
    }
    
    public IdempotentStrategy(long ttlSeconds, boolean failOpen) {
        this.ttlSeconds = ttlSeconds;
        this.failOpen = failOpen;
        this.enabled = true;
    }
    
    public long getTtlSeconds() {
        return ttlSeconds;
    }
    
    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }
    
    public boolean isFailOpen() {
        return failOpen;
    }
    
    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}

