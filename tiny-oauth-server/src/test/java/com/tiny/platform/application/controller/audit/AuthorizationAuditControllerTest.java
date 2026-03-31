package com.tiny.platform.application.controller.audit;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.core.oauth.tenant.TenantLifecycleAccessGuard;
import com.tiny.platform.infrastructure.auth.audit.domain.AuthorizationAuditLog;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditQuery;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditService;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditSummary;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorizationAuditControllerTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void list_shouldUseActiveTenantAndFiltersInTenantScope() {
        AuthorizationAuditService auditService = mock(AuthorizationAuditService.class);
        TenantLifecycleAccessGuard accessGuard = mock(TenantLifecycleAccessGuard.class);
        AuthorizationAuditController controller = new AuthorizationAuditController(auditService, accessGuard);
        var pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        AuthorizationAuditLog audit = sampleAudit();
        when(auditService.search(any(AuthorizationAuditQuery.class), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(audit), pageable, 1));
        TenantContext.setActiveTenantId(9L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);

        var response = controller.list(
            null,
            " role_assignment_grant ",
            3L,
            7L,
            " success ",
            "system:user:assign-role",
            "manual freeze",
            " carrier-type-1 ",
            5,
            " ALLOW ",
            LocalDateTime.of(2026, 3, 1, 0, 0),
            LocalDateTime.of(2026, 3, 2, 0, 0),
            pageable
        );

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);
        assertThat(response.getBody().getContent().getFirst().getTenantId()).isEqualTo(9L);
        assertThat(response.getBody().getContent().getFirst().getEventType()).isEqualTo("ROLE_ASSIGNMENT_GRANT");

        ArgumentCaptor<AuthorizationAuditQuery> captor = ArgumentCaptor.forClass(AuthorizationAuditQuery.class);
        verify(auditService).search(captor.capture(), eq(pageable));
        verify(accessGuard).assertPlatformTargetTenantReadable(9L, "system:audit:auth:view");
        AuthorizationAuditQuery query = captor.getValue();
        assertThat(query.tenantId()).isEqualTo(9L);
        assertThat(query.eventType()).isEqualTo("ROLE_ASSIGNMENT_GRANT");
        assertThat(query.actorUserId()).isEqualTo(3L);
        assertThat(query.targetUserId()).isEqualTo(7L);
        assertThat(query.result()).isEqualTo("SUCCESS");
        assertThat(query.resourcePermission()).isEqualTo("system:user:assign-role");
        assertThat(query.detailReason()).isEqualTo("manual freeze");
        assertThat(query.carrierType()).isEqualTo("carrier-type-1");
        assertThat(query.requirementGroup()).isEqualTo(5);
        assertThat(query.decision()).isEqualTo("ALLOW");
        assertThat(query.startTime()).isEqualTo(LocalDateTime.of(2026, 3, 1, 0, 0));
        assertThat(query.endTime()).isEqualTo(LocalDateTime.of(2026, 3, 2, 0, 0));
    }

    @Test
    void list_shouldAllowPlatformScopeTenantFilter() {
        AuthorizationAuditService auditService = mock(AuthorizationAuditService.class);
        TenantLifecycleAccessGuard accessGuard = mock(TenantLifecycleAccessGuard.class);
        AuthorizationAuditController controller = new AuthorizationAuditController(auditService, accessGuard);
        var pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(auditService.search(any(AuthorizationAuditQuery.class), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(), pageable, 0));
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);

        controller.list(12L, null, null, null, null, null, null, null, null, null, null, null, pageable);

        ArgumentCaptor<AuthorizationAuditQuery> captor = ArgumentCaptor.forClass(AuthorizationAuditQuery.class);
        verify(auditService).search(captor.capture(), eq(pageable));
        verify(accessGuard).assertPlatformTargetTenantReadable(12L, "system:audit:auth:view");
        assertThat(captor.getValue().tenantId()).isEqualTo(12L);
    }

    @Test
    void list_shouldRejectCrossTenantQueryInTenantScope() {
        AuthorizationAuditService auditService = mock(AuthorizationAuditService.class);
        AuthorizationAuditController controller = new AuthorizationAuditController(
            auditService,
            mock(TenantLifecycleAccessGuard.class)
        );
        var pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        TenantContext.setActiveTenantId(9L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);

        assertThatThrownBy(() -> controller.list(10L, null, null, null, null, null, null, null, null, null, null, null, pageable))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("当前作用域不允许查询其他租户的授权审计");
    }

    @Test
    void list_shouldRejectInvalidTimeRange() {
        AuthorizationAuditService auditService = mock(AuthorizationAuditService.class);
        AuthorizationAuditController controller = new AuthorizationAuditController(
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
        AuthorizationAuditService auditService = mock(AuthorizationAuditService.class);
        TenantLifecycleAccessGuard accessGuard = mock(TenantLifecycleAccessGuard.class);
        AuthorizationAuditController controller = new AuthorizationAuditController(auditService, accessGuard);
        when(auditService.summarize(any(AuthorizationAuditQuery.class)))
            .thenReturn(new AuthorizationAuditSummary(
                5L,
                4L,
                1L,
                List.of(new AuthorizationAuditSummary.EventTypeCount("ROLE_ASSIGNMENT_GRANT", 5L))
            ));
        TenantContext.setActiveTenantId(9L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);

        var response = controller.summary(
            null,
            " role_assignment_grant ",
            3L,
            7L,
            "denied",
            "system:user:assign-role",
            "manual freeze",
            " carrier-type-1 ",
            5,
            " ALLOW ",
            LocalDateTime.of(2026, 3, 1, 0, 0),
            LocalDateTime.of(2026, 3, 2, 0, 0)
        );

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().totalCount()).isEqualTo(5L);
        ArgumentCaptor<AuthorizationAuditQuery> captor = ArgumentCaptor.forClass(AuthorizationAuditQuery.class);
        verify(auditService).summarize(captor.capture());
        verify(accessGuard).assertPlatformTargetTenantReadable(9L, "system:audit:auth:view");
        AuthorizationAuditQuery query = captor.getValue();
        assertThat(query.tenantId()).isEqualTo(9L);
        assertThat(query.eventType()).isEqualTo("ROLE_ASSIGNMENT_GRANT");
        assertThat(query.actorUserId()).isEqualTo(3L);
        assertThat(query.targetUserId()).isEqualTo(7L);
        assertThat(query.result()).isEqualTo("DENIED");
        assertThat(query.resourcePermission()).isEqualTo("system:user:assign-role");
        assertThat(query.detailReason()).isEqualTo("manual freeze");
        assertThat(query.carrierType()).isEqualTo("carrier-type-1");
        assertThat(query.requirementGroup()).isEqualTo(5);
        assertThat(query.decision()).isEqualTo("ALLOW");
    }

    @Test
    void export_shouldReturnCsvContent() throws Exception {
        AuthorizationAuditService auditService = mock(AuthorizationAuditService.class);
        TenantLifecycleAccessGuard accessGuard = mock(TenantLifecycleAccessGuard.class);
        AuthorizationAuditController controller = new AuthorizationAuditController(auditService, accessGuard);
        AuthorizationAuditLog audit = sampleAudit();
        when(auditService.search(any(AuthorizationAuditQuery.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(audit), PageRequest.of(0, 10001, Sort.by(Sort.Direction.DESC, "createdAt")), 1));
        TenantContext.setActiveTenantId(9L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);

        ArgumentCaptor<AuthorizationAuditQuery> queryCaptor = ArgumentCaptor.forClass(AuthorizationAuditQuery.class);
        var response = controller.export(
            null,
            "role_assignment_grant",
            3L,
            7L,
            "SUCCESS",
            "system:user:assign-role",
            "manual freeze",
            " carrier-type-1 ",
            5,
            " ALLOW ",
            "incident-check",
            "TICKET-9",
            null,
            null
        );

        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("authorization-audit");
        StreamingResponseBody body = response.getBody();
        assertThat(body).isNotNull();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        body.writeTo(outputStream);
        String csv = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(csv).contains("ROLE_ASSIGNMENT_GRANT");
        assertThat(csv).contains("system:user:assign-role");
        assertThat(csv).contains("manual freeze");
        verify(auditService).search(queryCaptor.capture(), any(PageRequest.class));
        AuthorizationAuditQuery query = queryCaptor.getValue();
        assertThat(query.carrierType()).isEqualTo("carrier-type-1");
        assertThat(query.requirementGroup()).isEqualTo(5);
        assertThat(query.decision()).isEqualTo("ALLOW");
        verify(accessGuard).assertPlatformTargetTenantReadable(
            9L,
            "system:audit:auth:export",
            true,
            "incident-check",
            "TICKET-9"
        );
    }

    private static AuthorizationAuditLog sampleAudit() {
        AuthorizationAuditLog audit = new AuthorizationAuditLog();
        audit.setId(1L);
        audit.setTenantId(9L);
        audit.setEventType("ROLE_ASSIGNMENT_GRANT");
        audit.setActorUserId(3L);
        audit.setTargetUserId(7L);
        audit.setScopeType("TENANT");
        audit.setScopeId(9L);
        audit.setRoleId(11L);
        audit.setModule("iam");
        audit.setResourcePermission("system:user:assign-role");
        audit.setEventDetail("{\"action\":\"ROLE_ASSIGNMENT_GRANT\",\"reason\":\"manual freeze\",\"targetUserId\":7}");
        audit.setResult("SUCCESS");
        audit.setIpAddress("127.0.0.1");
        audit.setCreatedAt(LocalDateTime.of(2026, 3, 1, 10, 0));
        return audit;
    }
}
