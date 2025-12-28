package com.tiny.platform.infrastructure.core.exception.exception;

import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;

/**
 * 资源不存在异常（404）
 * 
 * <p>用于表示请求的资源不存在的情况，会自动使用 {@link ErrorCode#NOT_FOUND}。</p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 方式 1：使用资源类型名称
 * User user = userRepository.findById(id)
 *     .orElseThrow(() -> new NotFoundException("用户"));
 * 
 * // 方式 2：使用资源类型和 ID
 * User user = userRepository.findById(id)
 *     .orElseThrow(() -> new NotFoundException("用户", id));
 * 
 * // 方式 3：使用自定义消息
 * throw new NotFoundException("用户不存在", cause);
 * </pre>
 * 
 * @author Tiny Platform
 * @since 1.0.0
 */
public class NotFoundException extends BusinessException {
    
    /**
     * 创建资源不存在异常
     * 
     * @param resourceType 资源类型名称（如 "用户"、"订单"）
     */
    public NotFoundException(String resourceType) {
        super(ErrorCode.NOT_FOUND, resourceType + " 不存在");
    }
    
    /**
     * 创建资源不存在异常（包含资源 ID）
     * 
     * @param resourceType 资源类型名称（如 "用户"、"订单"）
     * @param resourceId 资源 ID
     */
    public NotFoundException(String resourceType, Object resourceId) {
        super(ErrorCode.NOT_FOUND, String.format("%s (ID: %s) 不存在", resourceType, resourceId));
    }
    
    /**
     * 创建资源不存在异常（自定义消息）
     * 
     * @param message 错误消息
     * @param cause 原因异常
     */
    public NotFoundException(String message, Throwable cause) {
        super(ErrorCode.NOT_FOUND, message, cause);
    }
}

