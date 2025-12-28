package com.tiny.platform.infrastructure.scheduling.exception;

import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;

/**
 * 调度模块统一业务异常。
 * 
 * <p>继承自统一的 BusinessException，使用统一的 ErrorCode</p>
 */
public class SchedulingException extends BusinessException {

    public SchedulingException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public SchedulingException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
