package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.infrastructure.auth.role.domain.PlatformRoleAssignmentRequest;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.dto.PlatformRoleAssignmentRequestDtos.PlatformRoleAssignmentRequestResponseDto;
import com.tiny.platform.infrastructure.auth.role.dto.PlatformRoleAssignmentRequestDtos.PlatformRoleAssignmentRequestReviewDto;
import com.tiny.platform.infrastructure.auth.role.dto.PlatformRoleAssignmentRequestDtos.PlatformRoleAssignmentRequestSubmitDto;
import com.tiny.platform.infrastructure.auth.role.repository.PlatformRoleAssignmentRequestRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.user.repository.PlatformUserProfileRepository;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.tiny.platform.core.oauth.model.SecurityUser;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tiny.platform.infrastructure.auth.role.domain.PlatformRoleGovernanceCodes.ACTION_GRANT;
import static com.tiny.platform.infrastructure.auth.role.domain.PlatformRoleGovernanceCodes.ACTION_REVOKE;
import static com.tiny.platform.infrastructure.auth.role.domain.PlatformRoleGovernanceCodes.APPROVAL_MODE_NONE;
import static com.tiny.platform.infrastructure.auth.role.domain.PlatformRoleGovernanceCodes.APPROVAL_MODE_ONE_STEP;
import static com.tiny.platform.infrastructure.auth.role.domain.PlatformRoleGovernanceCodes.STATUS_APPLIED;
import static com.tiny.platform.infrastructure.auth.role.domain.PlatformRoleGovernanceCodes.STATUS_CANCELED;
import static com.tiny.platform.infrastructure.auth.role.domain.PlatformRoleGovernanceCodes.STATUS_FAILED;
import static com.tiny.platform.infrastructure.auth.role.domain.PlatformRoleGovernanceCodes.STATUS_PENDING;
import static com.tiny.platform.infrastructure.auth.role.domain.PlatformRoleGovernanceCodes.STATUS_REJECTED;

@Service
public class PlatformRoleAssignmentRequestServiceImpl implements PlatformRoleAssignmentRequestService {

    private final PlatformRoleAssignmentRequestRepository requestRepository;
    private final RoleRepository roleRepository;
    private final PlatformUserProfileRepository platformUserProfileRepository;
    private final RoleAssignmentSyncService roleAssignmentSyncService;
    private final PlatformRoleAssignmentApplyExecutor platformRoleAssignmentApplyExecutor;

