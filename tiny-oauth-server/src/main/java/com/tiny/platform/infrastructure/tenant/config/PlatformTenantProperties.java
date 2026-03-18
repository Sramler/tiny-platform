package com.tiny.platform.infrastructure.tenant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 平台租户配置，用于迁移期“平台 = 某租户 code”的语义。
 *
 * <p>与 docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md 一致：默认租户仅作迁移期兼容承载，
 * 长期应改为 scope_type=PLATFORM。配置项：tiny.platform.tenant.platform-tenant-code，默认 "default"。</p>
 */
@Component
@ConfigurationProperties(prefix = "tiny.platform.tenant")
public class PlatformTenantProperties {

    /**
     * 平台租户编码（迁移期：用该 code 的租户表示“平台”或模板来源）。
     * 默认 "default"，可与 tiny.idempotent.ops.platform-tenant-code 保持一致。
     */
    private String platformTenantCode = "default";

    public String getPlatformTenantCode() {
        return StringUtils.hasText(platformTenantCode) ? platformTenantCode.trim() : "default";
    }

    public void setPlatformTenantCode(String platformTenantCode) {
        this.platformTenantCode = platformTenantCode;
    }
}
