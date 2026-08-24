package com.tiny.platform.core.oauth.config.jackson;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SecurityUser 的 userId 字段序列化器。
 * <p>
 * 将 Long 类型的 userId 序列化为 String，避免：
 * 1. JavaScript 精度丢失（JavaScript Number 类型只能安全表示 -2^53 到 2^53 的整数）
 * 2. Web / 授权持久化链路之间出现 Long 表达不一致
 * <p>
 * 这是符合官方指南的扩展方式，通过自定义序列化器而不是修改框架内部实现。
 *
 * @since 1.0.0
 */
public class SecurityUserLongSerializer extends ValueSerializer<Long> {

    private static final Logger log = LoggerFactory.getLogger(SecurityUserLongSerializer.class);

    @Override
    public void serialize(Long value, JsonGenerator gen, SerializationContext serializers) throws JacksonException {
        if (value == null) {
            log.debug("[SecurityUserLongSerializer] 序列化 userId: null");
            gen.writeNull();
        } else {
            // 将 Long 序列化为 String
            String stringValue = String.valueOf(value);
            log.debug("[SecurityUserLongSerializer] 序列化 userId: {} (Long) -> \"{}\" (String)", value, stringValue);
            gen.writeString(stringValue);
        }
    }
}
