package com.tiny.platform.infrastructure.tenant.guard;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 统一的租户生命周期守卫。
 *
 * <p>用于在各模块写操作入口处收口 FROZEN / DECOMMISSIONED 租户的写保护策略。</p>
 */
@Component
public class TenantLifecycleGuard {

    private static final Logger logger = LoggerFactory.getLogger(TenantLifecycleGuard.class);

    private final TenantRepository tenantRepository;

    public TenantLifecycleGuard(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    /**
     * 当当前 activeTenantId 对应租户为 FROZEN 或 DECOMMISSIONED 时禁止写操作。
     *
     * @param module    调用模块标识（如 "scheduling"、"user"）
     * @param operation 具体操作标识（如 "createTaskType"）
     */
    public void assertNotFrozenForWrite(String module, String operation) {
        Long tenantId = TenantContext.getActiveTenantId();
        if (tenantId == null) {
            return;
        }
        var tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return;
        }
        String lifecycleStatus = tenant.getLifecycleStatus();
        if ("FROZEN".equalsIgnoreCase(lifecycleStatus)) {
            logger.warn(
                    "Deny write for frozen tenant: tenantId={}, module={}, operation={}",
                    tenantId, module, operation);
            throw new BusinessException(
                    ErrorCode.RESOURCE_STATE_INVALID,
                    "当前租户已被冻结，禁止执行写操作");
        }
        if (!"DECOMMISSIONED".equalsIgnoreCase(lifecycleStatus)) {
            return;
        }
        logger.warn(
                "Deny write for decommissioned tenant: tenantId={}, module={}, operation={}",
                tenantId, module, operation);
        throw new BusinessException(
                ErrorCode.RESOURCE_STATE_INVALID,
                "当前租户已下线，禁止执行写操作");
    }

    public boolean isFrozen(Long tenantId) {
        return tenantRepository.isTenantFrozen(tenantId);
    }
}
