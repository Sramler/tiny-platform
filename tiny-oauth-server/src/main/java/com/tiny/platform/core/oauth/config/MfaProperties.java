package com.tiny.platform.core.oauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MFA 配置属性类
 * 使用 @ConfigurationProperties 实现类型安全的配置绑定
 */
@Component
@ConfigurationProperties(prefix = "security.mfa")
public class MfaProperties {

    /**
     * MFA 模式
     * - NONE: 完全禁用 MFA，不推荐也不拦截
     * - OPTIONAL: 推荐绑定，允许跳过
     * - REQUIRED: 强制绑定，不允许跳过
     */
    private String mode = "NONE";

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    /**
     * 判断是否禁用 MFA
     */
    public boolean isDisabled() {
        return "NONE".equalsIgnoreCase(mode);
    }

    /**
     * 判断是否强制绑定
     */
    public boolean isRequired() {
        return "REQUIRED".equalsIgnoreCase(mode);
    }

    /**
     * 判断是否推荐绑定（可跳过）
     */
    public boolean isRecommended() {
        return "OPTIONAL".equalsIgnoreCase(mode);
    }
}
