package com.tiny.platform.infrastructure.tenant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * <strong>Bootstrap-only</strong>：与运行时 {@code activeScopeType=PLATFORM} / {@code TenantContext.isPlatformScope()} 无关。
 *
 * <p>{@code platform-tenant-code} 仅在「{@code tenant_id IS NULL} 平台模板缺失、且需从历史租户做一次回填」时由
 * {@link com.tiny.platform.infrastructure.tenant.service.TenantBootstrapServiceImpl} 读取；模板已存在时<strong>不会</strong>读取该配置，
 * 也不得把它当作平台控制面或鉴权真相源（CARD-14A）。生产回填请优先受控流程或平台身份调用
 * {@code POST /sys/tenants/platform-template/initialize}。</p>
 */
@Component
@ConfigurationProperties(prefix = "tiny.platform.tenant")
public class PlatformTenantProperties {

    /**
     * 平台模板来源租户编码（bootstrap / 历史入口兼容配置）。
     * 为空时表示当前环境未声明历史模板来源，必须依赖已存在的 tenant_id IS NULL 平台模板。
     */
    private String platformTenantCode;

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
        return StringUtils.hasText(platformTenantCode) ? platformTenantCode.trim() : null;
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
