package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.infrastructure.auth.role.domain.PlatformRoleAssignmentRequest;
import com.tiny.platform.infrastructure.auth.role.domain.PlatformRoleGovernanceCodes;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.dto.PlatformRoleAssignmentRequestDtos.PlatformRoleAssignmentRequestReviewDto;
import com.tiny.platform.infrastructure.auth.role.dto.PlatformRoleAssignmentRequestDtos.PlatformRoleAssignmentRequestSubmitDto;
import com.tiny.platform.infrastructure.auth.role.repository.PlatformRoleAssignmentRequestRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.user.repository.PlatformUserProfileRepository;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformRoleAssignmentRequestServiceImplTest {

    private PlatformRoleAssignmentRequestRepository requestRepository;
    private RoleRepository roleRepository;
    private PlatformUserProfileRepository platformUserProfileRepository;
    private RoleAssignmentSyncService roleAssignmentSyncService;
    private PlatformRoleAssignmentApplyExecutor applyExecutor;
    private PlatformRoleAssignmentRequestServiceImpl service;

    @BeforeEach
    void setUp() {
        requestRepository = mock(PlatformRoleAssignmentRequestRepository.class);
        roleRepository = mock(RoleRepository.class);
        platformUserProfileRepository = mock(PlatformUserProfileRepository.class);
        roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        applyExecutor = mock(PlatformRoleAssignmentApplyExecutor.class);
        service = new PlatformRoleAssignmentRequestServiceImpl(
            requestRepository,
            roleRepository,
            platformUserProfileRepository,
            roleAssignmentSyncService,
            applyExecutor
        );
        loginAs(9001L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(Long userId) {
        SecurityUser principal = new SecurityUser(
            userId,
            1L,
            "u",
            "",
            List.of(new SimpleGrantedAuthority("platform:role:approval:submit")),
            true,
            true,
            true,
            true
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            principal,
            "",
            principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void submit_shouldPersistPending_whenOneStepGrant() {
        when(platformUserProfileRepository.existsByUserId(10L)).thenReturn(true);
        Role role = platformRole(7L, PlatformRoleGovernanceCodes.APPROVAL_MODE_ONE_STEP);
        when(roleRepository.findById(7L)).thenReturn(Optional.of(role));
        when(requestRepository.existsByTargetUserIdAndRoleIdAndActionTypeAndStatus(
            10L, 7L, PlatformRoleGovernanceCodes.ACTION_GRANT, PlatformRoleGovernanceCodes.STATUS_PENDING
        )).thenReturn(false);
        when(roleAssignmentSyncService.findActiveRoleIdsForUserInPlatform(10L)).thenReturn(List.of());
        when(requestRepository.saveAndFlush(any(PlatformRoleAssignmentRequest.class))).thenAnswer(inv -> {
            PlatformRoleAssignmentRequest e = inv.getArgument(0);
            e.setId(100L);
            return e;
        });

        var dto = service.submit(new PlatformRoleAssignmentRequestSubmitDto(10L, 7L, "GRANT", "need"));

        assertThat(dto.status()).isEqualTo(PlatformRoleGovernanceCodes.STATUS_PENDING);
        assertThat(dto.id()).isEqualTo(100L);
        verify(requestRepository).saveAndFlush(any(PlatformRoleAssignmentRequest.class));
    }

    @Test
    void submit_shouldTranslateDuplicatePendingConstraintViolation() {
        when(platformUserProfileRepository.existsByUserId(10L)).thenReturn(true);
        Role role = platformRole(7L, PlatformRoleGovernanceCodes.APPROVAL_MODE_ONE_STEP);
        when(roleRepository.findById(7L)).thenReturn(Optional.of(role));
        when(requestRepository.existsByTargetUserIdAndRoleIdAndActionTypeAndStatus(
            10L, 7L, PlatformRoleGovernanceCodes.ACTION_GRANT, PlatformRoleGovernanceCodes.STATUS_PENDING
        )).thenReturn(false);
        when(roleAssignmentSyncService.findActiveRoleIdsForUserInPlatform(10L)).thenReturn(List.of());
        when(requestRepository.saveAndFlush(any(PlatformRoleAssignmentRequest.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate pending"));

        assertThatThrownBy(() -> service.submit(new PlatformRoleAssignmentRequestSubmitDto(10L, 7L, "GRANT", null)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("待审批申请");
    }

    @Test
    void submit_shouldReject_whenApprovalModeNone() {
        when(platformUserProfileRepository.existsByUserId(10L)).thenReturn(true);
        Role role = platformRole(7L, PlatformRoleGovernanceCodes.APPROVAL_MODE_NONE);
        when(roleRepository.findById(7L)).thenReturn(Optional.of(role));
        when(roleAssignmentSyncService.findActiveRoleIdsForUserInPlatform(10L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.submit(new PlatformRoleAssignmentRequestSubmitDto(10L, 7L, "GRANT", null)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("无需审批");
    }

    @Test
    void approve_shouldMarkFailed_whenRbac3RejectsApply() {
        PlatformRoleAssignmentRequest entity = pendingRequest(1L, 10L, 7L, PlatformRoleGovernanceCodes.ACTION_GRANT);
        when(requestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(entity));
        Role role = platformRole(7L, PlatformRoleGovernanceCodes.APPROVAL_MODE_ONE_STEP);
        when(roleRepository.findById(7L)).thenReturn(Optional.of(role));
        when(roleAssignmentSyncService.findActiveRoleIdsForUserInPlatform(10L)).thenReturn(List.of());
        doThrow(BusinessException.validationError("RBAC3 冲突"))
            .when(applyExecutor).replacePlatformRoles(eq(10L), anyList());

        var dto = service.approve(1L, new PlatformRoleAssignmentRequestReviewDto("ok"));

        assertThat(dto.status()).isEqualTo(PlatformRoleGovernanceCodes.STATUS_FAILED);
        assertThat(dto.applyError()).contains("RBAC3");
        verify(requestRepository).save(entity);
        verify(requestRepository).findByIdForUpdate(1L);
        verify(requestRepository, never()).findById(1L);
    }

    @Test
    void approve_shouldMarkFailed_whenUnexpectedApplyErrorOccurs() {
        PlatformRoleAssignmentRequest entity = pendingRequest(11L, 10L, 7L, PlatformRoleGovernanceCodes.ACTION_GRANT);
        when(requestRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(entity));
        Role role = platformRole(7L, PlatformRoleGovernanceCodes.APPROVAL_MODE_ONE_STEP);
        when(roleRepository.findById(7L)).thenReturn(Optional.of(role));
        when(roleAssignmentSyncService.findActiveRoleIdsForUserInPlatform(10L)).thenReturn(List.of());
        doThrow(new IllegalStateException("db down"))
            .when(applyExecutor).replacePlatformRoles(eq(10L), anyList());

        var dto = service.approve(11L, new PlatformRoleAssignmentRequestReviewDto("ok"));

        assertThat(dto.status()).isEqualTo(PlatformRoleGovernanceCodes.STATUS_FAILED);
        assertThat(dto.applyError()).contains("db down");
        verify(requestRepository).save(entity);
    }

    @Test
    void approve_shouldApply_whenRbac3Passes() {
        PlatformRoleAssignmentRequest entity = pendingRequest(2L, 10L, 7L, PlatformRoleGovernanceCodes.ACTION_GRANT);
        when(requestRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(entity));
        Role role = platformRole(7L, PlatformRoleGovernanceCodes.APPROVAL_MODE_ONE_STEP);
        when(roleRepository.findById(7L)).thenReturn(Optional.of(role));
        when(roleAssignmentSyncService.findActiveRoleIdsForUserInPlatform(10L)).thenReturn(List.of());

        var dto = service.approve(2L, new PlatformRoleAssignmentRequestReviewDto(null));

        assertThat(dto.status()).isEqualTo(PlatformRoleGovernanceCodes.STATUS_APPLIED);
        verify(applyExecutor).replacePlatformRoles(10L, List.of(7L));
        verify(requestRepository).save(entity);
    }

    @Test
    void reject_shouldUseLockedLookup() {
        PlatformRoleAssignmentRequest entity = pendingRequest(12L, 10L, 7L, PlatformRoleGovernanceCodes.ACTION_GRANT);
        when(requestRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(entity));
        when(roleRepository.findById(7L)).thenReturn(Optional.of(platformRole(7L, PlatformRoleGovernanceCodes.APPROVAL_MODE_ONE_STEP)));

        var dto = service.reject(12L, new PlatformRoleAssignmentRequestReviewDto("no"));

        assertThat(dto.status()).isEqualTo(PlatformRoleGovernanceCodes.STATUS_REJECTED);
        verify(requestRepository).findByIdForUpdate(12L);
        verify(requestRepository, never()).findById(12L);
    }

    @Test
    void cancel_shouldUseLockedLookup() {
        PlatformRoleAssignmentRequest entity = pendingRequest(13L, 10L, 7L, PlatformRoleGovernanceCodes.ACTION_GRANT);
        entity.setRequestedBy(9001L);
        when(requestRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(entity));

        service.cancel(13L);

        assertThat(entity.getStatus()).isEqualTo(PlatformRoleGovernanceCodes.STATUS_CANCELED);
        verify(requestRepository).findByIdForUpdate(13L);
        verify(requestRepository, never()).findById(13L);
    }

    @Test
    void list_shouldMapPage() {
        PlatformRoleAssignmentRequest row = pendingRequest(3L, 10L, 8L, PlatformRoleGovernanceCodes.ACTION_REVOKE);
        Role role = platformRole(8L, PlatformRoleGovernanceCodes.APPROVAL_MODE_ONE_STEP);
        when(requestRepository.search(isNull(), isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 10), 1));
        when(roleRepository.findByIdInAndTenantIdIsNullOrderByIdAsc(List.of(8L))).thenReturn(List.of(role));

        var page = service.list(null, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).roleCode()).isEqualTo("ROLE_X");
    }

    private static PlatformRoleAssignmentRequest pendingRequest(Long id, Long targetUserId, Long roleId, String action) {
        PlatformRoleAssignmentRequest e = new PlatformRoleAssignmentRequest();
        e.setId(id);
        e.setTargetUserId(targetUserId);
        e.setRoleId(roleId);
        e.setActionType(action);
        e.setStatus(PlatformRoleGovernanceCodes.STATUS_PENDING);
        e.setRequestedBy(1L);
        e.setRequestedAt(LocalDateTime.now());
        return e;
    }

    private static Role platformRole(Long id, String approvalMode) {
        Role r = new Role();
        r.setId(id);
        r.setTenantId(null);
        r.setRoleLevel("PLATFORM");
        r.setCode("ROLE_X");
        r.setName("X");
        r.setEnabled(true);
        r.setBuiltin(false);
        r.setApprovalMode(approvalMode);
        r.setRiskLevel("NORMAL");
        return r;
    }
}
