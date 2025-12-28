package com.tiny.platform.infrastructure.idempotent.sdk.exception;

/**
 * 幂等性 SDK 异常
 * 
 * <p>当检测到重复请求时抛出此异常。</p>
 * <p>继承自 core 异常，提供业务友好的异常信息。</p>
 * 
 * @author Auto Generated
 * @since 1.0.0
 */
public class IdempotentException extends com.tiny.platform.infrastructure.idempotent.core.exception.IdempotentException {
    
    public IdempotentException(String message) {
        super(message);
    }
    
    public IdempotentException(String message, Throwable cause) {
        super(message, cause);
    }
}
