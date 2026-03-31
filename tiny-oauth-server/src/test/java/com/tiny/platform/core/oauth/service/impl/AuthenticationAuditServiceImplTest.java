package com.tiny.platform.core.oauth.service.impl;

import com.tiny.platform.core.oauth.service.AuthenticationAuditQuery;
import com.tiny.platform.core.oauth.service.AuthenticationAuditSummary;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextFilter;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationAudit;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationAuditRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationAuditServiceImplTest {

    private final UserAuthenticationAuditRepository auditRepository = mock(UserAuthenticationAuditRepository.class);
    private final AuthenticationAuditServiceImpl auditService = new AuthenticationAuditServiceImpl(auditRepository);

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPersistTenantResolutionSourceFromRequestAttribute() {
        when(auditRepository.save(any(UserAuthenticationAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TenantContext.setActiveTenantId(9L);
        TenantContext.setTenantSource(TenantContext.SOURCE_SESSION);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(TenantContextFilter.TENANT_SOURCE_REQUEST_ATTRIBUTE)).thenReturn(TenantContext.SOURCE_TOKEN);

        auditService.recordLoginSuccess("admin", 1L, "LOCAL", "PASSWORD", request);

        ArgumentCaptor<UserAuthenticationAudit> captor = ArgumentCaptor.forClass(UserAuthenticationAudit.class);
        verify(auditRepository).save(captor.capture());

        UserAuthenticationAudit savedAudit = captor.getValue();
        assertThat(savedAudit.getTenantId()).isEqualTo(9L);
        assertThat(savedAudit.getTenantResolutionCode()).isEqualTo("resolved");
        assertThat(savedAudit.getTenantResolutionSource()).isEqualTo(TenantContext.SOURCE_TOKEN);
    }

    @Test
    void shouldFallbackToUnknownTenantSourceWhenContextMissing() {
        when(auditRepository.save(any(UserAuthenticationAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        auditService.recordLoginFailure("admin", 1L, "LOCAL", "PASSWORD", null);

        ArgumentCaptor<UserAuthenticationAudit> captor = ArgumentCaptor.forClass(UserAuthenticationAudit.class);
        verify(auditRepository).save(captor.capture());

        UserAuthenticationAudit savedAudit = captor.getValue();
        assertThat(savedAudit.getTenantId()).isNull();
        assertThat(savedAudit.getTenantResolutionCode()).isEqualTo("tenant_context_missing");
        assertThat(savedAudit.getTenantResolutionSource()).isEqualTo(TenantContext.SOURCE_UNKNOWN);
    }

    @Test
    void shouldResolveTenantFromAuthenticationActiveTenantWhenTenantContextMissing() {
        when(auditRepository.save(any(UserAuthenticationAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SecurityUser principal = new SecurityUser(8L, 12L, "alice", "", java.util.List.of(), true, true, true, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", java.util.List.of())
        );

        auditService.recordLoginSuccess("alice", 8L, "LOCAL", "PASSWORD", null);

        ArgumentCaptor<UserAuthenticationAudit> captor = ArgumentCaptor.forClass(UserAuthenticationAudit.class);
        verify(auditRepository).save(captor.capture());

        UserAuthenticationAudit savedAudit = captor.getValue();
        assertThat(savedAudit.getTenantId()).isEqualTo(12L);
        assertThat(savedAudit.getTenantResolutionCode()).isEqualTo("resolved");
        assertThat(savedAudit.getTenantResolutionSource()).isEqualTo(TenantContext.SOURCE_UNKNOWN);
    }

    @Test
    void recordSessionRevoke_shouldPersistTargetSessionId() {
        when(auditRepository.save(any(UserAuthenticationAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        auditService.recordSessionRevoke("alice", 8L, "sid-other", null);

        ArgumentCaptor<UserAuthenticationAudit> captor = ArgumentCaptor.forClass(UserAuthenticationAudit.class);
        verify(auditRepository).save(captor.capture());

        UserAuthenticationAudit savedAudit = captor.getValue();
        assertThat(savedAudit.getEventType()).isEqualTo("SESSION_REVOKE");
        assertThat(savedAudit.getSessionId()).isEqualTo("sid-other");
        assertThat(savedAudit.getSuccess()).isTrue();
    }

    @Test
    void search_shouldDelegateToRepositoryWithSpecificationAndPageable() {
        var pageable = PageRequest.of(0, 20);
        var page = new PageImpl<>(java.util.List.of(new UserAuthenticationAudit()), pageable, 1);
        var query = new AuthenticationAuditQuery(
            9L,
            1L,
            "alice",
            "LOGIN",
            true,
            java.time.LocalDateTime.of(2026, 3, 1, 0, 0),
            java.time.LocalDateTime.of(2026, 3, 2, 0, 0)
        );
        when(auditRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        var result = auditService.search(query, pageable);

        assertThat(result).isSameAs(page);
        verify(auditRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void listCurrentUserLoginHistory_shouldQueryLoginEvents() {
        var pageable = PageRequest.of(0, 20);
        var page = new PageImpl<>(java.util.List.of(new UserAuthenticationAudit()), pageable, 1);
        when(auditRepository.findByUserIdAndEventTypeOrderByCreatedAtDesc(8L, "LOGIN", pageable)).thenReturn(page);

        var result = auditService.listCurrentUserLoginHistory(8L, pageable);

        assertThat(result).isSameAs(page);
        verify(auditRepository).findByUserIdAndEventTypeOrderByCreatedAtDesc(8L, "LOGIN", pageable);
    }

    @Test
    void summarize_shouldAggregateRepositoryCounts() {
        var query = new AuthenticationAuditQuery(
            9L,
            1L,
            "alice",
            "LOGIN",
            true,
            java.time.LocalDateTime.of(2026, 3, 1, 0, 0),
            java.time.LocalDateTime.of(2026, 3, 2, 0, 0)
        );

        when(auditRepository.countByFilters(9L, 1L, "alice", "LOGIN", true,
            java.time.LocalDateTime.of(2026, 3, 1, 0, 0), java.time.LocalDateTime.of(2026, 3, 2, 0, 0)))
            .thenReturn(6L);
        when(auditRepository.countSuccessfulByFilters(9L, 1L, "alice", "LOGIN",
            java.time.LocalDateTime.of(2026, 3, 1, 0, 0), java.time.LocalDateTime.of(2026, 3, 2, 0, 0)))
            .thenReturn(4L);
        when(auditRepository.countFailedByFilters(9L, 1L, "alice", "LOGIN",
            java.time.LocalDateTime.of(2026, 3, 1, 0, 0), java.time.LocalDateTime.of(2026, 3, 2, 0, 0)))
            .thenReturn(2L);
        when(auditRepository.countSuccessfulLoginsByFilters(9L, 1L, "alice", "LOGIN",
            java.time.LocalDateTime.of(2026, 3, 1, 0, 0), java.time.LocalDateTime.of(2026, 3, 2, 0, 0)))
            .thenReturn(4L);
        when(auditRepository.countFailedLoginsByFilters(9L, 1L, "alice", "LOGIN",
            java.time.LocalDateTime.of(2026, 3, 1, 0, 0), java.time.LocalDateTime.of(2026, 3, 2, 0, 0)))
            .thenReturn(2L);
        when(auditRepository.countGroupedByEventType(9L, 1L, "alice", "LOGIN", true,
            java.time.LocalDateTime.of(2026, 3, 1, 0, 0), java.time.LocalDateTime.of(2026, 3, 2, 0, 0)))
            .thenReturn(java.util.List.<Object[]>of(new Object[] { "LOGIN", 6L }));

        AuthenticationAuditSummary summary = auditService.summarize(query);

        assertThat(summary.totalCount()).isEqualTo(6L);
        assertThat(summary.successCount()).isEqualTo(4L);
        assertThat(summary.failureCount()).isEqualTo(2L);
        assertThat(summary.loginSuccessCount()).isEqualTo(4L);
        assertThat(summary.loginFailureCount()).isEqualTo(2L);
        assertThat(summary.eventTypeCounts()).containsExactly(
            new AuthenticationAuditSummary.EventTypeCount("LOGIN", 6L)
        );
    }
}
