package com.tiny.platform.infrastructure.auth.datascope.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.datascope.domain.RoleDataScope;
import com.tiny.platform.infrastructure.auth.datascope.domain.RoleDataScopeItem;
import com.tiny.platform.infrastructure.auth.datascope.framework.ResolvedDataScope;
import com.tiny.platform.infrastructure.auth.datascope.repository.RoleDataScopeItemRepository;
import com.tiny.platform.infrastructure.auth.datascope.repository.RoleDataScopeRepository;
import com.tiny.platform.infrastructure.auth.org.domain.OrganizationUnit;
import com.tiny.platform.infrastructure.auth.org.domain.UserUnit;
import com.tiny.platform.infrastructure.auth.org.repository.OrganizationUnitRepository;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.auth.role.service.EffectiveRoleResolutionService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DataScopeResolverServiceTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void resolve_should_use_effective_role_ids_including_inherited_roles() {
        RoleDataScopeRepository dataScopeRepository = mock(RoleDataScopeRepository.class);
        RoleDataScopeItemRepository itemRepository = mock(RoleDataScopeItemRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        OrganizationUnitRepository orgUnitRepository = mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        DataScopeResolverService service = new DataScopeResolverService(
                dataScopeRepository,
                itemRepository,
                effectiveRoleResolutionService,
                orgUnitRepository,
                userUnitRepository
        );

        RoleDataScope inheritedRule = new RoleDataScope();
        inheritedRule.setId(9L);
        inheritedRule.setTenantId(7L);
        inheritedRule.setRoleId(200L);
        inheritedRule.setModule("user");
        inheritedRule.setAccessType("READ");
        inheritedRule.setScopeType("SELF");

        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(11L, 7L)).thenReturn(List.of(100L, 200L));
        when(dataScopeRepository.findByTenantIdAndModuleAndAccessTypeAndRoleIdIn(7L, "user", "READ", List.of(100L, 200L)))
                .thenReturn(List.of(inheritedRule));

        ResolvedDataScope resolved = service.resolve(11L, 7L, "user", "READ");

        assertThat(resolved.isSelfOnly()).isTrue();
        verify(effectiveRoleResolutionService).findEffectiveRoleIdsForUserInTenant(11L, 7L);
    }

    @Test
    void resolve_should_collect_custom_targets() {
        RoleDataScopeRepository dataScopeRepository = mock(RoleDataScopeRepository.class);
        RoleDataScopeItemRepository itemRepository = mock(RoleDataScopeItemRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        OrganizationUnitRepository orgUnitRepository = mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        DataScopeResolverService service = new DataScopeResolverService(
                dataScopeRepository,
                itemRepository,
                effectiveRoleResolutionService,
                orgUnitRepository,
                userUnitRepository
        );

        RoleDataScope customRule = new RoleDataScope();
        customRule.setId(21L);
        customRule.setTenantId(8L);
        customRule.setRoleId(300L);
        customRule.setModule("user");
        customRule.setAccessType("READ");
        customRule.setScopeType("CUSTOM");

        RoleDataScopeItem customUser = new RoleDataScopeItem();
        customUser.setRoleDataScopeId(21L);
        customUser.setTargetType("USER");
        customUser.setTargetId(901L);

        RoleDataScopeItem customUnit = new RoleDataScopeItem();
        customUnit.setRoleDataScopeId(21L);
        customUnit.setTargetType("DEPT");
        customUnit.setTargetId(902L);

        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(12L, 8L)).thenReturn(List.of(300L));
        when(dataScopeRepository.findByTenantIdAndModuleAndAccessTypeAndRoleIdIn(8L, "user", "READ", List.of(300L)))
                .thenReturn(List.of(customRule));
        when(itemRepository.findByRoleDataScopeId(21L)).thenReturn(List.of(customUser, customUnit));

        ResolvedDataScope resolved = service.resolve(12L, 8L, "user", "READ");

        assertThat(resolved.getVisibleUserIds()).containsExactly(901L);
        assertThat(resolved.getVisibleUnitIds()).containsExactly(902L);
        assertThat(resolved.isSelfOnly()).isFalse();
    }

    @Test
    void resolve_should_call_scoped_effective_roles_when_org_active_scope() {
        RoleDataScopeRepository dataScopeRepository = mock(RoleDataScopeRepository.class);
        RoleDataScopeItemRepository itemRepository = mock(RoleDataScopeItemRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        OrganizationUnitRepository orgUnitRepository = mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        DataScopeResolverService service = new DataScopeResolverService(
                dataScopeRepository,
                itemRepository,
                effectiveRoleResolutionService,
                orgUnitRepository,
                userUnitRepository
        );

        TenantContext.setActiveTenantId(7L);
        TenantContext.setActiveScopeType("ORG");
        TenantContext.setActiveScopeId(500L);

        RoleDataScope rule = new RoleDataScope();
        rule.setId(1L);
        rule.setTenantId(7L);
        rule.setRoleId(999L);
        rule.setModule("user");
        rule.setAccessType("READ");
        rule.setScopeType("SELF");

        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(11L, 7L, "ORG", 500L))
                .thenReturn(List.of(999L));
        when(dataScopeRepository.findByTenantIdAndModuleAndAccessTypeAndRoleIdIn(7L, "user", "READ", List.of(999L)))
                .thenReturn(List.of(rule));

        ResolvedDataScope resolved = service.resolve(11L, 7L, "user", "READ");

        assertThat(resolved.isSelfOnly()).isTrue();
        verify(effectiveRoleResolutionService).findEffectiveRoleIdsForUserInTenant(11L, 7L, "ORG", 500L);
    }

    /** 与 user 模块相同：menu 控制面读链在 DEPT active scope 下走成对角色解析（扩面证据）。 */
    @Test
    void resolve_menu_module_uses_scoped_effective_roles_when_dept_active_scope() {
        RoleDataScopeRepository dataScopeRepository = mock(RoleDataScopeRepository.class);
        RoleDataScopeItemRepository itemRepository = mock(RoleDataScopeItemRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        OrganizationUnitRepository orgUnitRepository = mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        DataScopeResolverService service = new DataScopeResolverService(
                dataScopeRepository,
                itemRepository,
                effectiveRoleResolutionService,
                orgUnitRepository,
                userUnitRepository
        );

        TenantContext.setActiveTenantId(7L);
        TenantContext.setActiveScopeType("DEPT");
        TenantContext.setActiveScopeId(400L);

        RoleDataScope rule = new RoleDataScope();
        rule.setId(1L);
        rule.setTenantId(7L);
        rule.setRoleId(888L);
        rule.setModule("menu");
        rule.setAccessType("READ");
        rule.setScopeType("SELF");

        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(11L, 7L, "DEPT", 400L))
                .thenReturn(List.of(888L));
        when(dataScopeRepository.findByTenantIdAndModuleAndAccessTypeAndRoleIdIn(7L, "menu", "READ", List.of(888L)))
                .thenReturn(List.of(rule));

        ResolvedDataScope resolved = service.resolve(11L, 7L, "menu", "READ");

        assertThat(resolved.isSelfOnly()).isTrue();
        verify(effectiveRoleResolutionService).findEffectiveRoleIdsForUserInTenant(11L, 7L, "DEPT", 400L);
    }

    /** dict 控制面读链在 ORG active scope 下与 user/menu 相同走成对角色解析（扩面证据）。 */
    @Test
    void resolve_dict_module_uses_scoped_effective_roles_when_org_active_scope() {
        RoleDataScopeRepository dataScopeRepository = mock(RoleDataScopeRepository.class);
        RoleDataScopeItemRepository itemRepository = mock(RoleDataScopeItemRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        OrganizationUnitRepository orgUnitRepository = mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        DataScopeResolverService service = new DataScopeResolverService(
                dataScopeRepository,
                itemRepository,
                effectiveRoleResolutionService,
                orgUnitRepository,
                userUnitRepository
        );

        TenantContext.setActiveTenantId(7L);
        TenantContext.setActiveScopeType("ORG");
        TenantContext.setActiveScopeId(500L);

        RoleDataScope rule = new RoleDataScope();
        rule.setId(1L);
        rule.setTenantId(7L);
        rule.setRoleId(777L);
        rule.setModule("dict");
        rule.setAccessType("READ");
        rule.setScopeType("SELF");

        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(11L, 7L, "ORG", 500L))
                .thenReturn(List.of(777L));
        when(dataScopeRepository.findByTenantIdAndModuleAndAccessTypeAndRoleIdIn(7L, "dict", "READ", List.of(777L)))
                .thenReturn(List.of(rule));

        ResolvedDataScope resolved = service.resolve(11L, 7L, "dict", "READ");

        assertThat(resolved.isSelfOnly()).isTrue();
        verify(effectiveRoleResolutionService).findEffectiveRoleIdsForUserInTenant(11L, 7L, "ORG", 500L);
    }

    /** dict 控制面读链在 DEPT active scope 下走成对角色解析（与 menu 扩面对齐）。 */
    @Test
    void resolve_dict_module_uses_scoped_effective_roles_when_dept_active_scope() {
        RoleDataScopeRepository dataScopeRepository = mock(RoleDataScopeRepository.class);
        RoleDataScopeItemRepository itemRepository = mock(RoleDataScopeItemRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        OrganizationUnitRepository orgUnitRepository = mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        DataScopeResolverService service = new DataScopeResolverService(
                dataScopeRepository,
                itemRepository,
                effectiveRoleResolutionService,
                orgUnitRepository,
                userUnitRepository
        );

        TenantContext.setActiveTenantId(7L);
        TenantContext.setActiveScopeType("DEPT");
        TenantContext.setActiveScopeId(400L);

        RoleDataScope rule = new RoleDataScope();
        rule.setId(1L);
        rule.setTenantId(7L);
        rule.setRoleId(888L);
        rule.setModule("dict");
        rule.setAccessType("READ");
        rule.setScopeType("SELF");

        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(11L, 7L, "DEPT", 400L))
                .thenReturn(List.of(888L));
        when(dataScopeRepository.findByTenantIdAndModuleAndAccessTypeAndRoleIdIn(7L, "dict", "READ", List.of(888L)))
                .thenReturn(List.of(rule));

        ResolvedDataScope resolved = service.resolve(11L, 7L, "dict", "READ");

        assertThat(resolved.isSelfOnly()).isTrue();
        verify(effectiveRoleResolutionService).findEffectiveRoleIdsForUserInTenant(11L, 7L, "DEPT", 400L);
    }

    @Test
    void resolve_should_anchor_org_rule_to_active_org_not_primary_unit() {
        RoleDataScopeRepository dataScopeRepository = mock(RoleDataScopeRepository.class);
        RoleDataScopeItemRepository itemRepository = mock(RoleDataScopeItemRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        OrganizationUnitRepository orgUnitRepository = mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        DataScopeResolverService service = new DataScopeResolverService(
                dataScopeRepository,
                itemRepository,
                effectiveRoleResolutionService,
                orgUnitRepository,
                userUnitRepository
        );

        TenantContext.setActiveTenantId(8L);
        TenantContext.setActiveScopeType("ORG");
        TenantContext.setActiveScopeId(600L);

        RoleDataScope orgRule = new RoleDataScope();
        orgRule.setId(2L);
        orgRule.setTenantId(8L);
        orgRule.setRoleId(300L);
        orgRule.setModule("user");
        orgRule.setAccessType("READ");
        orgRule.setScopeType("ORG");

        OrganizationUnit activeOrg = new OrganizationUnit();
        activeOrg.setId(600L);
        activeOrg.setTenantId(8L);
        activeOrg.setUnitType("ORG");

        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(12L, 8L, "ORG", 600L))
                .thenReturn(List.of(300L));
        when(dataScopeRepository.findByTenantIdAndModuleAndAccessTypeAndRoleIdIn(8L, "user", "READ", List.of(300L)))
                .thenReturn(List.of(orgRule));
        when(orgUnitRepository.findByIdAndTenantId(600L, 8L)).thenReturn(Optional.of(activeOrg));

        ResolvedDataScope resolved = service.resolve(12L, 8L, "user", "READ");

        assertThat(resolved.getVisibleUnitIds()).containsExactly(600L);
        verify(userUnitRepository, never()).findPrimaryByTenantIdAndUserId(8L, 12L);
    }

    /**
     * Contract B: ORG active scope + pure DEPT rule → geometry anchors to primary department, not active org id.
     */
    @Test
    void resolve_contract_b_org_active_scope_pure_dept_rule_anchors_primary_department_not_active_org() {
        RoleDataScopeRepository dataScopeRepository = mock(RoleDataScopeRepository.class);
        RoleDataScopeItemRepository itemRepository = mock(RoleDataScopeItemRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        OrganizationUnitRepository orgUnitRepository = mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        DataScopeResolverService service = new DataScopeResolverService(
                dataScopeRepository,
                itemRepository,
                effectiveRoleResolutionService,
                orgUnitRepository,
                userUnitRepository
        );

        TenantContext.setActiveTenantId(8L);
        TenantContext.setActiveScopeType("ORG");
        TenantContext.setActiveScopeId(600L);

        RoleDataScope deptRule = new RoleDataScope();
        deptRule.setId(44L);
        deptRule.setTenantId(8L);
        deptRule.setRoleId(304L);
        deptRule.setModule("user");
        deptRule.setAccessType("READ");
        deptRule.setScopeType("DEPT");

        UserUnit primaryUu = new UserUnit();
        primaryUu.setUnitId(50L);
        OrganizationUnit primaryDept = new OrganizationUnit();
        primaryDept.setId(50L);
        primaryDept.setTenantId(8L);
        primaryDept.setUnitType("DEPT");

        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(12L, 8L, "ORG", 600L))
                .thenReturn(List.of(304L));
        when(dataScopeRepository.findByTenantIdAndModuleAndAccessTypeAndRoleIdIn(8L, "user", "READ", List.of(304L)))
                .thenReturn(List.of(deptRule));
        when(userUnitRepository.findPrimaryByTenantIdAndUserId(8L, 12L)).thenReturn(Optional.of(primaryUu));
        when(orgUnitRepository.findByIdAndTenantId(50L, 8L)).thenReturn(Optional.of(primaryDept));

        ResolvedDataScope resolved = service.resolve(12L, 8L, "user", "READ");

        assertThat(resolved.getVisibleUnitIds()).containsExactly(50L);
        verify(orgUnitRepository, never()).findByIdAndTenantId(600L, 8L);
    }

    /** Contract B: ORG active scope + DEPT_AND_CHILD uses primary dept and its subtree — not active org as DEPT anchor. */
    @Test
    void resolve_contract_b_org_active_scope_dept_and_child_anchors_primary_dept_tree() {
        RoleDataScopeRepository dataScopeRepository = mock(RoleDataScopeRepository.class);
        RoleDataScopeItemRepository itemRepository = mock(RoleDataScopeItemRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        OrganizationUnitRepository orgUnitRepository = mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        DataScopeResolverService service = new DataScopeResolverService(
                dataScopeRepository,
                itemRepository,
                effectiveRoleResolutionService,
                orgUnitRepository,
                userUnitRepository
        );

        TenantContext.setActiveTenantId(8L);
        TenantContext.setActiveScopeType("ORG");
        TenantContext.setActiveScopeId(600L);

        RoleDataScope deptChildRule = new RoleDataScope();
        deptChildRule.setId(45L);
        deptChildRule.setTenantId(8L);
        deptChildRule.setRoleId(305L);
        deptChildRule.setModule("user");
        deptChildRule.setAccessType("READ");
        deptChildRule.setScopeType("DEPT_AND_CHILD");

        UserUnit primaryUu = new UserUnit();
        primaryUu.setUnitId(50L);
        OrganizationUnit primaryDept = new OrganizationUnit();
        primaryDept.setId(50L);
        primaryDept.setTenantId(8L);
        primaryDept.setUnitType("DEPT");

        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(12L, 8L, "ORG", 600L))
                .thenReturn(List.of(305L));
        when(dataScopeRepository.findByTenantIdAndModuleAndAccessTypeAndRoleIdIn(8L, "user", "READ", List.of(305L)))
                .thenReturn(List.of(deptChildRule));
        when(userUnitRepository.findPrimaryByTenantIdAndUserId(8L, 12L)).thenReturn(Optional.of(primaryUu));
        when(orgUnitRepository.findByIdAndTenantId(50L, 8L)).thenReturn(Optional.of(primaryDept));
        when(orgUnitRepository.findChildIdsByTenantIdAndParentId(8L, 50L)).thenReturn(List.of(51L));
        when(orgUnitRepository.findChildIdsByTenantIdAndParentId(8L, 51L)).thenReturn(List.of());

        ResolvedDataScope resolved = service.resolve(12L, 8L, "user", "READ");

        assertThat(resolved.getVisibleUnitIds()).containsExactlyInAnyOrder(50L, 51L);
        verify(orgUnitRepository, never()).findByIdAndTenantId(600L, 8L);
    }

    @Test
    void resolve_should_anchor_dept_rule_to_active_dept_scope() {
        RoleDataScopeRepository dataScopeRepository = mock(RoleDataScopeRepository.class);
        RoleDataScopeItemRepository itemRepository = mock(RoleDataScopeItemRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        OrganizationUnitRepository orgUnitRepository = mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        DataScopeResolverService service = new DataScopeResolverService(
                dataScopeRepository,
                itemRepository,
                effectiveRoleResolutionService,
                orgUnitRepository,
                userUnitRepository
        );

        TenantContext.setActiveTenantId(8L);
        TenantContext.setActiveScopeType("DEPT");
        TenantContext.setActiveScopeId(700L);

        RoleDataScope deptRule = new RoleDataScope();
        deptRule.setId(3L);
        deptRule.setTenantId(8L);
        deptRule.setRoleId(301L);
        deptRule.setModule("user");
        deptRule.setAccessType("READ");
        deptRule.setScopeType("DEPT");

        OrganizationUnit dept = new OrganizationUnit();
        dept.setId(700L);
        dept.setTenantId(8L);
        dept.setUnitType("DEPT");

        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(12L, 8L, "DEPT", 700L))
                .thenReturn(List.of(301L));
        when(dataScopeRepository.findByTenantIdAndModuleAndAccessTypeAndRoleIdIn(8L, "user", "READ", List.of(301L)))
                .thenReturn(List.of(deptRule));
        when(orgUnitRepository.findByIdAndTenantId(700L, 8L)).thenReturn(Optional.of(dept));

        ResolvedDataScope resolved = service.resolve(12L, 8L, "user", "READ");

        assertThat(resolved.getVisibleUnitIds()).containsExactly(700L);
        verify(userUnitRepository, never()).findPrimaryByTenantIdAndUserId(8L, 12L);
    }

    @Test
    void resolve_scheduling_read_should_use_tenant_scoped_roles_when_tenant_active_scope() {
        RoleDataScopeRepository dataScopeRepository = mock(RoleDataScopeRepository.class);
        RoleDataScopeItemRepository itemRepository = mock(RoleDataScopeItemRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        OrganizationUnitRepository orgUnitRepository = mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        DataScopeResolverService service = new DataScopeResolverService(
                dataScopeRepository,
                itemRepository,
                effectiveRoleResolutionService,
                orgUnitRepository,
                userUnitRepository
        );

        TenantContext.setActiveTenantId(7L);
        TenantContext.setActiveScopeType("TENANT");
        TenantContext.setActiveScopeId(7L);

        RoleDataScope rule = new RoleDataScope();
        rule.setId(40L);
        rule.setTenantId(7L);
        rule.setRoleId(400L);
        rule.setModule("scheduling");
        rule.setAccessType("READ");
        rule.setScopeType("SELF");

        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(11L, 7L)).thenReturn(List.of(400L));
        when(dataScopeRepository.findByTenantIdAndModuleAndAccessTypeAndRoleIdIn(7L, "scheduling", "READ", List.of(400L)))
                .thenReturn(List.of(rule));

        ResolvedDataScope resolved = service.resolve(11L, 7L, "scheduling", "READ");

        assertThat(resolved.isSelfOnly()).isTrue();
        verify(effectiveRoleResolutionService).findEffectiveRoleIdsForUserInTenant(11L, 7L);
    }

    @Test
    void resolve_scheduling_read_should_use_org_scoped_roles_when_org_active_scope() {
        RoleDataScopeRepository dataScopeRepository = mock(RoleDataScopeRepository.class);
        RoleDataScopeItemRepository itemRepository = mock(RoleDataScopeItemRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        OrganizationUnitRepository orgUnitRepository = mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        DataScopeResolverService service = new DataScopeResolverService(
                dataScopeRepository,
                itemRepository,
                effectiveRoleResolutionService,
                orgUnitRepository,
                userUnitRepository
        );

        TenantContext.setActiveTenantId(7L);
        TenantContext.setActiveScopeType("ORG");
        TenantContext.setActiveScopeId(500L);

        RoleDataScope rule = new RoleDataScope();
        rule.setId(41L);
        rule.setTenantId(7L);
        rule.setRoleId(401L);
        rule.setModule("scheduling");
        rule.setAccessType("READ");
        rule.setScopeType("SELF");

        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(11L, 7L, "ORG", 500L))
                .thenReturn(List.of(401L));
        when(dataScopeRepository.findByTenantIdAndModuleAndAccessTypeAndRoleIdIn(7L, "scheduling", "READ", List.of(401L)))
                .thenReturn(List.of(rule));

        ResolvedDataScope resolved = service.resolve(11L, 7L, "scheduling", "READ");

        assertThat(resolved.isSelfOnly()).isTrue();
        verify(effectiveRoleResolutionService).findEffectiveRoleIdsForUserInTenant(11L, 7L, "ORG", 500L);
    }

    @Test
    void resolve_export_read_should_use_dept_scoped_roles_when_dept_active_scope() {
        RoleDataScopeRepository dataScopeRepository = mock(RoleDataScopeRepository.class);
        RoleDataScopeItemRepository itemRepository = mock(RoleDataScopeItemRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        OrganizationUnitRepository orgUnitRepository = mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        DataScopeResolverService service = new DataScopeResolverService(
                dataScopeRepository,
                itemRepository,
                effectiveRoleResolutionService,
                orgUnitRepository,
                userUnitRepository
        );

        TenantContext.setActiveTenantId(8L);
        TenantContext.setActiveScopeType("DEPT");
        TenantContext.setActiveScopeId(702L);

        RoleDataScope rule = new RoleDataScope();
        rule.setId(42L);
        rule.setTenantId(8L);
        rule.setRoleId(402L);
        rule.setModule("export");
        rule.setAccessType("READ");
        rule.setScopeType("SELF");

        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(12L, 8L, "DEPT", 702L))
                .thenReturn(List.of(402L));
        when(dataScopeRepository.findByTenantIdAndModuleAndAccessTypeAndRoleIdIn(8L, "export", "READ", List.of(402L)))
                .thenReturn(List.of(rule));

        ResolvedDataScope resolved = service.resolve(12L, 8L, "export", "READ");

        assertThat(resolved.isSelfOnly()).isTrue();
        verify(effectiveRoleResolutionService).findEffectiveRoleIdsForUserInTenant(12L, 8L, "DEPT", 702L);
    }
}
