package com.tiny.platform.core.oauth.service.impl;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextFilter;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationAudit;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationAuditRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
}
