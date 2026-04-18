package com.tiny.platform.infrastructure.auth.user.service;

import com.tiny.platform.infrastructure.auth.user.domain.PlatformUserProfile;
import com.tiny.platform.infrastructure.auth.user.dto.PlatformUserManagementDtos.PlatformUserCreateDto;
import com.tiny.platform.infrastructure.auth.user.dto.PlatformUserManagementDtos.PlatformUserDetailDto;
import com.tiny.platform.infrastructure.auth.user.dto.PlatformUserManagementDtos.PlatformUserListItemDto;
import com.tiny.platform.infrastructure.auth.user.dto.PlatformUserManagementDtos.PlatformUserRoleDto;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.role.service.RoleAssignmentSyncService;
import com.tiny.platform.infrastructure.auth.user.repository.PlatformUserDetailProjection;
import com.tiny.platform.infrastructure.auth.user.repository.PlatformUserListProjection;
import com.tiny.platform.infrastructure.auth.user.repository.PlatformUserProfileRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tiny.platform.infrastructure.auth.role.domain.PlatformRoleGovernanceCodes.APPROVAL_MODE_NONE;

@Service
public class PlatformUserManagementServiceImpl implements PlatformUserManagementService {

    private final PlatformUserProfileRepository platformUserProfileRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoleAssignmentSyncService roleAssignmentSyncService;

