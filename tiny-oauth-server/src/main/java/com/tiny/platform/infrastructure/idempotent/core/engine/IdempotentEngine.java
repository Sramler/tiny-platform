package com.tiny.platform.infrastructure.idempotent.core.engine;

import com.tiny.platform.infrastructure.idempotent.core.context.IdempotentContext;
import com.tiny.platform.infrastructure.idempotent.core.exception.IdempotentException;
import com.tiny.platform.infrastructure.idempotent.core.key.IdempotentKey;
import com.tiny.platform.infrastructure.idempotent.core.record.IdempotentState;
import com.tiny.platform.infrastructure.idempotent.core.repository.IdempotentRepository;
import com.tiny.platform.infrastructure.idempotent.core.strategy.IdempotentStrategy;

import java.util.function.Supplier;

/**
 * 幂等执行引擎
 * 
 * <p>负责幂等性检查和状态流转的核心引擎</p>
 * 
 * @author Auto Generated
 * @since 1.0.0
 */
public class IdempotentEngine {
    
    private final IdempotentRepository repository;
    
    public IdempotentEngine(IdempotentRepository repository) {
        this.repository = repository;
    }
    
    /**
     * 处理幂等性请求（检查并设置）
     * 
     * @param key 幂等性 Key
     * @param ttlSeconds TTL（秒）
     * @param failOpen 是否 fail-open
     * @return 幂等性上下文
     */
    public IdempotentContext process(IdempotentKey key, long ttlSeconds, boolean failOpen) {
        IdempotentStrategy strategy = new IdempotentStrategy(ttlSeconds, failOpen);
        IdempotentContext context = new IdempotentContext(key, strategy);
        
        try {
            boolean isFirstRequest = repository.checkAndSet(key, ttlSeconds);
            if (!isFirstRequest) {
                IdempotentState existingState = repository.getState(key);
                if (existingState != null) {
                    context.setState(existingState);
                }
            }
        } catch (Exception e) {
            if (!failOpen) {
                throw new IdempotentException("幂等性服务不可用", e);
            }
        }
        
        return context;
    }
    
    /**
     * 检查 Key 是否存在
     * 
     * @param key 幂等性 Key
     * @return 是否存在
     */
    public boolean exists(IdempotentKey key) {
        return repository.exists(key);
    }
    
    /**
     * 删除幂等性 Key
     * 
     * @param key 幂等性 Key
     */
    public void delete(IdempotentKey key) {
        repository.delete(key);
    }
    
    /**
     * 执行幂等性保护的业务逻辑
     * 
     * @param context 幂等性上下文
     * @param executor 业务执行器
     * @return 执行结果
     * @throws Throwable 业务异常或幂等性异常
     */
    public <T> T execute(IdempotentContext context, Supplier<T> executor) throws Throwable {
        // 检查并设置幂等性 token
        boolean isFirstRequest;
        try {
            isFirstRequest = repository.checkAndSet(context.getKey(), context.getTtlSeconds());
        } catch (Exception e) {
            // 存储服务异常处理
            if (context.getStrategy().isFailOpen()) {
                // fail-open：继续执行业务逻辑
                return executor.get();
            } else {
                // fail-close：抛出异常
                throw new IdempotentException("幂等性服务不可用", e);
            }
        }
        
        // 如果是重复请求，抛出异常
        if (!isFirstRequest) {
            IdempotentState existingState = repository.getState(context.getKey());
            if (existingState == IdempotentState.SUCCESS) {
                // 之前已经成功，返回成功结果（可以根据需要返回缓存的结果）
                throw new IdempotentException("重复请求，操作已成功");
            } else {
                // 其他状态，抛出重复请求异常
                throw new IdempotentException("请勿重复提交");
            }
        }
        
        try {
            // 执行业务逻辑
            T result = executor.get();
            // 执行成功，更新状态
            repository.updateState(context.getKey(), IdempotentState.SUCCESS);
            context.setState(IdempotentState.SUCCESS);
            return result;
        } catch (Throwable e) {
            // 执行失败，更新状态并删除 token，允许重试
            repository.updateState(context.getKey(), IdempotentState.FAILED);
            repository.delete(context.getKey());
            context.setState(IdempotentState.FAILED);
            throw e;
        }
    }
}

