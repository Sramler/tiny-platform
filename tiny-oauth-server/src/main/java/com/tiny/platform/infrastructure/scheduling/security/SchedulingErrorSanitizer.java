package com.tiny.platform.infrastructure.scheduling.security;

import org.springframework.util.StringUtils;

/**
 * 调度模块错误信息脱敏。
 * 用于持久化到 task_instance.error_message / task_history 或返回给前端的文案，避免泄露内部堆栈、类名、路径。
 */
public final class SchedulingErrorSanitizer {

    private static final String FALLBACK_MESSAGE = "任务执行失败";
    private static final int MAX_SAFE_LENGTH = 500;

    private SchedulingErrorSanitizer() {
    }

    /**
     * 对将写入 DB 或返回给客户端的错误信息做脱敏。
     * 若消息疑似堆栈、异常类名或过长，则返回统一兜底文案。
     *
     * @param message 原始错误信息，可为 null
     * @return 脱敏后的文案，不会为 null
     */
    public static String sanitizeForPersistence(String message) {
        if (!StringUtils.hasText(message)) {
            return FALLBACK_MESSAGE;
        }
        String trimmed = message.trim();
        if (trimmed.length() > MAX_SAFE_LENGTH) {
            return FALLBACK_MESSAGE;
        }
        // 已知安全前缀：超时、取消等业务语义，可保留
        if (trimmed.startsWith("TIMEOUT:") || trimmed.startsWith("CANCELLED:")) {
            return trimmed;
        }
        // 疑似内部信息：堆栈、异常类名、路径
        if (trimmed.contains(" at ") || trimmed.contains("\tat ")
                || trimmed.contains("Exception") && trimmed.contains(".")
                || trimmed.contains("java.") || trimmed.contains("com.") || trimmed.contains("org.")) {
            return FALLBACK_MESSAGE;
        }
        return trimmed;
    }

    /** 最大返回给前端的日志/结果摘要长度 */
    private static final int MAX_LOG_RESPONSE_LENGTH = 2048;

    /**
     * 对返回给前端的执行结果/日志摘要做脱敏与截断，不暴露堆栈、路径、过长内容。
     */
    public static String sanitizeForLogResponse(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.contains(" at ") || trimmed.contains("\tat ")
                || trimmed.contains("java.") || trimmed.contains("com.") || trimmed.contains("org.")
                || trimmed.contains("Exception") && trimmed.contains(".")) {
            return FALLBACK_MESSAGE;
        }
        if (trimmed.length() > MAX_LOG_RESPONSE_LENGTH) {
            return trimmed.substring(0, MAX_LOG_RESPONSE_LENGTH) + "\n...(已截断)";
        }
        return trimmed;
    }
}
