package com.tiny.platform.infrastructure.idempotent.sdk.resolver;

import com.tiny.platform.infrastructure.idempotent.core.key.IdempotentKey;
import org.aspectj.lang.ProceedingJoinPoint;

import java.lang.reflect.Method;

/**
 * 幂等性 Key 解析器接口
 * 
 * <p>可插拔的 Key 解析器，支持自定义 Key 生成策略。</p>
 * 
 * @author Auto Generated
 * @since 1.0.0
 */
public interface IdempotentKeyResolver {
    
    /**
     * 解析生成幂等性 Key
     * 
     * @param joinPoint AOP 连接点
     * @param method 方法
     * @param args 方法参数
     * @return 幂等性 Key，如果无法解析返回 null
     */
    IdempotentKey resolve(ProceedingJoinPoint joinPoint, Method method, Object[] args);
    
    /**
     * 获取优先级（数字越小优先级越高）
     * 
     * @return 优先级
     */
    default int getOrder() {
        return 0;
    }
}

