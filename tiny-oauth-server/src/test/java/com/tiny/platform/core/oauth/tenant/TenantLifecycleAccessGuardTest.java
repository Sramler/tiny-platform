package com.tiny.platform.core.oauth.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.infrastructure.auth.audit.domain.AuthorizationAuditEventType;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditService;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantLifecycleAccessGuardTest {

    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final AuthorizationAuditService authorizationAuditService = mock(AuthorizationAuditService.class);
    private final TenantLifecycleAccessGuard guard = new TenantLifecycleAccessGuard(
        tenantRepository,
        new TenantLifecycleReadPolicy(),
        authorizationAuditService,
        new ObjectMapper()
    );

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldAllowFrozenTargetTenantReadForPlatformScope() {
        TenantContext.setActiveTenantId(1L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        setCurrentRequest("GET", "/sys/tenants/9");
        SecurityContextHolder.getContext().setAuthentication(authenticatedUser(
            10L,
            "platform-admin",
            "system:tenant:view"
        ));
        when(tenantRepository.findById(9L)).thenReturn(Optional.of(tenant(9L, "t-9", "Tenant 9", "FROZEN")));

        guard.assertPlatformTargetTenantReadable(9L, "system:tenant:view");

        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
        verify(authorizationAuditService).log(
            eq(AuthorizationAuditEventType.TENANT_LIFECYCLE_ALLOWLIST_ACCESS),
            eq(9L),
            eq(null),
            eq(null),
            eq(TenantContextContract.SCOPE_TYPE_PLATFORM),
            eq(9L),
            eq("tenant"),
            eq("system:tenant:view"),
            detailCaptor.capture(),
            eq("SUCCESS"),
            eq(null)
        );
        assertThat(detailCaptor.getValue()).contains("\"lifecycleStatus\":\"FROZEN\"");
        assertThat(detailCaptor.getValue()).contains("\"resourcePermission\":\"system:tenant:view\"");
    }

    @Test
    void shouldRejectDecommissionedHighSensitivityReadWithoutMfa() {
        TenantContext.setActiveTenantId(1L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        setCurrentRequest("GET", "/sys/audit/authentication/export");
        SecurityContextHolder.getContext().setAuthentication(authenticatedUser(
            10L,
            "platform-admin",
            "system:audit:authentication:export"
        ));
        when(tenantRepository.findById(9L)).thenReturn(Optional.of(tenant(9L, "t-9", "Tenant 9", "DECOMMISSIONED")));

        assertThatThrownBy(() -> guard.assertPlatformTargetTenantReadable(
            9L,
            "system:audit:authentication:export",
            true,
            "incident-check",
            null
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.FORBIDDEN);

        verify(authorizationAuditService).log(
            eq(AuthorizationAuditEventType.TENANT_LIFECYCLE_ALLOWLIST_ACCESS),
            eq(9L),
            eq(null),
            eq(null),
            eq(TenantContextContract.SCOPE_TYPE_PLATFORM),
            eq(9L),
            eq("audit"),
            eq("system:audit:authentication:export"),
            org.mockito.ArgumentMatchers.contains("\"reason\":\"incident-check\""),
            eq("DENIED"),
            eq("mfa_required")
        );
    }

    @Test
    void shouldRejectDecommissionedHighSensitivityReadWithoutReasonOrTicket() {
        TenantContext.setActiveTenantId(1L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        setCurrentRequest("GET", "/sys/audit/authorization/export");
        SecurityContextHolder.getContext().setAuthentication(authenticatedUser(
            10L,
            "platform-admin",
            "system:audit:auth:export",
            "FACTOR_TOTP"
        ));
        when(tenantRepository.findById(9L)).thenReturn(Optional.of(tenant(9L, "t-9", "Tenant 9", "DECOMMISSIONED")));

        assertThatThrownBy(() -> guard.assertPlatformTargetTenantReadable(
            9L,
            "system:audit:auth:export",
            true,
            "  ",
            null
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_PARAMETER);
    }

    @Test
    void shouldRejectNonAllowlistedTargetRead() {
        TenantContext.setActiveTenantId(1L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        setCurrentRequest("GET", "/sys/tenants/9/freeze");
        SecurityContextHolder.getContext().setAuthentication(authenticatedUser(
            10L,
            "platform-admin",
            "system:tenant:freeze"
        ));
        when(tenantRepository.findById(9L)).thenReturn(Optional.of(tenant(9L, "t-9", "Tenant 9", "DECOMMISSIONED")));

        assertThatThrownBy(() -> guard.assertPlatformTargetTenantReadable(9L, "system:tenant:freeze"))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.RESOURCE_STATE_INVALID);
    }

    private void setCurrentRequest(String method, String uri) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest(method, uri)));
    }

    private UsernamePasswordAuthenticationToken authenticatedUser(Long userId, String username, String... authorities) {
        SecurityUser principal = new SecurityUser(
            userId,
            1L,
            username,
            "",
            List.of(),
            true,
            true,
            true,
            true
        );
        return UsernamePasswordAuthenticationToken.authenticated(
            principal,
            null,
            java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()
        );
    }

    private Tenant tenant(Long id, String code, String name, String lifecycleStatus) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setCode(code);
        tenant.setName(name);
        tenant.setLifecycleStatus(lifecycleStatus);
        return tenant;
    }
}
