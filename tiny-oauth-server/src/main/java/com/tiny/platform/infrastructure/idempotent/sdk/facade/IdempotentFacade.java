package com.tiny.platform.infrastructure.idempotent.sdk.facade;

import com.tiny.platform.infrastructure.idempotent.core.context.IdempotentContext;
import com.tiny.platform.infrastructure.idempotent.core.engine.IdempotentEngine;
import com.tiny.platform.infrastructure.idempotent.core.exception.IdempotentException;

import java.util.function.Supplier;

/**
 * 幂等性统一入口（Facade）
 * 
 * <p>提供给业务使用的统一幂等性接口，是业务唯一入口。</p>
 * 
 * @author Auto Generated
 * @since 1.0.0
 */
public class IdempotentFacade {
    
    private final IdempotentEngine engine;
    
    public IdempotentFacade(IdempotentEngine engine) {
        this.engine = engine;
    }
    
    /**
     * 执行幂等性保护的业务逻辑
     * 
     * @param context 幂等性上下文
     * @param executor 业务执行器
     * @return 执行结果
     * @throws IdempotentException 幂等性异常
     * @throws Throwable 业务异常
     */
    public <T> T execute(IdempotentContext context, Supplier<T> executor) throws Throwable {
        return engine.execute(context, executor);
    }
    
    /**
     * 执行幂等性保护的业务逻辑（无返回值）
     * 
     * @param context 幂等性上下文
     * @param executor 业务执行器
     * @throws IdempotentException 幂等性异常
     * @throws Throwable 业务异常
     */
    public void execute(IdempotentContext context, Runnable executor) throws Throwable {
        engine.execute(context, () -> {
            executor.run();
            return null;
        });
    }
}

