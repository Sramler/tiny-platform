package com.tiny.platform.infrastructure.idempotent.core.repository;

import com.tiny.platform.infrastructure.idempotent.core.key.IdempotentKey;
import com.tiny.platform.infrastructure.idempotent.core.record.IdempotentRecord;
import com.tiny.platform.infrastructure.idempotent.core.record.IdempotentState;

/**
 * 幂等性存储接口（抽象定义）
 * 
 * <p>定义幂等性 token 的存储和查询操作。</p>
 * <p>所有实现必须在 infra 模块（idempotent-repository）</p>
 * 
 * @author Auto Generated
 * @since 1.0.0
 */
public interface IdempotentRepository {
    
    /**
     * 检查并设置幂等性 token
     * 
     * <p>如果 token 不存在，则设置并返回 true；如果 token 已存在，则返回 false。</p>
     * 
     * @param key 幂等性 key
     * @param ttlSeconds TTL（秒）
     * @return true 表示首次请求（设置成功），false 表示重复请求（已存在）
     */
    boolean checkAndSet(IdempotentKey key, long ttlSeconds);
    
    /**
     * 删除幂等性 token
     * 
     * @param key 幂等性 key
     */
    void delete(IdempotentKey key);
    
    /**
     * 检查 token 是否存在
     * 
     * @param key 幂等性 key
     * @return true 表示存在
     */
    boolean exists(IdempotentKey key);
    
    /**
     * 获取记录
     * 
     * @param key 幂等性 key
     * @return 记录，如果不存在返回 null
     */
    IdempotentRecord getRecord(IdempotentKey key);
    
    /**
     * 获取状态
     * 
     * @param key 幂等性 key
     * @return 状态，如果不存在返回 null
     */
    IdempotentState getState(IdempotentKey key);
    
    /**
     * 更新状态
     * 
     * @param key 幂等性 key
     * @param state 新状态
     */
    void updateState(IdempotentKey key, IdempotentState state);
    
    /**
     * 延长 TTL
     * 
     * @param key 幂等性 key
     * @param ttlSeconds 新的 TTL（秒）
     */
    void expire(IdempotentKey key, long ttlSeconds);
}

