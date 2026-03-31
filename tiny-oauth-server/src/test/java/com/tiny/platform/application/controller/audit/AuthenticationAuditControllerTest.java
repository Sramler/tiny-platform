package com.tiny.platform.application.controller.audit;

import com.tiny.platform.core.oauth.service.AuthenticationAuditQuery;
import com.tiny.platform.core.oauth.service.AuthenticationAuditService;
import com.tiny.platform.core.oauth.service.AuthenticationAuditSummary;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.core.oauth.tenant.TenantLifecycleAccessGuard;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationAudit;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationAuditControllerTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void list_shouldUseActiveTenantInTenantScope() {
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        TenantLifecycleAccessGuard accessGuard = mock(TenantLifecycleAccessGuard.class);
        AuthenticationAuditController controller = new AuthenticationAuditController(auditService, accessGuard);
        var pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        UserAuthenticationAudit audit = sampleAudit();
        when(auditService.search(any(AuthenticationAuditQuery.class), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(audit), pageable, 1));
        TenantContext.setActiveTenantId(9L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);

        var response = controller.list(
            null,
            7L,
            "alice",
            "login",
            true,
            LocalDateTime.of(2026, 3, 1, 0, 0),
            LocalDateTime.of(2026, 3, 2, 0, 0),
            pageable
        );

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);
        assertThat(response.getBody().getContent().getFirst().tenantId()).isEqualTo(9L);
        assertThat(response.getBody().getContent().getFirst().eventType()).isEqualTo("LOGIN");

        ArgumentCaptor<AuthenticationAuditQuery> captor = ArgumentCaptor.forClass(AuthenticationAuditQuery.class);
        verify(auditService).search(captor.capture(), eq(pageable));
        verify(accessGuard).assertPlatformTargetTenantReadable(9L, "system:audit:authentication:view");
        AuthenticationAuditQuery query = captor.getValue();
        assertThat(query.tenantId()).isEqualTo(9L);
        assertThat(query.userId()).isEqualTo(7L);
        assertThat(query.username()).isEqualTo("alice");
        assertThat(query.eventType()).isEqualTo("LOGIN");
        assertThat(query.success()).isTrue();
    }

    @Test
    void list_shouldAllowPlatformScopeTenantFilter() {
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        TenantLifecycleAccessGuard accessGuard = mock(TenantLifecycleAccessGuard.class);
        AuthenticationAuditController controller = new AuthenticationAuditController(auditService, accessGuard);
        var pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(auditService.search(any(AuthenticationAuditQuery.class), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(), pageable, 0));
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);

        controller.list(12L, null, null, null, null, null, null, pageable);

        ArgumentCaptor<AuthenticationAuditQuery> captor = ArgumentCaptor.forClass(AuthenticationAuditQuery.class);
        verify(auditService).search(captor.capture(), eq(pageable));
        verify(accessGuard).assertPlatformTargetTenantReadable(12L, "system:audit:authentication:view");
        assertThat(captor.getValue().tenantId()).isEqualTo(12L);
    }

    @Test
    void list_shouldRejectCrossTenantQueryInTenantScope() {
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        AuthenticationAuditController controller = new AuthenticationAuditController(
            auditService,
            mock(TenantLifecycleAccessGuard.class)
        );
        var pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        TenantContext.setActiveTenantId(9L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);

        assertThatThrownBy(() -> controller.list(10L, null, null, null, null, null, null, pageable))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("当前作用域不允许查询其他租户的认证审计");
    }

    @Test
    void list_shouldRejectInvalidTimeRange() {
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        AuthenticationAuditController controller = new AuthenticationAuditController(
            auditService,
            mock(TenantLifecycleAccessGuard.class)
        );
        var pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        TenantContext.setActiveTenantId(9L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);

        assertThatThrownBy(() -> controller.list(
            null,
            null,
            null,
            null,
            null,
            LocalDateTime.of(2026, 3, 2, 0, 0),
            LocalDateTime.of(2026, 3, 1, 0, 0),
            pageable
        )).isInstanceOf(BusinessException.class)
            .hasMessageContaining("开始时间不能晚于结束时间");
    }

    @Test
    void summary_shouldUseTenantScopeAndFilters() {
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        TenantLifecycleAccessGuard accessGuard = mock(TenantLifecycleAccessGuard.class);
        AuthenticationAuditController controller = new AuthenticationAuditController(auditService, accessGuard);
        when(auditService.summarize(any(AuthenticationAuditQuery.class)))
            .thenReturn(new AuthenticationAuditSummary(
                6L,
                4L,
                2L,
                4L,
                2L,
                List.of(new AuthenticationAuditSummary.EventTypeCount("LOGIN", 6L))
            ));
        TenantContext.setActiveTenantId(9L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);

        var response = controller.summary(
            null,
            7L,
            "alice",
            "login",
            true,
            LocalDateTime.of(2026, 3, 1, 0, 0),
            LocalDateTime.of(2026, 3, 2, 0, 0)
        );

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().totalCount()).isEqualTo(6L);

        ArgumentCaptor<AuthenticationAuditQuery> captor = ArgumentCaptor.forClass(AuthenticationAuditQuery.class);
        verify(auditService).summarize(captor.capture());
        verify(accessGuard).assertPlatformTargetTenantReadable(9L, "system:audit:authentication:view");
        AuthenticationAuditQuery query = captor.getValue();
        assertThat(query.tenantId()).isEqualTo(9L);
        assertThat(query.userId()).isEqualTo(7L);
        assertThat(query.username()).isEqualTo("alice");
        assertThat(query.eventType()).isEqualTo("LOGIN");
        assertThat(query.success()).isTrue();
    }

    @Test
    void export_shouldReturnCsvContent() throws Exception {
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        TenantLifecycleAccessGuard accessGuard = mock(TenantLifecycleAccessGuard.class);
        AuthenticationAuditController controller = new AuthenticationAuditController(auditService, accessGuard);
        UserAuthenticationAudit audit = sampleAudit();
        when(auditService.search(any(AuthenticationAuditQuery.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(audit), PageRequest.of(0, 10001, Sort.by(Sort.Direction.DESC, "createdAt")), 1));
        TenantContext.setActiveTenantId(9L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);

        var response = controller.export(null, 7L, "alice", "login", true, "incident-check", "TICKET-8", null, null);

        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("authentication-audit");
        StreamingResponseBody body = response.getBody();
        assertThat(body).isNotNull();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        body.writeTo(outputStream);
        String csv = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(csv).contains("LOGIN");
        assertThat(csv).contains("alice");
        verify(accessGuard).assertPlatformTargetTenantReadable(
            9L,
            "system:audit:authentication:export",
            true,
            "incident-check",
            "TICKET-8"
        );
    }

    private static UserAuthenticationAudit sampleAudit() {
        UserAuthenticationAudit audit = new UserAuthenticationAudit();
        audit.setId(1L);
        audit.setTenantId(9L);
        audit.setUserId(7L);
        audit.setUsername("alice");
        audit.setEventType("LOGIN");
        audit.setSuccess(true);
        audit.setAuthenticationProvider("LOCAL");
        audit.setAuthenticationFactor("PASSWORD");
        audit.setIpAddress("127.0.0.1");
        audit.setUserAgent("JUnit");
        audit.setSessionId("session-1");
        audit.setTokenId("token-1");
        audit.setTenantResolutionCode("resolved");
        audit.setTenantResolutionSource("token");
        audit.setCreatedAt(LocalDateTime.of(2026, 3, 1, 10, 0));
        return audit;
    }
}
