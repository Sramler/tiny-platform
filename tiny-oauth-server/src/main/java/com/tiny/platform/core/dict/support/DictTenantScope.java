package com.tiny.platform.core.dict.support;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;

/**
 * DictTenantScope - 统一字典模块的租户边界判定。
 *
 * <p>三种模型：</p>
 * <ul>
 *   <li>平台字典：tenantId IS NULL，所有租户只读可见；字典项上租户可对同 value 做"覆盖"（仅改 label）。</li>
 *   <li>租户覆盖：平台字典下的字典项，租户创建同 value 的项时仅能覆盖 label，不能新增平台未定义的 value。</li>
 *   <li>租户自定义字典：tenantId=当前租户，dict_code 在 (tenant_id, dict_code) 下唯一，仅当前租户可见、可写。</li>
 * </ul>
 * <p>唯一约束：dict_type 为 (COALESCE(tenant_id, 0), dict_code)；
 * dict_item 为 (dict_type_id, COALESCE(tenant_id, 0), value)。</p>
 *
 * <p><b>平台语义</b>：{@code tenant_id IS NULL} 表示平台级数据，
 * 与 role/resource 表的 {@code tenant_id IS NULL + level=PLATFORM} 模式统一。</p>
 */
public final class DictTenantScope {

    private DictTenantScope() {
    }

    public static Long requireCurrentTenantId() {
        Long tenantId = TenantContext.getActiveTenantId();
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少有效租户上下文");
        }
        return tenantId;
    }

    public static boolean isPlatformTenant(Long tenantId) {
        return tenantId == null;
    }

    public static boolean isCurrentTenant(Long tenantId, Long currentTenantId) {
        return tenantId != null && tenantId.equals(currentTenantId);
    }

    public static boolean isVisibleTenant(Long tenantId, Long currentTenantId) {
        return isPlatformTenant(tenantId) || isCurrentTenant(tenantId, currentTenantId);
    }

    public static String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return StringUtils.hasText(securityUser.getUsername()) ? securityUser.getUsername() : null;
        }
        if (principal instanceof UserDetails userDetails) {
            return StringUtils.hasText(userDetails.getUsername()) ? userDetails.getUsername() : null;
        }
        String name = authentication.getName();
        if (!StringUtils.hasText(name) || "anonymousUser".equalsIgnoreCase(name)) {
            return null;
        }
        return name.trim();
    }

    public static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return securityUser.getUserId();
        }
        String name = authentication.getName();
        if (!StringUtils.hasText(name) || "anonymousUser".equalsIgnoreCase(name)) {
            return null;
        }
        try {
            return Long.valueOf(name.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
