package com.tiny.platform.core.dict.support;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;

/**
 * DictTenantScope - 统一字典模块的租户边界判定。
 * <p>
 * 三种模型：
 * <ul>
 *   <li>平台字典：tenantId=0，所有租户只读可见；字典项上租户可对同 value 做“覆盖”（仅改 label）。</li>
 *   <li>租户覆盖：平台字典下的字典项，租户创建同 value 的项时仅能覆盖 label，不能新增平台未定义的 value。</li>
 *   <li>租户自定义字典：tenantId=当前租户，dict_code 在 (tenant_id, dict_code) 下唯一，仅当前租户可见、可写。</li>
 * </ul>
 * 唯一约束：dict_type 为 (tenant_id, dict_code)；dict_item 为 (dict_type_id, tenant_id, value) 同租户内唯一。
 */
public final class DictTenantScope {

    public static final long PLATFORM_TENANT_ID = 0L;

    private DictTenantScope() {
    }

    public static Long requireCurrentTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId <= PLATFORM_TENANT_ID) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少有效租户上下文");
        }
        return tenantId;
    }

    public static boolean isPlatformTenant(Long tenantId) {
        return tenantId != null && tenantId == PLATFORM_TENANT_ID;
    }

    public static boolean isCurrentTenant(Long tenantId, Long currentTenantId) {
        return tenantId != null && tenantId.equals(currentTenantId);
    }

    public static boolean isVisibleTenant(Long tenantId, Long currentTenantId) {
        return isPlatformTenant(tenantId) || isCurrentTenant(tenantId, currentTenantId);
    }
}
