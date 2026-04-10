package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.tenant.ActiveTenantResponseSupport;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final AuthUserResolutionService authUserResolutionService;
    private final PermissionVersionService permissionVersionService;
    private final SecurityUserAuthorityService securityUserAuthorityService;

    @Autowired
    public UserDetailsServiceImpl(AuthUserResolutionService authUserResolutionService,
                                  PermissionVersionService permissionVersionService,
                                  SecurityUserAuthorityService securityUserAuthorityService) {
        this.authUserResolutionService = java.util.Objects.requireNonNull(authUserResolutionService, "AuthUserResolutionService 未配置");
        this.permissionVersionService = permissionVersionService;
        this.securityUserAuthorityService = java.util.Objects.requireNonNull(securityUserAuthorityService, "SecurityUserAuthorityService 未配置");
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Long activeTenantId = resolveActiveTenantId();
        String activeScopeType = resolveActiveScopeType();
        Long activeScopeId = resolveActiveScopeId(activeScopeType, activeTenantId);
        if (!TenantContextContract.SCOPE_TYPE_PLATFORM.equalsIgnoreCase(activeScopeType)
            && activeTenantId == null) {
            throw new UsernameNotFoundException("缺少租户信息");
        }

        AuthResolvedUser resolvedUser;
        if (TenantContextContract.SCOPE_TYPE_PLATFORM.equalsIgnoreCase(activeScopeType)) {
            resolvedUser = requireAuthUserResolutionService()
                .resolveUserInScope(username, activeTenantId, activeScopeType)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
        } else {
            resolvedUser = requireAuthUserResolutionService()
                .resolveUserInActiveTenant(username, activeTenantId)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
        }

        return new SecurityUser(
            resolvedUser.user(),
            "",
            TenantContextContract.SCOPE_TYPE_PLATFORM.equalsIgnoreCase(activeScopeType) ? null : resolvedUser.activeTenantId(),
            resolveAuthorities(
                resolvedUser.user().getId(),
                TenantContextContract.SCOPE_TYPE_PLATFORM.equalsIgnoreCase(activeScopeType) ? null : resolvedUser.activeTenantId(),
                activeScopeType,
                activeScopeId,
                resolvedUser.effectiveRoles()
            ),
            extractRoleCodes(resolvedUser.effectiveRoles()),
            resolvePermissionsVersion(resolvedUser.user().getId(), resolvedUser.activeTenantId(), activeScopeType, activeScopeId)
        );
    }

    private Set<String> extractRoleCodes(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (Role role : roles) {
            if (role == null || role.getCode() == null) {
                continue;
            }
            String code = role.getCode().trim();
            if (!code.isEmpty()) {
                values.add(code);
            }
        }
        return Set.copyOf(values);
    }

    private String resolvePermissionsVersion(Long userId, Long activeTenantId, String activeScopeType, Long activeScopeId) {
        if (permissionVersionService == null || userId == null) {
            return null;
        }
        return permissionVersionService.resolvePermissionsVersion(userId, activeTenantId, activeScopeType, activeScopeId);
    }

    private java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> resolveAuthorities(Long userId,
                                                                                                                    Long activeTenantId,
                                                                                                                    String activeScopeType,
                                                                                                                    Long activeScopeId,
                                                                                                                    java.util.Set<Role> effectiveRoles) {
        return securityUserAuthorityService.buildAuthorities(
            userId,
            activeTenantId,
            activeScopeType,
            activeScopeId,
            effectiveRoles
        );
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

    private String resolveActiveScopeType() {
        String scopeType = ActiveTenantResponseSupport.resolveActiveScopeTypeFromRequestContext();
        if (scopeType == null || scopeType.isBlank()) {
            return TenantContextContract.SCOPE_TYPE_TENANT;
        }
        return scopeType.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private Long resolveActiveScopeId(String activeScopeType, Long activeTenantId) {
        Long scopeId = ActiveTenantResponseSupport.resolveActiveScopeIdFromRequestContext();
        if (scopeId != null && scopeId > 0) {
            return scopeId;
        }
        if (TenantContextContract.SCOPE_TYPE_PLATFORM.equalsIgnoreCase(activeScopeType)) {
            return null;
        }
        return activeTenantId;
    }
}
