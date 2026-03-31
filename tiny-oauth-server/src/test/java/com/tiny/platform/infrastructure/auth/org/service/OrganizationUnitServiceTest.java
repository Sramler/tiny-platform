package com.tiny.platform.infrastructure.auth.org.service;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditService;
import com.tiny.platform.infrastructure.auth.datascope.framework.DataScopeContext;
import com.tiny.platform.infrastructure.auth.datascope.framework.ResolvedDataScope;
import com.tiny.platform.infrastructure.auth.org.domain.OrganizationUnit;
import com.tiny.platform.infrastructure.auth.org.repository.OrganizationUnitRepository;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrganizationUnitServiceTest {

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void listShouldOnlyReturnDirectlyVisibleUnits() {
        OrganizationUnitRepository orgUnitRepository = mock(OrganizationUnitRepository.class);
        when(orgUnitRepository.findByTenantIdOrderBySortOrderAsc(1L)).thenReturn(List.of(
            unit(10L, null, "ORG", 100L),
            unit(20L, 10L, "DEPT", 200L),
            unit(30L, 10L, "DEPT", 300L)
        ));

        OrganizationUnitService service = new OrganizationUnitService(
            orgUnitRepository,
            mock(UserUnitRepository.class),
            mock(AuthorizationAuditService.class)
        );

        DataScopeContext.set(ResolvedDataScope.ofUnitsAndUsers(java.util.Set.of(20L), java.util.Set.of(), false));

        var result = service.list(1L);

        assertThat(result).extracting("id").containsExactly(20L);
    }

    @Test
    void getTreeShouldPreserveAncestorPathForVisibleUnit() {
        OrganizationUnitRepository orgUnitRepository = mock(OrganizationUnitRepository.class);
        when(orgUnitRepository.findByTenantIdOrderBySortOrderAsc(1L)).thenReturn(List.of(
            unit(10L, null, "ORG", 100L),
            unit(20L, 10L, "DEPT", 200L),
            unit(30L, 10L, "DEPT", 300L)
        ));

        OrganizationUnitService service = new OrganizationUnitService(
            orgUnitRepository,
            mock(UserUnitRepository.class),
            mock(AuthorizationAuditService.class)
        );

        DataScopeContext.set(ResolvedDataScope.ofUnitsAndUsers(java.util.Set.of(20L), java.util.Set.of(), false));

        var tree = service.getTree(1L);

        assertThat(tree).hasSize(1);
        assertThat(tree.getFirst().getId()).isEqualTo(10L);
        assertThat(tree.getFirst().getChildren()).extracting("id").containsExactly(20L);
    }

    @Test
    void getByIdShouldRejectUnitsOutsideDirectScope() {
        OrganizationUnitRepository orgUnitRepository = mock(OrganizationUnitRepository.class);
        when(orgUnitRepository.findByIdAndTenantId(30L, 1L)).thenReturn(java.util.Optional.of(unit(30L, 10L, "DEPT", 300L)));

        OrganizationUnitService service = new OrganizationUnitService(
            orgUnitRepository,
            mock(UserUnitRepository.class),
            mock(AuthorizationAuditService.class)
        );

        authenticate(8L);
        DataScopeContext.set(ResolvedDataScope.selfOnly());

        assertThatThrownBy(() -> service.getById(1L, 30L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("组织/部门节点不存在");
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new SecurityUser(userId, 1L, "alice", "", List.of(), true, true, true, true),
            null,
            List.of()
        ));
    }

    private OrganizationUnit unit(Long id, Long parentId, String unitType, Long createdBy) {
        OrganizationUnit unit = new OrganizationUnit();
        unit.setId(id);
        unit.setTenantId(1L);
        unit.setParentId(parentId);
        unit.setUnitType(unitType);
        unit.setCode("unit-" + id);
        unit.setName("unit-" + id);
        unit.setStatus("ACTIVE");
        unit.setCreatedBy(createdBy);
        return unit;
    }
}
