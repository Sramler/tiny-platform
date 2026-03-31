package com.tiny.platform.infrastructure.auth.user.service;

import com.tiny.platform.core.oauth.security.LoginFailurePolicy;
import com.tiny.platform.core.oauth.security.AuthUserResolutionService;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.auth.datascope.framework.DataScope;
import com.tiny.platform.infrastructure.auth.datascope.framework.DataScopeContext;
import com.tiny.platform.infrastructure.auth.datascope.framework.ResolvedDataScope;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.auth.org.service.UserUnitService;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.dto.UserCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.user.dto.UserRequestDto;
import com.tiny.platform.infrastructure.auth.user.dto.UserResponseDto;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.role.service.EffectiveRoleResolutionService;
import com.tiny.platform.infrastructure.auth.role.service.RoleAssignmentSyncService;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationMethodRepository;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import com.tiny.platform.infrastructure.tenant.guard.TenantLifecycleGuard;
import com.tiny.platform.infrastructure.tenant.service.TenantQuotaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.data.jpa.domain.Specification;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.HashSet;
import java.util.HashMap;
import java.time.LocalDateTime;

@Service
public class UserServiceImpl implements UserService {

    private static final String ACTIVE = "ACTIVE";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final UserAuthenticationMethodRepository authenticationMethodRepository;
    private final LoginFailurePolicy loginFailurePolicy;
    private final RoleAssignmentSyncService roleAssignmentSyncService;
    private final EffectiveRoleResolutionService effectiveRoleResolutionService;
    private final TenantUserRepository tenantUserRepository;
    private final UserUnitRepository userUnitRepository;
    private final UserUnitService userUnitService;
    private final AuthUserResolutionService authUserResolutionService;
    private final TenantLifecycleGuard tenantLifecycleGuard;
    private final TenantQuotaService tenantQuotaService;

