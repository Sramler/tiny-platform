package com.tiny.platform.infrastructure.auth.org.service;

import com.tiny.platform.infrastructure.auth.audit.domain.AuthorizationAuditEventType;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditService;
import com.tiny.platform.infrastructure.auth.datascope.framework.DataScope;
import com.tiny.platform.infrastructure.auth.datascope.framework.DataScopeContext;
import com.tiny.platform.infrastructure.auth.datascope.framework.ResolvedDataScope;
import com.tiny.platform.infrastructure.auth.org.domain.OrganizationUnit;
import com.tiny.platform.infrastructure.auth.org.domain.UserUnit;
import com.tiny.platform.infrastructure.auth.org.dto.UserUnitResponseDto;
import com.tiny.platform.infrastructure.auth.org.repository.OrganizationUnitRepository;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户-组织/部门归属管理服务。
 *
 * <p>业务规则：</p>
 * <ul>
 *   <li>同一租户同一用户同一部门不可重复归属</li>
 *   <li>同一租户同一用户最多一个主部门（{@code isPrimary=true}）</li>
 *   <li>设置新主部门时自动清除旧主部门标记</li>
 * </ul>
 */
@Service
public class UserUnitService {

    private static final Logger logger = LoggerFactory.getLogger(UserUnitService.class);

    private final UserUnitRepository userUnitRepository;
    private final OrganizationUnitRepository orgUnitRepository;
    private final AuthorizationAuditService auditService;

    public UserUnitService(UserUnitRepository userUnitRepository,
                           OrganizationUnitRepository orgUnitRepository,
                           AuthorizationAuditService auditService) {
        this.userUnitRepository = userUnitRepository;
        this.orgUnitRepository = orgUnitRepository;
        this.auditService = auditService;
    }

    @DataScope(module = "org")
    public List<UserUnitResponseDto> listByUser(Long tenantId, Long userId) {
        if (!isUserVisibleForRead(tenantId, userId)) {
            return List.of();
        }
        List<UserUnit> memberships = userUnitRepository.findByTenantIdAndUserIdAndStatus(tenantId, userId, "ACTIVE");
        return memberships.stream().map(uu -> toDto(uu, tenantId)).toList();
    }

    @DataScope(module = "org")
    public List<UserUnitResponseDto> listByUnit(Long tenantId, Long unitId) {
        if (!isUnitVisibleForRead(unitId)) {
            return List.of();
        }
        List<UserUnit> memberships = userUnitRepository.findByTenantIdAndUnitIdAndStatus(tenantId, unitId, "ACTIVE");
        return memberships.stream().map(uu -> toDto(uu, tenantId)).toList();
    }

    /**
     * 将用户添加到组织/部门节点。
     *
     * @param isPrimary 是否设为主部门；如果为 true，会自动清除该用户在当前租户的旧主部门标记
     */
    @Transactional
    public UserUnitResponseDto addUserToUnit(Long tenantId, Long userId, Long unitId, boolean isPrimary) {
        OrganizationUnit unit = orgUnitRepository.findByIdAndTenantId(unitId, tenantId)
            .orElseThrow(() -> BusinessException.notFound("组织/部门节点不存在"));

        if (userUnitRepository.existsByTenantIdAndUserIdAndUnitId(tenantId, userId, unitId)) {
            throw BusinessException.alreadyExists("用户已属于该组织/部门");
        }

        if (isPrimary) {
            clearPrimaryFlag(tenantId, userId);
        }

        UserUnit uu = new UserUnit();
        uu.setTenantId(tenantId);
        uu.setUserId(userId);
        uu.setUnitId(unitId);
        uu.setIsPrimary(isPrimary);
        uu.setStatus("ACTIVE");

        uu = userUnitRepository.save(uu);
        logger.info("Added user {} to unit {} (tenantId={}, primary={})", userId, unitId, tenantId, isPrimary);
        auditService.logSuccess(AuthorizationAuditEventType.USER_UNIT_ASSIGN,
            tenantId, userId, null, unit.getUnitType(), unitId,
            "{\"isPrimary\":" + isPrimary + "}");
        return toDto(uu, tenantId);
    }

