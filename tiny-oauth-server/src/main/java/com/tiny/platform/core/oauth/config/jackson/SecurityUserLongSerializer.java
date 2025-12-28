package com.tiny.platform.core.oauth.config.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * SecurityUser 的 userId 字段序列化器。
 * <p>
 * 将 Long 类型的 userId 序列化为 String，避免：
 * 1. Spring Security allowlist 检查失败（Long 不在默认 allowlist 中）
 * 2. JavaScript 精度丢失（JavaScript Number 类型只能安全表示 -2^53 到 2^53 的整数）
 * <p>
 * 这是符合官方指南的扩展方式，通过自定义序列化器而不是修改框架内部实现。
 *
 * @since 1.0.0
 */
public class SecurityUserLongSerializer extends JsonSerializer<Long> {

    private static final Logger log = LoggerFactory.getLogger(SecurityUserLongSerializer.class);

    @Override
    public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
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