    @Autowired
    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          RoleRepository roleRepository, UserAuthenticationMethodRepository authenticationMethodRepository,
                          LoginFailurePolicy loginFailurePolicy,
                          RoleAssignmentSyncService roleAssignmentSyncService,
                          EffectiveRoleResolutionService effectiveRoleResolutionService,
                          TenantUserRepository tenantUserRepository,
                          UserUnitRepository userUnitRepository,
                          UserUnitService userUnitService,
                          AuthUserResolutionService authUserResolutionService,
                          TenantLifecycleGuard tenantLifecycleGuard,
                          TenantQuotaService tenantQuotaService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
        this.authenticationMethodRepository = authenticationMethodRepository;
        this.loginFailurePolicy = loginFailurePolicy;
        this.roleAssignmentSyncService = roleAssignmentSyncService;
        this.effectiveRoleResolutionService = effectiveRoleResolutionService;
        this.tenantUserRepository = tenantUserRepository;
        this.userUnitRepository = userUnitRepository;
        this.userUnitService = userUnitService;
        this.authUserResolutionService = authUserResolutionService;
        this.tenantLifecycleGuard = tenantLifecycleGuard;
        this.tenantQuotaService = tenantQuotaService;
    }

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           RoleRepository roleRepository, UserAuthenticationMethodRepository authenticationMethodRepository,
                           LoginFailurePolicy loginFailurePolicy,
                           RoleAssignmentSyncService roleAssignmentSyncService,
                           EffectiveRoleResolutionService effectiveRoleResolutionService,
                           TenantUserRepository tenantUserRepository,
                           UserUnitRepository userUnitRepository,
                           UserUnitService userUnitService,
                           AuthUserResolutionService authUserResolutionService,
                           TenantLifecycleGuard tenantLifecycleGuard) {
        this(
            userRepository,
            passwordEncoder,
            roleRepository,
            authenticationMethodRepository,
            loginFailurePolicy,
            roleAssignmentSyncService,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            userUnitService,
            authUserResolutionService,
            tenantLifecycleGuard,
            null
        );
    }

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           RoleRepository roleRepository, UserAuthenticationMethodRepository authenticationMethodRepository,
                           LoginFailurePolicy loginFailurePolicy,
                           RoleAssignmentSyncService roleAssignmentSyncService,
                           EffectiveRoleResolutionService effectiveRoleResolutionService,
                           TenantUserRepository tenantUserRepository,
                           UserUnitRepository userUnitRepository,
                           AuthUserResolutionService authUserResolutionService,
                           TenantLifecycleGuard tenantLifecycleGuard) {
        this(
            userRepository,
            passwordEncoder,
            roleRepository,
            authenticationMethodRepository,
            loginFailurePolicy,
            roleAssignmentSyncService,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            null,
            authUserResolutionService,
            tenantLifecycleGuard,
            null
        );
    }


    @Override
    @DataScope(module = "user")
    public Page<UserResponseDto> users(UserRequestDto query, Pageable pageable) {
        Long tenantId = requireTenantId();
        var visibleUserIds = resolveVisibleUserIdsForRead(tenantId);
        if (visibleUserIds.isEmpty()) {
            return Page.empty(pageable);
        }
        Page<User> users = userRepository.findAll((Specification<User>) (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("id").in(visibleUserIds));

            if (StringUtils.hasText(query.getUsername())) {
                predicates.add(cb.like(root.get("username"), "%" + query.getUsername() + "%"));
            }
            if (StringUtils.hasText(query.getNickname())) {
                predicates.add(cb.like(root.get("nickname"), "%" + query.getNickname() + "%")); // ⚠️ 字段名错误？可能你原本写了 phone？
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);

        // ⚠️ 用 Page.map() 转换为 Page<UserResponseDto>
        return users.map(this::toDto);
    }

    private UserResponseDto toDto(User user) {
        LocalDateTime now = LocalDateTime.now();
        boolean temporarilyLocked = loginFailurePolicy.isTemporarilyLocked(user, now);
        UserResponseDto dto = new UserResponseDto(user.getId(),user.getUsername(),user.getNickname(),user.isEnabled(),
                user.isAccountNonExpired(),user.isAccountNonLocked(),user.isCredentialsNonExpired(),user.getLastLoginAt(),
                user.getFailedLoginCount() != null ? user.getFailedLoginCount() : 0, user.getLastFailedLoginAt(),
                temporarilyLocked,
                temporarilyLocked ? loginFailurePolicy.remainingLockMinutes(user, now) : null);
        // 展示/审计用，来自兼容字段 user.tenant_id；授权与可见性以 tenant_user + activeTenantId 为准
        dto.setRecordTenantId(user.getTenantId());
        return dto;
    }

    @Override
    public Optional<User> findById(Long id) {
        Long tenantId = requireTenantId();
        if (tenantUserRepository.existsByTenantIdAndUserIdAndStatus(tenantId, id, ACTIVE)) {
            return userRepository.findById(id);
        }
        return Optional.empty();
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (TenantContext.isPlatformScope()) {
            return requireAuthUserResolutionService().resolveUserRecordInPlatform(username);
        }
        Long tenantId = requireTenantId();
        return requireAuthUserResolutionService().resolveUserRecordInActiveTenant(username, tenantId);
    }

    @Override
    public User create(User user) {
        tenantLifecycleGuard.assertNotFrozenForWrite("user", "create");
        Long tenantId = requireTenantId();
        if (tenantQuotaService != null) {
            tenantQuotaService.assertCanCreateUsers(tenantId, 1, "创建用户");
        }
        // user.tenant_id 双写已停止（044/045 迁移）；归属以 tenant_user membership 为准
        User saved = userRepository.save(user);
        roleAssignmentSyncService.ensureTenantMembership(saved.getId(), tenantId, true);
        return saved;
    }

    @Override
    public User update(Long id, User user) {
        return findById(id)
            .map(existing -> {
                existing.setUsername(user.getUsername());
                // 不再更新 user.password，密码已迁移到 user_authentication_method 表
                existing.setNickname(user.getNickname());
                existing.setEnabled(user.isEnabled());
                existing.setLastLoginAt(user.getLastLoginAt());
                return userRepository.save(existing);
            })
            .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Override
    public void delete(Long id) {
        tenantLifecycleGuard.assertNotFrozenForWrite("user", "delete");
        findById(id)
            .ifPresent(userRepository::delete);
    }

    @Override
    @Transactional
    public User createFromDto(UserCreateUpdateDto userDto) {
        tenantLifecycleGuard.assertNotFrozenForWrite("user", "create");
        Long tenantId = requireTenantId();
        if (tenantQuotaService != null) {
            tenantQuotaService.assertCanCreateUsers(tenantId, 1, "创建用户");
        }
        // 检查用户名是否已存在
        if (findByUsername(userDto.getUsername()).isPresent()) {
            throw new BusinessException(
                ErrorCode.RESOURCE_ALREADY_EXISTS,
                "用户名已存在: " + userDto.getUsername()
            );
        }
        
        User user = new User();
        user.setUsername(userDto.getUsername());
        user.setNickname(userDto.getNickname());
        user.setEmail(trimToNull(userDto.getEmail()));
        user.setPhone(trimToNull(userDto.getPhone()));
        user.setEnabled(userDto.getEnabled());
        user.setAccountNonExpired(userDto.getAccountNonExpired());
        user.setAccountNonLocked(userDto.getAccountNonLocked());
        user.setCredentialsNonExpired(userDto.getCredentialsNonExpired());
        
        // 处理角色
        if (userDto.getRoleIds() != null && !userDto.getRoleIds().isEmpty()) {
            var roles = roleRepository.findByIdInAndTenantId(userDto.getRoleIds(), tenantId);
            if (roles.size() != userDto.getRoleIds().size()) {
                throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "部分角色不存在"
                );
            }
        }
        
        // 保存用户，获取用户ID
        user = userRepository.save(user);
        roleAssignmentSyncService.ensureTenantMembership(user.getId(), tenantId, true);

        if (userDto.getRoleIds() != null && !userDto.getRoleIds().isEmpty()) {
            roleAssignmentSyncService.replaceUserTenantRoleAssignments(user.getId(), tenantId, userDto.getRoleIds());
        }

        syncUserUnitsIfPresent(tenantId, user.getId(), userDto);
        
        // 创建认证方法（将密码存储在 user_authentication_method 表中）
        createPasswordAuthenticationMethod(user.getId(), tenantId, userDto.getPassword());
        
        return user;
    }

    @Override
    @Transactional
    public User updateFromDto(UserCreateUpdateDto userDto) {
        tenantLifecycleGuard.assertNotFrozenForWrite("user", "update");
        Long tenantId = requireTenantId();
        User existingUser = findById(userDto.getId())
            .orElseThrow(() -> new BusinessException(
                ErrorCode.NOT_FOUND,
                "用户不存在: " + userDto.getId()
            ));
        
        // 检查用户名是否已被其他用户使用
        Optional<User> userWithSameUsername = findByUsername(userDto.getUsername());
        if (userWithSameUsername.isPresent() && !userWithSameUsername.get().getId().equals(userDto.getId())) {
            throw new BusinessException(
                ErrorCode.RESOURCE_ALREADY_EXISTS,
                "用户名已被其他用户使用: " + userDto.getUsername()
            );
        }
        
        // 更新基本信息
        existingUser.setUsername(userDto.getUsername());
        existingUser.setNickname(userDto.getNickname());
        // 更新邮箱（允许设置为null来清空）
        existingUser.setEmail(trimToNull(userDto.getEmail()));
        // 更新手机号（允许设置为null来清空）
        existingUser.setPhone(trimToNull(userDto.getPhone()));
        existingUser.setEnabled(userDto.getEnabled());
        existingUser.setAccountNonExpired(userDto.getAccountNonExpired());
        boolean unlockingUser = !existingUser.isAccountNonLocked() && Boolean.TRUE.equals(userDto.getAccountNonLocked());
        existingUser.setAccountNonLocked(userDto.getAccountNonLocked());
        existingUser.setCredentialsNonExpired(userDto.getCredentialsNonExpired());
        if (unlockingUser) {
            existingUser.setFailedLoginCount(0);
            existingUser.setLastFailedLoginAt(null);
        }
        
        // 如果提供了新密码，则更新认证方法表中的密码
        if (userDto.needUpdatePassword()) {
            updatePasswordAuthenticationMethod(userDto.getId(), tenantId, userDto.getPassword());
        }
        
        // 处理角色
        if (userDto.getRoleIds() != null) {
            var roles = roleRepository.findByIdInAndTenantId(userDto.getRoleIds(), tenantId);
            if (roles.size() != userDto.getRoleIds().size()) {
                throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "部分角色不存在"
                );
            }
            roleAssignmentSyncService.replaceUserTenantRoleAssignments(existingUser.getId(), tenantId, userDto.getRoleIds());
        }

        User savedUser = userRepository.save(existingUser);
        syncUserUnitsIfPresent(tenantId, existingUser.getId(), userDto);
        return savedUser;
    }
    
    /**
     * 创建密码认证方法
     * @param userId 用户ID
     * @param plainPassword 明文密码
     */
    private void createPasswordAuthenticationMethod(Long userId, Long tenantId, String plainPassword) {
        // 加密密码（DelegatingPasswordEncoder 会自动添加 {bcrypt} 前缀）
        String encodedPassword = passwordEncoder.encode(plainPassword);
        
        // 创建认证配置
        Map<String, Object> config = new HashMap<>();
        config.put("password", encodedPassword);
        
        // 创建认证方法
        UserAuthenticationMethod method = new UserAuthenticationMethod();
        method.setUserId(userId);
        method.setTenantId(tenantId);
        method.setAuthenticationProvider("LOCAL");
        method.setAuthenticationType("PASSWORD");
        method.setAuthenticationConfiguration(config);
        method.setIsPrimaryMethod(true);
        method.setIsMethodEnabled(true);
        method.setAuthenticationPriority(0);
        method.setCreatedAt(LocalDateTime.now());
        method.setUpdatedAt(LocalDateTime.now());
        
        // 保存认证方法
        authenticationMethodRepository.save(method);
    }
    
    /**
     * 更新密码认证方法
     * @param userId 用户ID
     * @param plainPassword 新明文密码
     */
    private void updatePasswordAuthenticationMethod(Long userId, Long tenantId, String plainPassword) {
        // 查找现有的认证方法
        Optional<UserAuthenticationMethod> existingMethod = authenticationMethodRepository
                .findEffectiveAuthenticationMethod(userId, tenantId, "LOCAL", "PASSWORD");
        
        if (existingMethod.isPresent()) {
            // 更新现有认证方法
            UserAuthenticationMethod method = existingMethod.get();
            Map<String, Object> config = method.getAuthenticationConfiguration();
            if (config == null) {
                config = new HashMap<>();
            }
            // 加密新密码（DelegatingPasswordEncoder 会自动添加 {bcrypt} 前缀）
            String encodedPassword = passwordEncoder.encode(plainPassword);
            config.put("password", encodedPassword);
            method.setAuthenticationConfiguration(config);
            method.setUpdatedAt(LocalDateTime.now());
            authenticationMethodRepository.save(method);
        } else {
            // 如果不存在，则创建新的认证方法
            createPasswordAuthenticationMethod(userId, tenantId, plainPassword);
        }
    }

    @Override
    @Transactional
    public void batchEnable(List<Long> ids) {
        tenantLifecycleGuard.assertNotFrozenForWrite("user", "batchEnable");
        // 批量启用用户，使用事务确保一致性
        List<User> users = requireUsersInTenant(ids);
        
        for (User user : users) {
            user.setEnabled(true);
        }
        userRepository.saveAll(users);
    }
    
    @Override
    @Transactional
    public void batchDisable(List<Long> ids) {
        tenantLifecycleGuard.assertNotFrozenForWrite("user", "batchDisable");
        // 批量禁用用户，使用事务确保一致性
        List<User> users = requireUsersInTenant(ids);
        
        for (User user : users) {
            user.setEnabled(false);
        }
        userRepository.saveAll(users);
    }
    
    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        tenantLifecycleGuard.assertNotFrozenForWrite("user", "batchDelete");
        // 批量删除用户，使用事务确保一致性
        List<User> users = requireUsersInTenant(ids);
        
        userRepository.deleteAll(users);
    }

    @Override
    @Transactional
    public void updateUserRoles(Long userId, List<Long> roleIds) {
        updateUserRoles(userId, "TENANT", null, roleIds);
    }

    @Override
    @Transactional
    public void updateUserRoles(Long userId, String scopeType, Long scopeId, List<Long> roleIds) {
        tenantLifecycleGuard.assertNotFrozenForWrite("user", "updateUserRoles");
        Long tenantId = requireTenantId();
        User user = findById(userId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.NOT_FOUND,
                "用户不存在: " + userId
            ));
        List<Role> roles = roleRepository.findByIdInAndTenantId(roleIds, tenantId);
        if (roles.size() != roleIds.size()) {
            throw new BusinessException(
                ErrorCode.NOT_FOUND,
                "部分角色不存在"
            );
        }
        roleAssignmentSyncService.replaceUserScopedRoleAssignments(user.getId(), tenantId, scopeType, scopeId, roleIds);
    }

    @Override
    public List<Long> getRoleIdsByUserId(Long userId) {
        Long tenantId = requireTenantId();
        if (findById(userId).isEmpty()) {
            throw new BusinessException(
                ErrorCode.NOT_FOUND,
                "用户不存在: " + userId
            );
        }

        return resolveEffectiveRoleIdsRespectingActiveScope(userId, tenantId);
    }

    /**
     * 与 {@link TenantContext} active scope 对齐的有效角色（工作流/服务侧展示与 TENANT 默认兼容）。
     */
    private List<Long> resolveEffectiveRoleIdsRespectingActiveScope(Long userId, Long tenantId) {
        if (TenantContext.isPlatformScope()) {
            return effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(userId, tenantId);
        }
        String ast = TenantContext.getActiveScopeType();
        Long asid = TenantContext.getActiveScopeId();
        if ((TenantContextContract.SCOPE_TYPE_ORG.equals(ast) || TenantContextContract.SCOPE_TYPE_DEPT.equals(ast))
            && asid != null && asid > 0) {
            return effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(userId, tenantId, ast, asid);
        }
        return effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(userId, tenantId);
    }

    @Override
    public List<Long> getDirectRoleIdsByUserId(Long userId, String scopeType, Long scopeId) {
        Long tenantId = requireTenantId();
        if (findById(userId).isEmpty()) {
            throw new BusinessException(
                ErrorCode.NOT_FOUND,
                "用户不存在: " + userId
            );
        }

        return roleAssignmentSyncService.findActiveRoleIdsForUserInScope(userId, tenantId, scopeType, scopeId);
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getActiveTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.MISSING_PARAMETER, "缺少租户信息");
        }
        return tenantId;
    }

    private AuthUserResolutionService requireAuthUserResolutionService() {
        if (authUserResolutionService == null) {
            throw new IllegalStateException("AuthUserResolutionService 未配置");
        }
        return authUserResolutionService;
    }

    private List<User> requireUsersInTenant(List<Long> ids) {
        Long tenantId = requireTenantId();
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        var requestedIds = new LinkedHashSet<>(ids);
        var visibleIds = resolveVisibleUserIdsForTenant(tenantId, requestedIds);
        if (visibleIds.size() != requestedIds.size()) {
            throw new BusinessException(
                ErrorCode.NOT_FOUND,
                "部分用户不存在"
            );
        }
        List<User> users = userRepository.findAllById(visibleIds).stream()
                .filter(user -> visibleIds.contains(user.getId()))
                .toList();
        if (users.size() != requestedIds.size()) {
            throw new BusinessException(
                ErrorCode.NOT_FOUND,
                "部分用户不存在"
            );
        }
        return users;
    }

    private LinkedHashSet<Long> resolveVisibleUserIdsForTenant(Long tenantId) {
        return new LinkedHashSet<>(tenantUserRepository.findUserIdsByTenantIdAndStatus(tenantId, ACTIVE));
    }

    private LinkedHashSet<Long> resolveVisibleUserIdsForTenant(Long tenantId, LinkedHashSet<Long> candidateIds) {
        return new LinkedHashSet<>(tenantUserRepository.findUserIdsByTenantIdAndUserIdInAndStatus(tenantId, candidateIds, ACTIVE));
    }

    private LinkedHashSet<Long> resolveVisibleUserIdsForRead(Long tenantId) {
        LinkedHashSet<Long> tenantVisibleUserIds = resolveVisibleUserIdsForTenant(tenantId);
        ResolvedDataScope scope = DataScopeContext.get();
        if (scope == null || scope.isUnrestricted()) {
            return tenantVisibleUserIds;
        }

        LinkedHashSet<Long> scopedUserIds = new LinkedHashSet<>();
        Long currentUserId = extractCurrentUserId();
        if (scope.isSelfOnly() && currentUserId != null) {
            scopedUserIds.add(currentUserId);
        }
        scopedUserIds.addAll(scope.getVisibleUserIds());

        if (!scope.getVisibleUnitIds().isEmpty()) {
            scopedUserIds.addAll(userUnitRepository.findUserIdsByTenantIdAndUnitIdInAndStatus(
                tenantId,
                scope.getVisibleUnitIds(),
                ACTIVE
            ));
        }

        if (scopedUserIds.isEmpty()) {
            return new LinkedHashSet<>();
        }

        scopedUserIds.retainAll(tenantVisibleUserIds);
        return scopedUserIds;
    }

    private Long extractCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return securityUser.getUserId();
        }
        Object details = authentication.getDetails();
        if (details instanceof SecurityUser securityUser) {
            return securityUser.getUserId();
        }
        return null;
    }

    private void syncUserUnitsIfPresent(Long tenantId, Long userId, UserCreateUpdateDto userDto) {
        if (userDto.getUnitIds() == null && userDto.getPrimaryUnitId() == null) {
            return;
        }
        if (userUnitService == null) {
            throw new IllegalStateException("UserUnitService 未配置");
        }
        userUnitService.replaceUserUnits(tenantId, userId, userDto.getUnitIds(), userDto.getPrimaryUnitId());
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
