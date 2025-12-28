package com.tiny.platform.infrastructure.core.exception.exception;

import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;

/**
 * 业务异常
 * 
 * <p>用于业务逻辑中的异常情况，会被 {@link com.tiny.platform.infrastructure.core.exception.handler.OAuthServerExceptionHandler}
 * 统一处理为 RFC 7807 Problem 格式。</p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 资源不存在
 * if (user == null) {
 *     throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
 * }
 * 
 * // 资源冲突
 * if (usernameExists) {
 *     throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "用户名已存在");
 * }
 * 
 * // 使用静态工厂方法（推荐）
 * throw BusinessException.notFound("用户不存在");
 * throw BusinessException.conflict("用户名已存在");
 * </pre>
 * 
 * <p>与其他异常的区别：</p>
 * <ul>
 *   <li>{@link NotFoundException}：专门用于资源不存在（404），自动使用 {@code ErrorCode.NOT_FOUND}</li>
 *   <li>{@link UnauthorizedException}：专门用于未授权（401），自动使用 {@code ErrorCode.UNAUTHORIZED}</li>
 *   <li>{@code BusinessException}：通用的业务异常，可以指定任意 {@link ErrorCode}</li>
 * </ul>
 * 
 * @author Tiny Platform
 * @since 1.0.0
 */
public class BusinessException extends RuntimeException {
    
    private final ErrorCode errorCode;
    
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
    
    // ==================== 静态工厂方法（推荐使用） ====================
    
    /**
     * 创建资源不存在异常
     * 
     * @param message 错误消息
     * @return BusinessException
     */
    public static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.NOT_FOUND, message);
    }
    
    /**
     * 创建资源冲突异常
     * 
     * @param message 错误消息
     * @return BusinessException
     */
    public static BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.RESOURCE_CONFLICT, message);
    }
    
    /**
     * 创建资源已存在异常
     * 
     * @param message 错误消息
     * @return BusinessException
     */
    public static BusinessException alreadyExists(String message) {
        return new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, message);
    }
    
    /**
     * 创建参数验证失败异常
     * 
     * @param message 错误消息
     * @return BusinessException
     */
    public static BusinessException validationError(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
    
    /**
     * 创建权限不足异常
     * 
     * @param message 错误消息
     * @return BusinessException
     */
    public static BusinessException forbidden(String message) {
        return new BusinessException(ErrorCode.FORBIDDEN, message);
    }
}

