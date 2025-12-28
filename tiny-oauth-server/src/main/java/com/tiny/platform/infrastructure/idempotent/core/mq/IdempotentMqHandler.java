package com.tiny.platform.infrastructure.idempotent.core.mq;

import com.tiny.platform.infrastructure.idempotent.core.context.IdempotentContext;
import com.tiny.platform.infrastructure.idempotent.core.record.IdempotentState;

/**
 * MQ 幂等处理器抽象接口
 * 
 * <p>定义 MQ 消息的幂等性处理规范。</p>
 * <p>所有实现必须在 infra 模块（idempotent-mq）</p>
 * 
 * @param <T> 消息类型
 * @author Auto Generated
 * @since 1.0.0
 */
public interface IdempotentMqHandler<T> {
    
    /**
     * 处理消息（带幂等性保证）
     * 
     * @param context 幂等性上下文
     * @param message 消息内容
     * @param handler 业务处理器
     * @return 处理结果
     */
    IdempotentMqResult handle(IdempotentContext context, T message, MqMessageHandler<T> handler);
    
    /**
     * 生成幂等性 Key
     * 
     * @param context 上下文
     * @param message 消息内容
     * @return 幂等性 Key
     */
    com.tiny.platform.infrastructure.idempotent.core.key.IdempotentKey generateIdempotentKey(IdempotentContext context, T message);
    
    /**
     * 获取支持的 MQ 类型
     * 
     * @return MQ 类型
     */
    MqType getMqType();
    
    /**
     * MQ 类型枚举
     */
    enum MqType {
        KAFKA,
        RABBITMQ,
        ROCKETMQ
    }
    
    /**
     * 业务消息处理器
     */
    @FunctionalInterface
    interface MqMessageHandler<T> {
        void handle(T message) throws Exception;
    }
    
    /**
     * 处理结果
     */
    class IdempotentMqResult {
        private boolean success;
        private boolean duplicate;
        private IdempotentState state;
        private Throwable error;
        
        public static IdempotentMqResult success() {
            IdempotentMqResult result = new IdempotentMqResult();
            result.success = true;
            result.state = IdempotentState.SUCCESS;
            return result;
        }
        
        public static IdempotentMqResult duplicate() {
            IdempotentMqResult result = new IdempotentMqResult();
            result.success = false;
            result.duplicate = true;
            result.state = IdempotentState.PENDING;
            return result;
        }
        
        public static IdempotentMqResult failed(Throwable error) {
            IdempotentMqResult result = new IdempotentMqResult();
            result.success = false;
            result.error = error;
            result.state = IdempotentState.FAILED;
            return result;
        }
        
        // Getters and setters
        public boolean isSuccess() { return success; }
        public boolean isDuplicate() { return duplicate; }
        public IdempotentState getState() { return state; }
        public Throwable getError() { return error; }
    }
}

