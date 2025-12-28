package com.tiny.platform.infrastructure.idempotent.core.record;

/**
 * 幂等性状态枚举
 * 
 * <p>定义幂等性 token 的生命周期状态</p>
 * 
 * @author Auto Generated
 * @since 1.0.0
 */
public enum IdempotentState {
    
    /**
     * 待处理：初始状态，请求已收到但尚未处理完成
     */
    PENDING,
    
    /**
     * 成功：请求处理成功
     */
    SUCCESS,
    
    /**
     * 失败：请求处理失败
     */
    FAILED,
    
    /**
     * 过期：token 已过期
     */
    EXPIRED;
}

