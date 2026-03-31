package com.tiny.platform.core.oauth.security;

import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.service.EffectiveRoleResolutionService;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class AuthUserResolutionService {

    private static final Logger log = LoggerFactory.getLogger(AuthUserResolutionService.class);
    private static final String ACTIVE = "ACTIVE";

    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;
    private final EffectiveRoleResolutionService effectiveRoleResolutionService;
    private final TenantRepository tenantRepository;

    public AuthUserResolutionService(UserRepository userRepository,
                                     TenantUserRepository tenantUserRepository,
                                     EffectiveRoleResolutionService effectiveRoleResolutionService,
                                     TenantRepository tenantRepository) {
        this.userRepository = userRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.effectiveRoleResolutionService = effectiveRoleResolutionService;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public Optional<AuthResolvedUser> resolveUserInActiveTenant(String username, Long activeTenantId) {
        Optional<User> resolvedUser = resolveUserRecordInActiveTenant(username, activeTenantId);
        if (resolvedUser.isEmpty()) {
            return Optional.empty();
        }

        User user = resolvedUser.get();
        // user_role 已移除（043/047 迁移），角色解析仅通过 role_assignment
        Set<Role> effectiveRoles = resolveEffectiveRoles(user.getId(), activeTenantId);

        return Optional.of(new AuthResolvedUser(user, activeTenantId, effectiveRoles));
    }

    @Transactional(readOnly = true)
    public Optional<AuthResolvedUser> resolveUserInScope(String username, Long activeTenantId, String activeScopeType) {
        String normalizedScopeType = activeScopeType == null
            ? TenantContextContract.SCOPE_TYPE_TENANT
            : activeScopeType.trim().toUpperCase(java.util.Locale.ROOT);
        if (TenantContextContract.SCOPE_TYPE_PLATFORM.equals(normalizedScopeType)) {
            Optional<User> resolvedUser = resolveUserRecordInPlatform(username);
            if (resolvedUser.isEmpty()) {
                return Optional.empty();
            }
            User user = resolvedUser.get();
            Set<Role> effectiveRoles = effectiveRoleResolutionService.findEffectiveRolesForUserInPlatform(user.getId());
            return Optional.of(new AuthResolvedUser(user, null, effectiveRoles));
        }
        return resolveUserInActiveTenant(username, activeTenantId);
    }

    @Transactional(readOnly = true)
    public Optional<User> resolveUserRecordInActiveTenant(String username, Long activeTenantId) {
        if (username == null || username.isBlank() || activeTenantId == null || activeTenantId <= 0) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "[auth-resolve] 跳过用户解析：参数非法 username='{}', activeTenantId={}",
                        username,
                        activeTenantId
                );
            }
            return Optional.empty();
        }

        Optional<String> blockedLifecycleStatus = tenantRepository.findLoginBlockedLifecycleStatus(activeTenantId);
        if (blockedLifecycleStatus == null) {
            blockedLifecycleStatus = Optional.empty();
        }
        if (blockedLifecycleStatus.isPresent()) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "[auth-resolve] 跳过用户解析：租户不可登录 activeTenantId={}, lifecycleStatus={}",
                        activeTenantId,
                        blockedLifecycleStatus.get()
                );
            }
            return Optional.empty();
        }

        List<User> candidates = userRepository.findAllByUsername(username.trim());
        if (candidates.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "[auth-resolve] 未找到用户名对应的用户记录 username='{}', activeTenantId={}",
                        username,
                        activeTenantId
                );
            }
            return Optional.empty();
        }

        List<User> membershipMatches = candidates.stream()
            .filter(user -> tenantUserRepository.existsByTenantIdAndUserIdAndStatus(activeTenantId, user.getId(), ACTIVE))
            .toList();

        User resolvedUser = resolveUserCandidate(username, activeTenantId, membershipMatches);
        if (resolvedUser == null) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "[auth-resolve] 在活动租户下未能解析唯一用户 username='{}', activeTenantId={}, membershipMatches={}",
                        username,
                        activeTenantId,
                        membershipMatches.size()
                );
            }
            return Optional.empty();
        }
        if (log.isDebugEnabled()) {
            log.debug(
                    "[auth-resolve] 在活动租户下解析到用户 username='{}', activeTenantId={}, userId={}",
                    username,
                    activeTenantId,
                    resolvedUser.getId()
            );
        }
        return Optional.of(resolvedUser);
    }

    @Transactional(readOnly = true)
    public Optional<User> resolveUserRecordInPlatform(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        List<User> candidates = userRepository.findAllByUsername(username.trim());
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() > 1) {
            log.error("用户名 {} 在 PLATFORM 作用域下命中多条用户记录，拒绝解析", username);
            return Optional.empty();
        }
        User resolved = candidates.get(0);
        if (effectiveRoleResolutionService.findEffectiveRoleIdsForUserInPlatform(resolved.getId()).isEmpty()) {
            log.warn("用户 {} 不具备 PLATFORM 作用域赋权，拒绝平台登录", username);
            return Optional.empty();
        }
        return Optional.of(resolved);
    }

    private User resolveUserCandidate(String username, Long activeTenantId, List<User> membershipMatches) {
        if (membershipMatches.isEmpty()) {
            return null;
        }

        if (membershipMatches.size() == 1) {
            return membershipMatches.get(0);
        }

        log.error("用户名 {} 在 activeTenantId={} 下存在多条 membership 命中，拒绝继续解析",
            username, activeTenantId);
        return null;
    }

    private Set<Role> resolveEffectiveRoles(Long userId, Long activeTenantId) {
        return effectiveRoleResolutionService.findEffectiveRolesForUserInTenant(userId, activeTenantId);
    }
}
