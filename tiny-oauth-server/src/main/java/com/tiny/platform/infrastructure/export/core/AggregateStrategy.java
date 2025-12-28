package com.tiny.platform.infrastructure.export.core;

/**
 * AggregateStrategy —— 聚合策略接口
 *
 * 每个业务可以实现不同的合计逻辑（sum/count/avg/自定义）
 *
 * 说明：
 *  - isAggregate(fieldName) 用于判断某列是否需要聚合
 *  - accumulate(...) 在流式写入时被调用用于累加
 *  - finalize(...) 在写合计行时将累加值转换为输出（例如 avg 需除以计数）
 */
public interface AggregateStrategy {
    boolean isAggregate(String fieldName);
    Object accumulate(String fieldName, Object currentValue, Object accumulatedValue);
    Object finalize(String fieldName, Object accumulatedValue);
}