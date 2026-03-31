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

    /**
     * 启动时若检测到不存在平台模板（无 {@code tenant_id IS NULL} 的角色/资源），是否从
     * {@link #platformTenantCode} 指向的租户自动复制一份。
     *
     * <p>默认 {@code false}：生产环境应由受控流程或 {@code POST /sys/tenants/platform-template/initialize}
     *（平台身份）执行。开发环境可在 {@code application-dev.yaml} 中开启，避免历史库从未触发过
     * bootstrap 时 {@code ensure-platform-admin.sh} 因缺少平台模板 {@code ROLE_PLATFORM_ADMIN} 而失败。</p>
     */
    private boolean autoInitializePlatformTemplateIfMissing = false;

    public String getPlatformTenantCode() {
        return StringUtils.hasText(platformTenantCode) ? platformTenantCode.trim() : "default";
    }

    public void setPlatformTenantCode(String platformTenantCode) {
        this.platformTenantCode = platformTenantCode;
    }

    public boolean isAutoInitializePlatformTemplateIfMissing() {
        return autoInitializePlatformTemplateIfMissing;
    }

    public void setAutoInitializePlatformTemplateIfMissing(boolean autoInitializePlatformTemplateIfMissing) {
        this.autoInitializePlatformTemplateIfMissing = autoInitializePlatformTemplateIfMissing;
    }
}
