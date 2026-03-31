package com.tiny.platform.infrastructure.tenant.config;

import com.tiny.platform.infrastructure.tenant.service.TenantBootstrapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 开发/本地自愈：在缺少平台模板时于进程启动阶段从配置的 platform-tenant-code 租户回填一份
 * {@code tenant_id IS NULL} 的角色与资源模板。
 *
 * <p>与 {@link TenantBootstrapService#ensurePlatformTemplatesInitialized()} 行为一致；
 * 不经过租户管理 API，便于从未有过平台身份的环境执行 {@code ensure-platform-admin.sh}。</p>
 */
@Component
@Order(Integer.MAX_VALUE / 2)
@ConditionalOnProperty(
        prefix = "tiny.platform.tenant",
        name = "auto-initialize-platform-template-if-missing",
        havingValue = "true"
)
public class PlatformTemplateDevAutoBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformTemplateDevAutoBootstrapRunner.class);

    private final TenantBootstrapService tenantBootstrapService;

    public PlatformTemplateDevAutoBootstrapRunner(TenantBootstrapService tenantBootstrapService) {
        this.tenantBootstrapService = tenantBootstrapService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            boolean initialized = tenantBootstrapService.ensurePlatformTemplatesInitialized();
            if (initialized) {
                log.warn(
                        "平台模板缺失：已按 tiny.platform.tenant 配置从 platform-tenant-code 指向的租户自动回填 "
                                + "tenant_id IS NULL 的角色/资源模板。生产环境请勿开启 "
                                + "auto-initialize-platform-template-if-missing。");
            }
        } catch (Exception e) {
            log.error(
                    "自动回填平台模板失败（{}）。可检查 default 租户下角色/资源是否完整，"
                            + "或修复库后重启；有平台身份时也可调用 POST /sys/tenants/platform-template/initialize。",
                    e.getMessage());
        }
    }
}
