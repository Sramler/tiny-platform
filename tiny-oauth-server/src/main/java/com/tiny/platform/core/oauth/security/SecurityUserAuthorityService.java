package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.config.PermissionRefactorObservabilityProperties;
import com.tiny.platform.core.oauth.tenant.ActiveTenantResponseSupport;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Service
public class SecurityUserAuthorityService {

    private static final Logger log = LoggerFactory.getLogger(SecurityUserAuthorityService.class);

    private final PermissionAuthorityReadRepository permissionAuthorityReadRepository;
    private final PermissionRefactorObservabilityProperties observabilityProperties;

    public SecurityUserAuthorityService(PermissionAuthorityReadRepository permissionAuthorityReadRepository,
                                        PermissionRefactorObservabilityProperties observabilityProperties) {
        this.permissionAuthorityReadRepository = permissionAuthorityReadRepository;
        this.observabilityProperties = observabilityProperties;
    }

    public Collection<? extends GrantedAuthority> buildAuthorities(Long userId,
                                                                   Long tenantId,
                                                                   String scopeType,
                                                                   Long scopeId,
                                                                   Set<Role> roles) {
        Set<Role> effectiveRoles = roles != null ? roles : Set.of();
        Set<String> roleCodeAuthorities = buildRoleCodeAuthorities(effectiveRoles);
        Set<String> newPermissionCodeSet = loadNewPermissionCodeSet(effectiveRoles, tenantId);
        DenyFilterResult denyFilterResult = applyFinalDenyFilter(newPermissionCodeSet, tenantId);

        logAuthorityDiffIfNeeded(userId, tenantId, scopeType, scopeId, effectiveRoles, denyFilterResult);

        LinkedHashSet<GrantedAuthority> finalAuthorities = new LinkedHashSet<>();
        roleCodeAuthorities.stream().map(SimpleGrantedAuthority::new).forEach(finalAuthorities::add);
        buildPermissionAuthorities(denyFilterResult.allowedPermissionCodes()).stream()
                .map(SimpleGrantedAuthority::new)
                .forEach(finalAuthorities::add);
        return finalAuthorities;
    }

    Set<String> buildRoleCodeAuthorities(Set<Role> roles) {
        LinkedHashSet<String> authorities = new LinkedHashSet<>();
        for (Role role : roles) {
            addAuthorityValue(authorities, role != null ? role.getCode() : null);
        }
        return authorities;
    }

    Set<String> loadNewPermissionCodeSet(Set<Role> roles, Long tenantId) {
        LinkedHashSet<Long> roleIds = new LinkedHashSet<>();
        for (Role role : roles) {
            if (role == null || role.getId() == null) {
                continue;
            }
            roleIds.add(role.getId());
        }
        return new LinkedHashSet<>(permissionAuthorityReadRepository.findPermissionCodesByRoleIds(roleIds, tenantId));
    }

    DenyFilterResult applyFinalDenyFilter(Set<String> candidatePermissionCodes, Long tenantId) {
        if (candidatePermissionCodes.isEmpty()) {
            return new DenyFilterResult(Set.of(), Set.of(), Set.of());
        }
        Map<String, Boolean> enabledFlags = permissionAuthorityReadRepository
                .findEnabledFlagsByPermissionCodes(candidatePermissionCodes, tenantId);
        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        LinkedHashSet<String> disabled = new LinkedHashSet<>();
        LinkedHashSet<String> unknown = new LinkedHashSet<>();
        for (String code : candidatePermissionCodes) {
            Boolean enabled = enabledFlags.get(code);
            if (enabled == null) {
                unknown.add(code);
                continue;
            }
            if (enabled) {
                allowed.add(code);
            } else {
                disabled.add(code);
            }
        }
        return new DenyFilterResult(allowed, disabled, unknown);
    }

    Set<String> buildPermissionAuthorities(Set<String> filteredPermissionCodeSet) {
        return new LinkedHashSet<>(filteredPermissionCodeSet);
    }

    void logAuthorityDiffIfNeeded(Long userId,
                                  Long tenantId,
                                  String scopeType,
                                  Long scopeId,
                                  Set<Role> roles,
                                  DenyFilterResult denyFilterResult) {
        if (!observabilityProperties.isAuthorityDiffLogEnabled()
                || !observabilityProperties.isContextInGrayWindow(tenantId, scopeType)
                || !observabilityProperties.shouldSample()) {
            return;
        }
        String signalSource = ActiveTenantResponseSupport.resolveSignalSourceFromRequestContext();
        LinkedHashSet<Long> roleIds = new LinkedHashSet<>();
        for (Role role : roles) {
            if (role != null && role.getId() != null) {
                roleIds.add(role.getId());
            }
        }
        if (!denyFilterResult.disabledPermissionCodes().isEmpty()) {
            log.info(
                    "authority deny disabled permissions, signalSource={}, tenantId={}, userId={}, scopeType={}, scopeId={}, roleIds={}, deniedPermissionCodes={}, denyReason=DISABLED",
                    signalSource,
                    tenantId,
                    userId,
                    scopeType,
                    scopeId,
                    roleIds,
                    denyFilterResult.disabledPermissionCodes()
            );
        }
        if (observabilityProperties.isFailClosedStrictEnabled() && !denyFilterResult.unknownPermissionCodes().isEmpty()) {
            log.info(
                    "authority deny unknown permissions, signalSource={}, tenantId={}, userId={}, scopeType={}, scopeId={}, roleIds={}, deniedPermissionCodes={}, denyReason=UNKNOWN",
                    signalSource,
                    tenantId,
                    userId,
                    scopeType,
                    scopeId,
                    roleIds,
                    denyFilterResult.unknownPermissionCodes()
            );
        }
    }

    private void addAuthorityValue(Set<String> values, String value) {
        if (value == null) {
            return;
        }
        String normalized = value.trim();
        if (!normalized.isEmpty()) {
            values.add(normalized);
        }
    }

    record DenyFilterResult(Set<String> allowedPermissionCodes, Set<String> disabledPermissionCodes, Set<String> unknownPermissionCodes) {
    }
}
