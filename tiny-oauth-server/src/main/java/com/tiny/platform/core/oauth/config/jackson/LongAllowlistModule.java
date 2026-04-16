package com.tiny.platform.core.oauth.config.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Jackson 2 compatibility placeholder.
 *
 * <p>授权持久化链路已迁移到 Spring Security 7 的 Jackson 3 {@code JsonMapper}，
 * 不再需要通过反射修改 Spring Security Jackson 2 allowlist。保留这个类仅为了兼容
 * 现有测试与调用点，避免在本轮 Boot 4 迁移里扩大改动面。</p>
 */
public class LongAllowlistModule extends SimpleModule {

    private static final long serialVersionUID = 1L;

    public LongAllowlistModule() {
        super("LongAllowlistModule");
    }
}