    /**
     * 将用户从组织/部门移除（逻辑移除，标记 LEFT + left_at）。
     */
    @Transactional
    public void removeUserFromUnit(Long tenantId, Long userId, Long unitId) {
        UserUnit uu = userUnitRepository.findByTenantIdAndUserIdAndUnitId(tenantId, userId, unitId)
            .orElseThrow(() -> BusinessException.notFound("用户归属关系不存在"));

        uu.setStatus("LEFT");
        uu.setLeftAt(LocalDateTime.now());
        uu.setIsPrimary(false);
        userUnitRepository.save(uu);
        logger.info("Removed user {} from unit {} (tenantId={})", userId, unitId, tenantId);
        auditService.logSuccess(AuthorizationAuditEventType.USER_UNIT_REMOVE,
            tenantId, userId, null, null, unitId, null);
    }

    /**
     * 设置用户的主部门。
     */
    @Transactional
    public void setPrimaryUnit(Long tenantId, Long userId, Long unitId) {
        UserUnit uu = userUnitRepository.findByTenantIdAndUserIdAndUnitId(tenantId, userId, unitId)
            .orElseThrow(() -> BusinessException.notFound("用户归属关系不存在"));

        if (!"ACTIVE".equals(uu.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "只能将 ACTIVE 状态的归属设为主部门");
        }

        clearPrimaryFlag(tenantId, userId);
        uu.setIsPrimary(true);
        userUnitRepository.save(uu);
        logger.info("Set primary unit for user {}: unitId={} (tenantId={})", userId, unitId, tenantId);
        auditService.logSuccess(AuthorizationAuditEventType.USER_UNIT_SET_PRIMARY,
            tenantId, userId, null, null, unitId, null);
    }

    /**
     * 获取用户在当前租户的主部门。
     */
    @DataScope(module = "org")
    public Optional<UserUnitResponseDto> getPrimaryUnit(Long tenantId, Long userId) {
        if (!isUserVisibleForRead(tenantId, userId)) {
            return Optional.empty();
        }
        return userUnitRepository.findPrimaryByTenantIdAndUserId(tenantId, userId)
            .map(uu -> toDto(uu, tenantId));
    }

    /**
     * 使用目标组织/部门集合整体替换用户归属。
     *
     * <p>规则：</p>
     * <ul>
     *   <li>{@code primaryUnitId} 必须包含在 {@code unitIds} 中</li>
     *   <li>已存在但离开的归属会被重新激活，而不是插入重复记录</li>
     *   <li>未出现在目标集合中的 ACTIVE 归属会被标记为 LEFT</li>
     * </ul>
     */
    @Transactional
    public void replaceUserUnits(Long tenantId, Long userId, List<Long> unitIds, Long primaryUnitId) {
        LinkedHashSet<Long> requestedUnitIds = normalizeUnitIds(unitIds);
        if (primaryUnitId != null && !requestedUnitIds.contains(primaryUnitId)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "主组织/部门必须包含在归属列表中");
        }
        validateUnitsExist(tenantId, requestedUnitIds);

        Map<Long, UserUnit> existingMemberships = requestedUnitIds.stream()
            .map(unitId -> userUnitRepository.findByTenantIdAndUserIdAndUnitId(tenantId, userId, unitId))
            .flatMap(Optional::stream)
            .collect(Collectors.toMap(UserUnit::getUnitId, Function.identity()));

        for (Long unitId : requestedUnitIds) {
            boolean isPrimary = unitId.equals(primaryUnitId);
            UserUnit membership = existingMemberships.get(unitId);
            boolean createdOrReactivated = membership == null || !"ACTIVE".equals(membership.getStatus());

            if (membership == null) {
                membership = new UserUnit();
                membership.setTenantId(tenantId);
                membership.setUserId(userId);
                membership.setUnitId(unitId);
            }

            membership.setStatus("ACTIVE");
            membership.setLeftAt(null);
            membership.setIsPrimary(isPrimary);
            if (membership.getJoinedAt() == null) {
                membership.setJoinedAt(LocalDateTime.now());
            }
            userUnitRepository.save(membership);

            if (createdOrReactivated) {
                logAssignAudit(tenantId, userId, unitId, isPrimary);
            }
        }

