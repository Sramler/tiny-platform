package com.tiny.platform.infrastructure.idempotent.core.exception;

/**
 * 幂等性核心异常
 * 
 * <p>当检测到重复请求时抛出此异常。</p>
 * 
 * @author Auto Generated
 * @since 1.0.0
 */
public class IdempotentException extends RuntimeException {
    
    public IdempotentException(String message) {
        super(message);
    }
    
    public IdempotentException(String message, Throwable cause) {
        super(message, cause);
    }
}

