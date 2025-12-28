package com.tiny.platform.infrastructure.scheduling.exception;

import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;

/**
 * 创建 {@link SchedulingException} 的便捷工具。
 * 
 * <p>使用统一的 ErrorCode 替代独立的 SchedulingErrorCode</p>
 */
public final class SchedulingExceptions {

    private SchedulingExceptions() {
    }

    public static SchedulingException notFound(String message, Object... args) {
        return new SchedulingException(ErrorCode.NOT_FOUND, format(message, args));
    }

    public static SchedulingException validation(String message, Object... args) {
        return new SchedulingException(ErrorCode.VALIDATION_ERROR, format(message, args));
    }

    public static SchedulingException conflict(String message, Object... args) {
        return new SchedulingException(ErrorCode.RESOURCE_CONFLICT, format(message, args));
    }

    public static SchedulingException operationNotAllowed(String message, Object... args) {
        return new SchedulingException(ErrorCode.FORBIDDEN, format(message, args));
    }

    public static SchedulingException systemError(String message, Throwable cause, Object... args) {
        return new SchedulingException(ErrorCode.INTERNAL_ERROR, format(message, args), cause);
    }

    public static SchedulingException systemError(String message, Object... args) {
        return new SchedulingException(ErrorCode.INTERNAL_ERROR, format(message, args));
    }

    private static String format(String message, Object... args) {
        return args == null || args.length == 0 ? message : String.format(message, args);
    }
}