        List<UserUnit> activeMemberships = userUnitRepository.findByTenantIdAndUserIdAndStatus(tenantId, userId, "ACTIVE");
        for (UserUnit membership : activeMemberships) {
            if (requestedUnitIds.contains(membership.getUnitId())) {
                continue;
            }
            membership.setStatus("LEFT");
            membership.setLeftAt(LocalDateTime.now());
            membership.setIsPrimary(false);
            userUnitRepository.save(membership);
            logger.info("Removed user {} from unit {} (tenantId={}) via replace", userId, membership.getUnitId(), tenantId);
            auditService.logSuccess(AuthorizationAuditEventType.USER_UNIT_REMOVE,
                tenantId, userId, null, null, membership.getUnitId(), null);
        }

        if (primaryUnitId != null) {
            logger.info("Set primary unit for user {}: unitId={} (tenantId={}) via replace", userId, primaryUnitId, tenantId);
            auditService.logSuccess(AuthorizationAuditEventType.USER_UNIT_SET_PRIMARY,
                tenantId, userId, null, null, primaryUnitId, null);
        }
    }

    private void clearPrimaryFlag(Long tenantId, Long userId) {
        userUnitRepository.findPrimaryByTenantIdAndUserId(tenantId, userId)
            .ifPresent(existing -> {
                existing.setIsPrimary(false);
                userUnitRepository.save(existing);
            });
    }

    private UserUnitResponseDto toDto(UserUnit uu, Long tenantId) {
        UserUnitResponseDto dto = new UserUnitResponseDto();
        dto.setId(uu.getId());
        dto.setTenantId(uu.getTenantId());
        dto.setUserId(uu.getUserId());
        dto.setUnitId(uu.getUnitId());
        dto.setIsPrimary(uu.getIsPrimary());
        dto.setStatus(uu.getStatus());
        dto.setJoinedAt(uu.getJoinedAt());
        dto.setLeftAt(uu.getLeftAt());
        dto.setCreatedAt(uu.getCreatedAt());
        dto.setUpdatedAt(uu.getUpdatedAt());

        orgUnitRepository.findByIdAndTenantId(uu.getUnitId(), tenantId).ifPresent(unit -> {
            dto.setUnitCode(unit.getCode());
            dto.setUnitName(unit.getName());
            dto.setUnitType(unit.getUnitType());
        });

        return dto;
    }

    private LinkedHashSet<Long> normalizeUnitIds(List<Long> unitIds) {
        if (CollectionUtils.isEmpty(unitIds)) {
            return new LinkedHashSet<>();
        }
        return unitIds.stream()
            .filter(unitId -> unitId != null && unitId > 0)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void validateUnitsExist(Long tenantId, Set<Long> unitIds) {
        for (Long unitId : unitIds) {
            orgUnitRepository.findByIdAndTenantId(unitId, tenantId)
                .orElseThrow(() -> BusinessException.notFound("组织/部门节点不存在: " + unitId));
        }
    }

    private void logAssignAudit(Long tenantId, Long userId, Long unitId, boolean isPrimary) {
        OrganizationUnit unit = orgUnitRepository.findByIdAndTenantId(unitId, tenantId)
            .orElseThrow(() -> BusinessException.notFound("组织/部门节点不存在"));
        logger.info("Added user {} to unit {} (tenantId={}, primary={}) via replace", userId, unitId, tenantId, isPrimary);
        auditService.logSuccess(AuthorizationAuditEventType.USER_UNIT_ASSIGN,
            tenantId, userId, null, unit.getUnitType(), unitId,
            "{\"isPrimary\":" + isPrimary + "}");
    }

    private boolean isUserVisibleForRead(Long tenantId, Long userId) {
        ResolvedDataScope scope = DataScopeContext.get();
        if (scope == null || scope.isUnrestricted()) {
            return true;
        }

        Long currentUserId = extractCurrentUserId();
        if (scope.isSelfOnly() && currentUserId != null && Objects.equals(currentUserId, userId)) {
            return true;
        }
        if (scope.getVisibleUserIds().contains(userId)) {
            return true;
        }
        if (scope.getVisibleUnitIds().isEmpty()) {
            return false;
        }
        return userUnitRepository.findUnitIdsByTenantIdAndUserIdAndStatus(tenantId, userId, "ACTIVE").stream()
            .anyMatch(scope.getVisibleUnitIds()::contains);
    }

    private boolean isUnitVisibleForRead(Long unitId) {
        ResolvedDataScope scope = DataScopeContext.get();
        if (scope == null || scope.isUnrestricted()) {
            return true;
        }
        return scope.getVisibleUnitIds().contains(unitId);
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
}