    public PlatformUserManagementServiceImpl(PlatformUserProfileRepository platformUserProfileRepository,
                                             UserRepository userRepository,
                                             RoleRepository roleRepository,
                                             RoleAssignmentSyncService roleAssignmentSyncService) {
        this.platformUserProfileRepository = platformUserProfileRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.roleAssignmentSyncService = roleAssignmentSyncService;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PlatformUserListItemDto> list(String keyword, Boolean enabled, String status, Pageable pageable) {
        String normalizedKeyword = normalize(keyword);
        String normalizedStatus = normalizeStatus(status, false);
        return platformUserProfileRepository.findPage(normalizedKeyword, enabled, normalizedStatus, pageable)
            .map(this::toListItemDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PlatformUserDetailDto> get(Long userId) {
        if (userId == null || userId <= 0) {
            return Optional.empty();
        }
        return platformUserProfileRepository.findDetailByUserId(userId)
            .map(this::toDetailDto);
    }

    @Override
    @Transactional
    public PlatformUserDetailDto create(PlatformUserCreateDto request) {
        if (request == null || request.userId() == null || request.userId() <= 0) {
            throw BusinessException.validationError("请求体必须提供合法 userId");
        }
        long requestUserId = request.userId();
        String normalizedStatus = normalizeStatus(request.status(), true);
        if (platformUserProfileRepository.existsByUserId(requestUserId)) {
            throw BusinessException.alreadyExists("平台用户档案已存在");
        }
        var user = userRepository.findById(requestUserId)
            .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        Long rawUserId = user.getId();
        if (rawUserId == null) {
            throw new IllegalStateException("平台用户 userId 不能为空");
        }
        long createdUserId = rawUserId;

        PlatformUserProfile profile = new PlatformUserProfile();
        profile.setUserId(createdUserId);
        profile.setStatus(normalizedStatus);
        String displayName = normalize(request.displayName());
        if (displayName == null) {
            displayName = normalize(user.getNickname());
        }
        profile.setDisplayName(displayName);
        platformUserProfileRepository.saveAndFlush(profile);

        return get(createdUserId).orElseThrow(() -> new IllegalStateException("平台用户档案创建后读取失败"));
    }

    @Override
    @Transactional
    public boolean updateStatus(Long userId, String status) {
        if (userId == null || userId <= 0) {
            return false;
        }
        return platformUserProfileRepository.findByUserId(userId)
            .map(profile -> {
                profile.setStatus(normalizeStatus(status, true));
                platformUserProfileRepository.save(profile);
                return true;
            })
            .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlatformUserRoleDto> getRoles(Long userId) {
        ensurePlatformUserExists(userId);
        return loadPlatformRoles(userId);
    }

    @Override
    @Transactional
    public List<PlatformUserRoleDto> replaceRoles(Long userId, List<Long> roleIds) {
        ensurePlatformUserExists(userId);
        List<Long> normalizedRoleIds = validateAndNormalizeRoleIds(roleIds);
        assertAllPlatformRoles(normalizedRoleIds);
        assertDirectReplaceDoesNotTouchApprovalBoundRoles(userId, normalizedRoleIds);
        roleAssignmentSyncService.replaceUserPlatformRoleAssignments(userId, normalizedRoleIds);
        return loadPlatformRoles(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPlatformUserActive(Long userId) {
        if (userId == null || userId <= 0) {
            return false;
        }
        return platformUserProfileRepository.existsByUserIdAndStatus(userId, PlatformUserProfile.STATUS_ACTIVE);
    }

    private PlatformUserListItemDto toListItemDto(PlatformUserListProjection row) {
        return new PlatformUserListItemDto(
            row.getUserId(),
            row.getUsername(),
            row.getNickname(),
            row.getDisplayName(),
            toBoolean(row.getUserEnabled()),
            row.getPlatformStatus(),
            toBoolean(row.getHasPlatformRoleAssignment()),
            row.getUpdatedAt()
        );
    }

    private PlatformUserDetailDto toDetailDto(PlatformUserDetailProjection row) {
        return new PlatformUserDetailDto(
            row.getUserId(),
            row.getUsername(),
            row.getNickname(),
            row.getDisplayName(),
            row.getEmail(),
            row.getPhone(),
            toBoolean(row.getUserEnabled()),
            toBoolean(row.getAccountNonExpired()),
            toBoolean(row.getAccountNonLocked()),
            toBoolean(row.getCredentialsNonExpired()),
            row.getPlatformStatus(),
            toBoolean(row.getHasPlatformRoleAssignment()),
            row.getLastLoginAt(),
            row.getCreatedAt(),
            row.getUpdatedAt(),
            loadPlatformRoles(row.getUserId())
        );
    }

    private void ensurePlatformUserExists(Long userId) {
        if (userId == null || userId <= 0 || !platformUserProfileRepository.existsByUserId(userId)) {
            throw BusinessException.notFound("平台用户档案不存在");
        }
    }

    private List<Long> validateAndNormalizeRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        if (roleIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw BusinessException.validationError("roleIds 仅支持正整数");
        }
        return roleIds.stream().distinct().toList();
    }

    private void assertAllPlatformRoles(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        List<Role> roles = roleRepository.findByIdInAndTenantIdIsNullOrderByIdAsc(roleIds);
        if (roles.size() != roleIds.size()) {
            throw BusinessException.validationError("仅允许绑定 tenant_id IS NULL + role_level=PLATFORM 的平台角色");
        }
        boolean allPlatform = roles.stream().allMatch(role -> isPlatformRole(role.getTenantId(), role.getRoleLevel()));
        if (!allPlatform) {
            throw BusinessException.validationError("仅允许绑定 tenant_id IS NULL + role_level=PLATFORM 的平台角色");
        }
    }

    /**
     * approval_mode != NONE 的平台角色绑定变更必须走审批申请，不得由直写接口绕过。
     */
    private void assertDirectReplaceDoesNotTouchApprovalBoundRoles(Long userId, List<Long> normalizedRoleIds) {
        List<Long> currentIds = roleAssignmentSyncService.findActiveRoleIdsForUserInPlatform(userId);
        Set<Long> current = new LinkedHashSet<>(currentIds);
        Set<Long> requested = new LinkedHashSet<>(normalizedRoleIds);
        Set<Long> union = new LinkedHashSet<>(current);
        union.addAll(requested);
        if (union.isEmpty()) {
            return;
        }
        List<Role> roles = roleRepository.findByIdInAndTenantIdIsNullOrderByIdAsc(new ArrayList<>(union));
        Map<Long, Role> byId = roles.stream().collect(Collectors.toMap(Role::getId, r -> r, (a, b) -> a));
        for (Long roleId : union) {
            Role role = byId.get(roleId);
            if (role == null || !isPlatformRole(role.getTenantId(), role.getRoleLevel())) {
                throw BusinessException.validationError("仅允许绑定 tenant_id IS NULL + role_level=PLATFORM 的平台角色");
            }
            boolean was = current.contains(roleId);
            boolean will = requested.contains(roleId);
            if (was != will && requiresApprovalBinding(role)) {
                throw BusinessException.validationError("涉及需审批绑定的平台角色变更，请使用平台角色赋权审批接口");
            }
        }
    }

    private static boolean requiresApprovalBinding(Role role) {
        String mode = role.getApprovalMode();
        if (mode == null || mode.isBlank()) {
            return false;
        }
        return !APPROVAL_MODE_NONE.equalsIgnoreCase(mode.trim());
    }

    private List<PlatformUserRoleDto> loadPlatformRoles(Long userId) {
        List<Long> roleIds = roleAssignmentSyncService.findActiveRoleIdsForUserInPlatform(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Role> roles = roleRepository.findByIdInAndTenantIdIsNullOrderByIdAsc(roleIds);
        if (roles.size() != roleIds.size()
            || roles.stream().anyMatch(role -> !isPlatformRole(role.getTenantId(), role.getRoleLevel()))) {
            throw BusinessException.validationError("检测到非平台角色绑定，平台用户角色读取已拒绝");
        }
        return roles.stream().map(this::toRoleDto).toList();
    }

    private PlatformUserRoleDto toRoleDto(Role role) {
        return new PlatformUserRoleDto(
            role.getId(),
            role.getCode(),
            role.getName(),
            role.getDescription(),
            role.isEnabled(),
            role.isBuiltin()
        );
    }

    private boolean isPlatformRole(Long tenantId, String roleLevel) {
        return tenantId == null && "PLATFORM".equalsIgnoreCase(roleLevel);
    }

    private boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        if (value instanceof CharSequence charSequence) {
            String normalized = charSequence.toString().trim();
            if ("1".equals(normalized) || "true".equalsIgnoreCase(normalized) || "yes".equalsIgnoreCase(normalized)) {
                return true;
            }
            if ("0".equals(normalized) || "false".equalsIgnoreCase(normalized) || "no".equalsIgnoreCase(normalized)) {
                return false;
            }
        }
        throw new IllegalArgumentException("unsupported boolean projection value type: " + value.getClass().getName());
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeStatus(String status, boolean defaultActive) {
        String normalized = normalize(status);
        if (normalized == null) {
            return defaultActive ? PlatformUserProfile.STATUS_ACTIVE : null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (PlatformUserProfile.STATUS_ACTIVE.equals(upper) || PlatformUserProfile.STATUS_DISABLED.equals(upper)) {
            return upper;
        }
        throw BusinessException.validationError("platform status 仅支持 ACTIVE 或 DISABLED");
    }
}
