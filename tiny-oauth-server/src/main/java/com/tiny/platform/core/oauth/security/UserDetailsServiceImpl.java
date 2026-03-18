package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.tenant.ActiveTenantResponseSupport;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.service.EffectiveRoleResolutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final AuthUserResolutionService authUserResolutionService;
    private final EffectiveRoleResolutionService effectiveRoleResolutionService;
    private final PermissionVersionService permissionVersionService;

    @Autowired
    public UserDetailsServiceImpl(AuthUserResolutionService authUserResolutionService,
                                  EffectiveRoleResolutionService effectiveRoleResolutionService,
                                  PermissionVersionService permissionVersionService) {
        this.authUserResolutionService = authUserResolutionService;
        this.effectiveRoleResolutionService = effectiveRoleResolutionService;
        this.permissionVersionService = permissionVersionService;
    }

    public UserDetailsServiceImpl(AuthUserResolutionService authUserResolutionService) {
        this(authUserResolutionService, null, null);
    }

    public UserDetailsServiceImpl(AuthUserResolutionService authUserResolutionService,
                                  PermissionVersionService permissionVersionService) {
        this(authUserResolutionService, null, permissionVersionService);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Long activeTenantId = resolveActiveTenantId();
        if (activeTenantId == null) {
            throw new UsernameNotFoundException("缺少租户信息");
        }

        AuthResolvedUser resolvedUser = requireAuthUserResolutionService()
            .resolveUserInActiveTenant(username, activeTenantId)
            .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

        return new SecurityUser(
            resolvedUser.user(),
            "",
            resolvedUser.activeTenantId(),
            resolvedUser.effectiveRoles(),
            resolvePermissionsVersion(resolvedUser.user().getId(), resolvedUser.activeTenantId())
        );
    }

    private String resolvePermissionsVersion(Long userId, Long activeTenantId) {
        if (permissionVersionService == null || userId == null || activeTenantId == null || activeTenantId <= 0) {
            return null;
        }
        return permissionVersionService.resolvePermissionsVersion(userId, activeTenantId);
    }

    private java.util.Set<Role> resolveEffectiveRoles(Long userId, Long activeTenantId) {
        if (effectiveRoleResolutionService == null || userId == null || activeTenantId == null || activeTenantId <= 0) {
            return java.util.Set.of();
        }
        return effectiveRoleResolutionService.findEffectiveRolesForUserInTenant(userId, activeTenantId);
    }

    private AuthUserResolutionService requireAuthUserResolutionService() {
        if (authUserResolutionService == null) {
            throw new IllegalStateException("AuthUserResolutionService 未配置");
        }
        return authUserResolutionService;
    }

    private Long resolveActiveTenantId() {
        return ActiveTenantResponseSupport.resolveActiveTenantIdFromRequestContext();
    }
}
