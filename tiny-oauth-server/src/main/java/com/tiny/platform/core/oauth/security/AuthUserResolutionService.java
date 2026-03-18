package com.tiny.platform.core.oauth.security;

import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.service.EffectiveRoleResolutionService;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
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
        // 仅使用 role_assignment 解析有效角色，不再从 user_role 回退（见 AUTHORIZATION_LEGACY_REMOVAL_PLAN）
        Set<Role> effectiveRoles = resolveEffectiveRoles(user.getId(), activeTenantId);

        return Optional.of(new AuthResolvedUser(user, activeTenantId, effectiveRoles));
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

        if (tenantRepository.isTenantFrozen(activeTenantId)) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "[auth-resolve] 跳过用户解析：租户已冻结 activeTenantId={}",
                        activeTenantId
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
