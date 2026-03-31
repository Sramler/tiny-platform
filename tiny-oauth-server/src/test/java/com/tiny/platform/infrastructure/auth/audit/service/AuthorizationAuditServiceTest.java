package com.tiny.platform.infrastructure.auth.audit.service;

import com.tiny.platform.infrastructure.auth.audit.domain.AuthorizationAuditLog;
import com.tiny.platform.infrastructure.auth.audit.domain.RequirementAwareAuditDetail;
import com.tiny.platform.infrastructure.auth.audit.repository.AuthorizationAuditLogRepository;
import com.tiny.platform.infrastructure.auth.audit.domain.AuthorizationAuditEventType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;

class AuthorizationAuditServiceTest {

    @Test
    void summarize_shouldAggregateFilteredRows() {
        AuthorizationAuditLogRepository repository = mock(AuthorizationAuditLogRepository.class);
        AuthorizationAuditService service = new AuthorizationAuditService(repository, new ObjectMapper());
        AuthorizationAuditQuery query = new AuthorizationAuditQuery(
            9L,
            "ROLE_ASSIGNMENT_GRANT",
            3L,
            7L,
            "SUCCESS",
            "system:user:assign-role",
            "manual freeze",
            null,
            null,
            null,
            LocalDateTime.of(2026, 3, 1, 0, 0),
            LocalDateTime.of(2026, 3, 2, 0, 0)
        );

        AuthorizationAuditLog success = new AuthorizationAuditLog();
        success.setEventType("ROLE_ASSIGNMENT_GRANT");
        success.setResult("SUCCESS");
        success.setEventDetail("{\"reason\":\"manual freeze\"}");
        AuthorizationAuditLog denied = new AuthorizationAuditLog();
        denied.setEventType("ROLE_ASSIGNMENT_GRANT");
        denied.setResult("DENIED");
        denied.setEventDetail("{\"reason\":\"manual freeze\"}");

        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(success, denied));

        AuthorizationAuditSummary summary = service.summarize(query);

        assertThat(summary.totalCount()).isEqualTo(2L);
        assertThat(summary.successCount()).isEqualTo(1L);
        assertThat(summary.deniedCount()).isEqualTo(1L);
        assertThat(summary.eventTypeCounts()).containsExactly(
            new AuthorizationAuditSummary.EventTypeCount("ROLE_ASSIGNMENT_GRANT", 2L)
        );
    }

    @Test
    void logRequirementAware_shouldPersistAllowDetail() throws Exception {
        AuthorizationAuditLogRepository repository = mock(AuthorizationAuditLogRepository.class);
        when(repository.save(any(AuthorizationAuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthorizationAuditService service = new AuthorizationAuditService(repository, new ObjectMapper());
        RequirementAwareAuditDetail detail = new RequirementAwareAuditDetail(
            "menu",
            10L,
            2,
            List.of("system:user:list"),
            List.of(),
            List.of(),
            "ALLOW",
            "REQUIREMENT_GROUP_SATISFIED"
        );

        AuthorizationAuditLog saved = service.logRequirementAware(
            AuthorizationAuditEventType.REQUIREMENT_AWARE_ACCESS,
            9L,
            "requirement-aware",
            detail
        );

        verify(repository, atLeastOnce()).save(any(AuthorizationAuditLog.class));
        assertThat(saved.getResult()).isEqualTo("SUCCESS");

        ObjectMapper om = new ObjectMapper();
        JsonNode node = om.readTree(saved.getEventDetail());
        assertThat(node.get("carrierType").asText()).isEqualTo("menu");
        assertThat(node.get("requirementGroup").asInt()).isEqualTo(2);
        assertThat(node.get("matchedPermissionCodes").isArray()).isTrue();
        assertThat(node.get("matchedPermissionCodes").get(0).asText()).isEqualTo("system:user:list");
        assertThat(node.get("decision").asText()).isEqualTo("ALLOW");
    }

    @Test
    void logRequirementAware_shouldPersistDenyDetail() throws Exception {
        AuthorizationAuditLogRepository repository = mock(AuthorizationAuditLogRepository.class);
        when(repository.save(any(AuthorizationAuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthorizationAuditService service = new AuthorizationAuditService(repository, new ObjectMapper());
        RequirementAwareAuditDetail detail = new RequirementAwareAuditDetail(
            "ui_action",
            22L,
            1,
            List.of(),
            List.of("system:user:create"),
            List.of(),
            "DENY",
            "REQUIREMENT_NOT_SATISFIED"
        );

        AuthorizationAuditLog saved = service.logRequirementAware(
            AuthorizationAuditEventType.REQUIREMENT_AWARE_ACCESS,
            9L,
            "requirement-aware",
            detail
        );

        assertThat(saved.getResult()).isEqualTo("DENIED");

        ObjectMapper om = new ObjectMapper();
        JsonNode node = om.readTree(saved.getEventDetail());
        assertThat(node.get("carrierType").asText()).isEqualTo("ui_action");
        assertThat(node.get("requirementGroup").asInt()).isEqualTo(1);
        assertThat(node.get("matchedPermissionCodes").isArray()).isTrue();
        assertThat(node.get("decision").asText()).isEqualTo("DENY");
    }
}
