package com.tiny.platform.core.oauth.config.jackson;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SecurityUser 的 userId 字段反序列化器。
 * <p>
 * 将 String 类型的 userId 反序列化为 Long，支持：
 * 1. 从 String 格式反序列化（由 {@link SecurityUserLongSerializer} 序列化）
 * 2. 从 Number 格式反序列化（兼容旧数据）
 * <p>
 * 这是符合官方指南的扩展方式，通过自定义反序列化器而不是修改框架内部实现。
 *
 * @since 1.0.0
 */
public class SecurityUserLongDeserializer extends ValueDeserializer<Long> {

    private static final Logger log = LoggerFactory.getLogger(SecurityUserLongDeserializer.class);

    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        // 支持从 String 或 Number 反序列化
        tools.jackson.core.JsonToken token = p.currentToken();
        if (token == null) {
            token = p.nextToken();
        }
        
        if (token == tools.jackson.core.JsonToken.VALUE_NUMBER_INT
            || token == tools.jackson.core.JsonToken.VALUE_NUMBER_FLOAT) {
            // 如果是数字，直接读取为 Long
            Long value = p.getLongValue();
            log.debug("[SecurityUserLongDeserializer] 反序列化 userId: {} (Number) -> {} (Long)", 
                    p.getText(), value);
            return value;
        } else if (token == tools.jackson.core.JsonToken.VALUE_STRING) {
            // 如果是字符串，解析为 Long
            String stringValue = p.getText();
            if (stringValue == null || stringValue.isEmpty()) {
                log.debug("[SecurityUserLongDeserializer] 反序列化 userId: null 或空字符串");
                return null;
            }
            try {
                Long value = Long.parseLong(stringValue);
                log.debug("[SecurityUserLongDeserializer] 反序列化 userId: \"{}\" (String) -> {} (Long)", 
                        stringValue, value);
                return value;
            } catch (NumberFormatException e) {
                log.error("[SecurityUserLongDeserializer] 无法将字符串 '{}' 解析为 Long", stringValue, e);
                throw ctxt.weirdStringException(stringValue, Long.class,
                        "无法将字符串解析为 Long");
            }
        } else if (token == tools.jackson.core.JsonToken.VALUE_NULL) {
            log.debug("[SecurityUserLongDeserializer] 反序列化 userId: null");
            return null;
        } else {
            log.error("[SecurityUserLongDeserializer] 无法反序列化 userId：期望 String 或 Number，但得到 {}", token);
            return ctxt.reportInputMismatch(Long.class,
                    "无法反序列化 userId：期望 String 或 Number，但得到 %s", token);
        }
    }
}