    public PlatformRoleAssignmentRequestServiceImpl(
        PlatformRoleAssignmentRequestRepository requestRepository,
        RoleRepository roleRepository,
        PlatformUserProfileRepository platformUserProfileRepository,
        RoleAssignmentSyncService roleAssignmentSyncService,
        PlatformRoleAssignmentApplyExecutor platformRoleAssignmentApplyExecutor
    ) {
        this.requestRepository = requestRepository;
        this.roleRepository = roleRepository;
        this.platformUserProfileRepository = platformUserProfileRepository;
        this.roleAssignmentSyncService = roleAssignmentSyncService;
        this.platformRoleAssignmentApplyExecutor = platformRoleAssignmentApplyExecutor;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PlatformRoleAssignmentRequestResponseDto> list(Long targetUserId, String status, Pageable pageable) {
        String normalizedStatus = normalizeUpper(status);
        Page<PlatformRoleAssignmentRequest> page = requestRepository.search(targetUserId, normalizedStatus, pageable);
        Set<Long> roleIds = page.getContent().stream().map(PlatformRoleAssignmentRequest::getRoleId).collect(Collectors.toSet());
        Map<Long, Role> roleMap = roleRepository.findByIdInAndTenantIdIsNullOrderByIdAsc(new ArrayList<>(roleIds)).stream()
            .collect(Collectors.toMap(Role::getId, r -> r, (a, b) -> a));
        return page.map(r -> toDto(r, roleMap.get(r.getRoleId())));
    }

    @Override
    @Transactional
    public PlatformRoleAssignmentRequestResponseDto submit(PlatformRoleAssignmentRequestSubmitDto request) {
        if (request == null || request.targetUserId() == null || request.targetUserId() <= 0
            || request.roleId() == null || request.roleId() <= 0) {
            throw BusinessException.validationError("targetUserId 与 roleId 必须为正整数");
        }
        Long requesterId = requireCurrentUserId();
        String action = normalizeActionType(request.actionType());
        if (!platformUserProfileRepository.existsByUserId(request.targetUserId())) {
            throw BusinessException.notFound("平台用户档案不存在");
        }
        Role role = roleRepository.findById(request.roleId())
            .orElseThrow(() -> BusinessException.validationError("角色不存在"));
        assertPlatformRole(role);
        if (!role.isEnabled()) {
            throw BusinessException.validationError("角色已禁用，无法发起申请");
        }
        String mode = normalizeApprovalMode(role.getApprovalMode());
        if (APPROVAL_MODE_NONE.equals(mode)) {
            throw BusinessException.validationError("该角色无需审批，请使用平台用户角色直写接口");
        }
        if (!APPROVAL_MODE_ONE_STEP.equals(mode)) {
            throw BusinessException.validationError("不支持的审批模式: " + mode);
        }
        if (requestRepository.existsByTargetUserIdAndRoleIdAndActionTypeAndStatus(
            request.targetUserId(), request.roleId(), action, STATUS_PENDING
        )) {
            throw BusinessException.validationError("已存在相同目标、角色与动作的待审批申请");
        }
        List<Long> active = roleAssignmentSyncService.findActiveRoleIdsForUserInPlatform(request.targetUserId());
        boolean holding = active.contains(request.roleId());
        if (ACTION_GRANT.equals(action)) {
            if (holding) {
                throw BusinessException.validationError("目标用户已持有该角色，无需申请授予");
            }
        } else if (ACTION_REVOKE.equals(action)) {
            if (!holding) {
                throw BusinessException.validationError("目标用户未持有该角色，无法申请回收");
            }
        } else {
            throw BusinessException.validationError("actionType 仅支持 GRANT 或 REVOKE");
        }

        PlatformRoleAssignmentRequest entity = new PlatformRoleAssignmentRequest();
        entity.setTargetUserId(request.targetUserId());
        entity.setRoleId(request.roleId());
        entity.setActionType(action);
        entity.setStatus(STATUS_PENDING);
        entity.setRequestedBy(requesterId);
        entity.setRequestedAt(LocalDateTime.now());
        entity.setReason(trimToNull(request.reason()));
        try {
            requestRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw BusinessException.validationError("已存在相同目标、角色与动作的待审批申请");
        }
        return toDto(entity, role);
    }

    @Override
    @Transactional
    public PlatformRoleAssignmentRequestResponseDto approve(Long id, PlatformRoleAssignmentRequestReviewDto review) {
        if (id == null || id <= 0) {
            throw BusinessException.validationError("申请 id 无效");
        }
        Long reviewerId = requireCurrentUserId();
        PlatformRoleAssignmentRequest entity = loadRequestForUpdate(id);
        if (STATUS_APPLIED.equals(entity.getStatus())) {
            Role role = loadPlatformRoleOrThrow(entity.getRoleId());
            return toDto(entity, role);
        }
        if (!STATUS_PENDING.equals(entity.getStatus())) {
            throw BusinessException.validationError("仅待审批的申请可通过审批动作处理");
        }
        Role role = loadPlatformRoleOrThrow(entity.getRoleId());
        String mode = normalizeApprovalMode(role.getApprovalMode());
        if (!APPROVAL_MODE_ONE_STEP.equals(mode)) {
            throw BusinessException.validationError("角色审批策略已变更，请拒绝该申请后重新提交");
        }
        LocalDateTime reviewedAt = LocalDateTime.now();
        entity.setReviewedBy(reviewerId);
        entity.setReviewedAt(reviewedAt);
        entity.setReviewComment(trimToNull(review != null ? review.comment() : null));

        List<Long> current = new ArrayList<>(roleAssignmentSyncService.findActiveRoleIdsForUserInPlatform(entity.getTargetUserId()));
        List<Long> next = computeNextRoleIds(current, entity.getRoleId(), entity.getActionType());

        try {
            platformRoleAssignmentApplyExecutor.replacePlatformRoles(entity.getTargetUserId(), next);
        } catch (RuntimeException ex) {
            entity.setStatus(STATUS_FAILED);
            entity.setAppliedAt(null);
            entity.setApplyError(resolveApplyError(ex));
            requestRepository.save(entity);
            return toDto(entity, role);
        }
        entity.setStatus(STATUS_APPLIED);
        entity.setAppliedAt(LocalDateTime.now());
        entity.setApplyError(null);
        requestRepository.save(entity);
        return toDto(entity, role);
    }

    @Override
    @Transactional
    public PlatformRoleAssignmentRequestResponseDto reject(Long id, PlatformRoleAssignmentRequestReviewDto review) {
        if (id == null || id <= 0) {
            throw BusinessException.validationError("申请 id 无效");
        }
        Long reviewerId = requireCurrentUserId();
        PlatformRoleAssignmentRequest entity = loadRequestForUpdate(id);
        if (!STATUS_PENDING.equals(entity.getStatus())) {
            throw BusinessException.validationError("仅待审批的申请可拒绝");
        }
        entity.setStatus(STATUS_REJECTED);
        entity.setReviewedBy(reviewerId);
        entity.setReviewedAt(LocalDateTime.now());
        entity.setReviewComment(trimToNull(review != null ? review.comment() : null));
        requestRepository.save(entity);
        return toDto(entity, loadPlatformRoleOrThrow(entity.getRoleId()));
    }

    @Override
    @Transactional
    public void cancel(Long id) {
        if (id == null || id <= 0) {
            throw BusinessException.validationError("申请 id 无效");
        }
        Long actorId = requireCurrentUserId();
        PlatformRoleAssignmentRequest entity = loadRequestForUpdate(id);
        if (!STATUS_PENDING.equals(entity.getStatus())) {
            throw BusinessException.validationError("仅待审批的申请可撤销");
        }
        if (!Objects.equals(entity.getRequestedBy(), actorId)) {
            throw BusinessException.validationError("仅申请人可撤销待审批申请");
        }
        entity.setStatus(STATUS_CANCELED);
        entity.setReviewedBy(actorId);
        entity.setReviewedAt(LocalDateTime.now());
        requestRepository.save(entity);
    }

    private PlatformRoleAssignmentRequest loadRequestForUpdate(Long id) {
        return requestRepository.findByIdForUpdate(id)
            .orElseThrow(() -> BusinessException.notFound("审批申请不存在"));
    }

    private Role loadPlatformRoleOrThrow(Long roleId) {
        Role role = roleRepository.findById(roleId)
            .orElseThrow(() -> BusinessException.validationError("角色不存在"));
        assertPlatformRole(role);
        return role;
    }

    private void assertPlatformRole(Role role) {
        if (role.getTenantId() != null || !"PLATFORM".equalsIgnoreCase(role.getRoleLevel())) {
            throw BusinessException.validationError("仅允许引用 tenant_id IS NULL 且 role_level=PLATFORM 的平台角色");
        }
    }

    private static List<Long> computeNextRoleIds(List<Long> current, Long roleId, String actionType) {
        LinkedHashSet<Long> set = new LinkedHashSet<>(current);
        if (ACTION_GRANT.equals(actionType)) {
            set.add(roleId);
        } else if (ACTION_REVOKE.equals(actionType)) {
            set.remove(roleId);
        } else {
            throw BusinessException.validationError("未知的 actionType");
        }
        return new ArrayList<>(set);
    }

    private PlatformRoleAssignmentRequestResponseDto toDto(PlatformRoleAssignmentRequest entity, Role role) {
        String code = role != null ? role.getCode() : null;
        String name = role != null ? role.getName() : null;
        return new PlatformRoleAssignmentRequestResponseDto(
            entity.getId(),
            entity.getTargetUserId(),
            entity.getRoleId(),
            code,
            name,
            entity.getActionType(),
            entity.getStatus(),
            entity.getRequestedBy(),
            entity.getRequestedAt(),
            entity.getReviewedBy(),
            entity.getReviewedAt(),
            entity.getReason(),
            entity.getReviewComment(),
            entity.getAppliedAt(),
            entity.getApplyError()
        );
    }

    private static String normalizeActionType(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw BusinessException.validationError("actionType 不能为空");
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        if (ACTION_GRANT.equals(u) || ACTION_REVOKE.equals(u)) {
            return u;
        }
        throw BusinessException.validationError("actionType 仅支持 GRANT 或 REVOKE");
    }

    private static String normalizeUpper(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeApprovalMode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return APPROVAL_MODE_NONE;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }

    private static String resolveApplyError(RuntimeException ex) {
        String message = ex.getMessage();
        if (StringUtils.hasText(message)) {
            return message.trim();
        }
        return "平台角色赋权写入失败: " + ex.getClass().getSimpleName();
    }

    private Long requireCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw BusinessException.validationError("未登录用户无法执行该操作");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            Long id = securityUser.getUserId();
            if (id == null || id <= 0) {
                throw new IllegalStateException("SecurityUser userId 无效");
            }
            return id;
        }
        throw BusinessException.validationError("无法解析当前用户");
    }
}
