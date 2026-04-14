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
 * 开发/本地自愈（bootstrap-only）：仅在 {@code auto-initialize-platform-template-if-missing=true} 时运行；
 * 若 {@code tenant_id IS NULL} 平台模板<strong>已齐全</strong>则立即返回，<strong>不会</strong>读取 {@code platform-tenant-code}（CARD-14A）。
 *
 * <p>与 {@link TenantBootstrapService#ensurePlatformTemplatesInitialized()} 行为一致；
 * 不经过租户管理 API，便于从未有过平台身份的环境执行 {@code ensure-platform-admin.sh}。</p>
 *
 * <p><strong>Fail-fast（CARD-14A 收口）</strong>：若平台模板缺失且历史回填无法完成（例如未配置 {@code platform-tenant-code}、来源租户不存在等），
 * 下层抛出的运行时异常会<strong>原样向上抛出</strong>，使应用启动失败；不会仅记日志后继续半启动。</p>
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
                        "【bootstrap 历史入口】平台模板缺失：已按显式 tiny.platform.tenant.platform-tenant-code 自动回填 "
                                + "tenant_id IS NULL 的角色/资源模板。生产环境请勿开启 "
                                + "auto-initialize-platform-template-if-missing。");
            } else {
                log.debug(
                        "平台模板（tenant_id IS NULL）已存在，跳过 dev 自愈回填；未使用 platform-tenant-code（CARD-14A）。");
            }
        } catch (RuntimeException e) {
            log.error(
                    "【bootstrap 历史入口】自动回填平台模板失败，启动将中止。请检查 tiny.platform.tenant.platform-tenant-code、"
                            + "来源租户数据或库状态；有平台身份时可调用 POST /sys/tenants/platform-template/initialize。原因：{}",
                    e.getMessage(),
                    e);
            throw e;
        }
    }
}
