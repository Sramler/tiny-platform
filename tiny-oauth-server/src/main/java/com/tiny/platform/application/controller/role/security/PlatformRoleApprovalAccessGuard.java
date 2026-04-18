package com.tiny.platform.application.controller.role.security;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component("platformRoleApprovalAccessGuard")
public class PlatformRoleApprovalAccessGuard {

    private static final Set<String> LIST_AUTHORITIES = Set.of("platform:role:approval:list");

    /**
     * 与菜单 / api_endpoint GET 的 OR 组一致：任一会审权限即可查询队列（避免仅有 submit/approve 的用户被方法级拒绝）。
     */
    private static final Set<String> QUERY_QUEUE_AUTHORITIES = Set.of(
        "platform:role:approval:list",
        "platform:role:approval:submit",
        "platform:role:approval:approve",
        "platform:role:approval:reject",
        "platform:role:approval:cancel"
    );

    private static final Set<String> SUBMIT_AUTHORITIES = Set.of("platform:role:approval:submit");

    /**
     * 发起审批申请时需要读取 approval_mode=ONE_STEP 的平台角色候选，避免 submit-only 用户进入页面后下拉为空。
     */
    private static final Set<String> ROLE_CATALOG_AUTHORITIES = Set.of("platform:role:approval:submit");

    private static final Set<String> APPROVE_AUTHORITIES = Set.of("platform:role:approval:approve");

    private static final Set<String> REJECT_AUTHORITIES = Set.of("platform:role:approval:reject");

    private static final Set<String> CANCEL_AUTHORITIES = Set.of("platform:role:approval:cancel");

    public boolean canList(Authentication authentication) {
        return isPlatformScope(authentication) && hasAnyAuthority(authentication, LIST_AUTHORITIES);
    }

    public boolean canQueryQueue(Authentication authentication) {
        return isPlatformScope(authentication) && hasAnyAuthority(authentication, QUERY_QUEUE_AUTHORITIES);
    }

    public boolean canSubmit(Authentication authentication) {
        return isPlatformScope(authentication) && hasAnyAuthority(authentication, SUBMIT_AUTHORITIES);
    }

    public boolean canReadRoleCatalog(Authentication authentication) {
        return isPlatformScope(authentication) && hasAnyAuthority(authentication, ROLE_CATALOG_AUTHORITIES);
    }

    public boolean canApprove(Authentication authentication) {
        return isPlatformScope(authentication) && hasAnyAuthority(authentication, APPROVE_AUTHORITIES);
    }

    public boolean canReject(Authentication authentication) {
        return isPlatformScope(authentication) && hasAnyAuthority(authentication, REJECT_AUTHORITIES);
    }

    public boolean canCancel(Authentication authentication) {
        return isPlatformScope(authentication) && hasAnyAuthority(authentication, CANCEL_AUTHORITIES);
    }

    private boolean isPlatformScope(Authentication authentication) {
        return authentication != null
            && authentication.isAuthenticated()
            && TenantContext.isPlatformScope();
    }

    private boolean hasAnyAuthority(Authentication authentication, Set<String> requiredAuthorities) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
            .map(grantedAuthority -> grantedAuthority.getAuthority())
            .anyMatch(requiredAuthorities::contains);
    }
}
