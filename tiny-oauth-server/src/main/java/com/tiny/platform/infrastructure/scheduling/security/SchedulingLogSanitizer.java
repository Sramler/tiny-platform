package com.tiny.platform.infrastructure.scheduling.security;

import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 调度模块日志脱敏。
 * 避免在日志中输出可能包含敏感信息的参数内容（40-security：不记录密码、密钥等）。
 */
public final class SchedulingLogSanitizer {

    private SchedulingLogSanitizer() {
    }

    /**
     * 将参数 Map 转为仅包含键名与数量的日志摘要，不输出值内容。
     *
     * @param params 任务参数，可为 null
     * @return 如 "keys=[a,b,c], size=3" 或 "null"
     */
    public static String maskParamsForLog(Map<String, Object> params) {
        if (params == null) {
            return "null";
        }
        if (params.isEmpty()) {
            return "keys=[], size=0";
        }
        String keys = params.keySet().stream().sorted().collect(Collectors.joining(", ", "[", "]"));
        return "keys=" + keys + ", size=" + params.size();
    }

    /**
     * 若文案可能包含敏感信息则脱敏后返回，否则返回原文（截断过长）。
     *
     * @param message 原始文案
     * @param maxLen  最大保留长度
     * @return 脱敏/截断后的文案
     */
    public static String sanitizeMessageForLog(String message, int maxLen) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        String trimmed = message.trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, maxLen) + "...";
    }
}
