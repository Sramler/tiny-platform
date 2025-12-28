package com.tiny.platform.core.oauth.config.jackson;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * ResourceResponseDto 的 MixIn 接口
 * <p>用于在 JacksonConfig 中配置，禁用类型信息，避免序列化时包含 {@code @class} 字段
 * 和集合类型的 {@code ["java.util.ArrayList", []]} 格式
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE) // 禁用类型信息
public interface ResourceResponseDtoMixin {
}
