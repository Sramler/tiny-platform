package com.tiny.platform.infrastructure.core.exception.exception;

import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;

/**
 * 未授权异常（401）
 * 
 * <p>用于表示用户未登录或令牌无效的情况，会自动使用 {@link ErrorCode#UNAUTHORIZED}。</p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 检查令牌
 * if (token == null || !isValidToken(token)) {
 *     throw new UnauthorizedException("令牌无效或已过期");
 * }
 * 
 * // 检查用户是否登录
 * if (authentication == null || !authentication.isAuthenticated()) {
 *     throw new UnauthorizedException("未登录");
 * }
 * </pre>
 * 
 * @author Tiny Platform
 * @since 1.0.0
 */
public class UnauthorizedException extends BusinessException {
    
    /**
     * 创建未授权异常
     * 
     * @param message 错误消息
     */
    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }
    
    /**
     * 创建未授权异常（包含原因）
     * 
     * @param message 错误消息
     * @param cause 原因异常
     */
    public UnauthorizedException(String message, Throwable cause) {
        super(ErrorCode.UNAUTHORIZED, message, cause);
    }
}

