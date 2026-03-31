package com.tiny.platform.infrastructure.auth.org.service;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.infrastructure.auth.audit.domain.AuthorizationAuditEventType;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditService;
import com.tiny.platform.infrastructure.auth.datascope.framework.DataScopeContext;
import com.tiny.platform.infrastructure.auth.datascope.framework.ResolvedDataScope;
import com.tiny.platform.infrastructure.auth.org.domain.OrganizationUnit;
import com.tiny.platform.infrastructure.auth.org.domain.UserUnit;
import com.tiny.platform.infrastructure.auth.org.repository.OrganizationUnitRepository;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserUnitServiceTest {

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void replaceUserUnits_should_reactivate_remove_missing_units_and_set_primary() {
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        OrganizationUnitRepository orgUnitRepository = mock(OrganizationUnitRepository.class);
        AuthorizationAuditService auditService = mock(AuthorizationAuditService.class);
        UserUnitService service = new UserUnitService(userUnitRepository, orgUnitRepository, auditService);

        Long tenantId = 1L;
        Long userId = 100L;

        OrganizationUnit dept10 = organizationUnit(10L, "DEPT", "平台运营部");
        OrganizationUnit org20 = organizationUnit(20L, "ORG", "平台事业群");

        UserUnit leftMembership = userUnit(tenantId, userId, 10L, false, "LEFT");
        leftMembership.setLeftAt(LocalDateTime.now().minusDays(1));
        UserUnit removedMembership = userUnit(tenantId, userId, 30L, true, "ACTIVE");

        when(orgUnitRepository.findByIdAndTenantId(10L, tenantId)).thenReturn(Optional.of(dept10));
        when(orgUnitRepository.findByIdAndTenantId(20L, tenantId)).thenReturn(Optional.of(org20));
        when(userUnitRepository.findByTenantIdAndUserIdAndUnitId(tenantId, userId, 10L)).thenReturn(Optional.of(leftMembership));
        when(userUnitRepository.findByTenantIdAndUserIdAndUnitId(tenantId, userId, 20L)).thenReturn(Optional.empty());
        when(userUnitRepository.findByTenantIdAndUserIdAndStatus(tenantId, userId, "ACTIVE"))
            .thenReturn(List.of(leftMembership, removedMembership, userUnit(tenantId, userId, 20L, true, "ACTIVE")));
        when(userUnitRepository.save(any(UserUnit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.replaceUserUnits(tenantId, userId, List.of(10L, 20L), 20L);

        assertThat(leftMembership.getStatus()).isEqualTo("ACTIVE");
        assertThat(leftMembership.getLeftAt()).isNull();
        assertThat(leftMembership.getIsPrimary()).isFalse();
        assertThat(removedMembership.getStatus()).isEqualTo("LEFT");
        assertThat(removedMembership.getIsPrimary()).isFalse();
        assertThat(removedMembership.getLeftAt()).isNotNull();

        verify(auditService, atLeastOnce()).logSuccess(
            eq(AuthorizationAuditEventType.USER_UNIT_ASSIGN),
            eq(tenantId),
            eq(userId),
            eq(null),
            eq("DEPT"),
            eq(10L),
            eq("{\"isPrimary\":false}")
        );
        verify(auditService, atLeastOnce()).logSuccess(
            eq(AuthorizationAuditEventType.USER_UNIT_ASSIGN),
            eq(tenantId),
            eq(userId),
            eq(null),
            eq("ORG"),
            eq(20L),
            eq("{\"isPrimary\":true}")
        );
        verify(auditService).logSuccess(
            AuthorizationAuditEventType.USER_UNIT_REMOVE,
            tenantId,
            userId,
            null,
            null,
            30L,
            null
        );
        verify(auditService).logSuccess(
            AuthorizationAuditEventType.USER_UNIT_SET_PRIMARY,
            tenantId,
            userId,
            null,
            null,
            20L,
            null
        );
    }

    @Test
    void replaceUserUnits_should_reject_primary_unit_outside_requested_units() {
        UserUnitService service = new UserUnitService(
            mock(UserUnitRepository.class),
            mock(OrganizationUnitRepository.class),
            mock(AuthorizationAuditService.class)
        );

        assertThatThrownBy(() -> service.replaceUserUnits(1L, 100L, List.of(10L), 20L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("主组织/部门必须包含在归属列表中");
    }

    @Test
    void listByUnitShouldReturnEmptyWhenUnitOutsideScope() {
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        UserUnitService service = new UserUnitService(
            userUnitRepository,
            mock(OrganizationUnitRepository.class),
            mock(AuthorizationAuditService.class)
        );

        DataScopeContext.set(ResolvedDataScope.ofUnitsAndUsers(java.util.Set.of(10L), java.util.Set.of(), false));

        assertThat(service.listByUnit(1L, 20L)).isEmpty();
    }

    @Test
    void listByUserShouldReturnMembershipsWhenUserBelongsToVisibleUnit() {
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        OrganizationUnitRepository orgUnitRepository = mock(OrganizationUnitRepository.class);
        AuthorizationAuditService auditService = mock(AuthorizationAuditService.class);
        UserUnitService service = new UserUnitService(userUnitRepository, orgUnitRepository, auditService);

        authenticate(8L);
        DataScopeContext.set(ResolvedDataScope.ofUnitsAndUsers(java.util.Set.of(10L), java.util.Set.of(), false));

        UserUnit membership = userUnit(1L, 100L, 10L, true, "ACTIVE");
        membership.setId(1L);
        when(userUnitRepository.findUnitIdsByTenantIdAndUserIdAndStatus(1L, 100L, "ACTIVE")).thenReturn(List.of(10L));
        when(userUnitRepository.findByTenantIdAndUserIdAndStatus(1L, 100L, "ACTIVE")).thenReturn(List.of(membership));
        when(orgUnitRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(organizationUnit(10L, "DEPT", "平台运营部")));

        var result = service.listByUser(1L, 100L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getUnitId()).isEqualTo(10L);
    }

    @Test
    void getPrimaryUnitShouldReturnEmptyWhenUserOutsideScope() {
        UserUnitService service = new UserUnitService(
            mock(UserUnitRepository.class),
            mock(OrganizationUnitRepository.class),
            mock(AuthorizationAuditService.class)
        );

        authenticate(8L);
        DataScopeContext.set(ResolvedDataScope.selfOnly());

        assertThat(service.getPrimaryUnit(1L, 100L)).isEmpty();
    }

    private OrganizationUnit organizationUnit(Long id, String unitType, String name) {
        OrganizationUnit unit = new OrganizationUnit();
        unit.setId(id);
        unit.setTenantId(1L);
        unit.setUnitType(unitType);
        unit.setCode("code-" + id);
        unit.setName(name);
        unit.setStatus("ACTIVE");
        return unit;
    }

    private UserUnit userUnit(Long tenantId, Long userId, Long unitId, boolean isPrimary, String status) {
        UserUnit userUnit = new UserUnit();
        userUnit.setTenantId(tenantId);
        userUnit.setUserId(userId);
        userUnit.setUnitId(unitId);
        userUnit.setIsPrimary(isPrimary);
        userUnit.setStatus(status);
        return userUnit;
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new SecurityUser(userId, 1L, "alice", "", List.of(), true, true, true, true),
            null,
            List.of()
        ));
    }
}
