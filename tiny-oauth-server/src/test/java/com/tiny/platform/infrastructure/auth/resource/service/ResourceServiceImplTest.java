package com.tiny.platform.infrastructure.auth.resource.service;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.auth.datascope.framework.DataScopeContext;
import com.tiny.platform.infrastructure.auth.datascope.framework.ResolvedDataScope;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.auth.resource.domain.ApiEndpointEntry;
import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.domain.UiActionEntry;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceRequestDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import com.tiny.platform.infrastructure.auth.resource.repository.ApiEndpointPermissionRequirementRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.CarrierPermissionRequirementRow;
import com.tiny.platform.infrastructure.auth.resource.repository.ApiEndpointEntryRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.UiActionEntryRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.UiActionPermissionRequirementRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.role.service.EffectiveRoleResolutionService;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.menu.repository.MenuEntryRepository;
import com.tiny.platform.infrastructure.menu.repository.MenuPermissionRequirementRepository;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditService;
import com.tiny.platform.infrastructure.auth.audit.domain.AuthorizationAuditEventType;
import com.tiny.platform.infrastructure.auth.audit.domain.RequirementAwareAuditDetail;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.tiny.platform.infrastructure.auth.datascope.framework.ResolvedDataScope;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResourceServiceImplTest {
    private AuthorizationAuditService authorizationAuditService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        DataScopeContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void findByUserId_should_prefer_assignment_backed_role_ids() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        MenuPermissionRequirementRepository menuPermissionRequirementRepository =
            mock(MenuPermissionRequirementRepository.class);
        UiActionPermissionRequirementRepository uiActionPermissionRequirementRepository =
            mock(UiActionPermissionRequirementRepository.class);
        ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository =
            mock(ApiEndpointPermissionRequirementRepository.class);
        CarrierPermissionRequirementEvaluator evaluator = new CarrierPermissionRequirementEvaluator(
            menuPermissionRequirementRepository,
            uiActionPermissionRequirementRepository,
            apiEndpointPermissionRequirementRepository
        );
        authorizationAuditService = mock(AuthorizationAuditService.class);
        ResourceServiceImpl service = new ResourceServiceImpl(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService,
            evaluator,
            authorizationAuditService
        );

        TenantContext.setActiveTenantId(1L);

        com.tiny.platform.infrastructure.menu.domain.MenuEntry menu = new com.tiny.platform.infrastructure.menu.domain.MenuEntry();
        menu.setId(9L);
        menu.setTenantId(1L);
        menu.setResourceLevel("TENANT");
        menu.setType(ResourceType.MENU.getCode());
        menu.setName("menu-9");
        menu.setTitle("菜单9");
        menu.setPath("/menu-9");
        menu.setSort(1);
        menu.setRequiredPermissionId(7001L);

        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(5L, 1L)).thenReturn(List.of(100L));
        when(roleRepository.findPermissionIdsByRoleIdsAndTenantId(List.of(100L), 1L)).thenReturn(List.of(7001L));
        when(menuEntryRepository.findByRequiredPermissionIdInAndScope(any(), eq(1L), eq("TENANT")))
            .thenReturn(List.of(menu));
        when(uiActionEntryRepository.findByRequiredPermissionIdInAndScope(any(), eq(1L), eq("TENANT")))
            .thenReturn(List.of());
        when(apiEndpointEntryRepository.findByRequiredPermissionIdInAndScope(any(), eq(1L), eq("TENANT")))
            .thenReturn(List.of());

        List<Resource> result = service.findByUserId(5L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(9L);
        verify(effectiveRoleResolutionService).findEffectiveRoleIdsForUserInTenant(5L, 1L);
        verify(roleRepository, never()).findResourceIdsByRoleId(any());
    }

    @Test
    void findByRoleId_should_load_mixed_carriers_by_permission_ids_without_resource_table_main_read() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        MenuPermissionRequirementRepository menuPermissionRequirementRepository = mock(MenuPermissionRequirementRepository.class);
        UiActionPermissionRequirementRepository uiActionPermissionRequirementRepository = mock(UiActionPermissionRequirementRepository.class);
        ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository = mock(ApiEndpointPermissionRequirementRepository.class);
        CarrierPermissionRequirementEvaluator evaluator = new CarrierPermissionRequirementEvaluator(
            menuPermissionRequirementRepository,
            uiActionPermissionRequirementRepository,
            apiEndpointPermissionRequirementRepository
        );
        authorizationAuditService = mock(AuthorizationAuditService.class);
        ResourceServiceImpl service = new ResourceServiceImpl(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService,
            evaluator,
            authorizationAuditService
        );

        TenantContext.setActiveTenantId(8L);
        when(roleRepository.findPermissionIdsByRoleIdAndTenantId(200L, 8L)).thenReturn(List.of(7001L, 7002L, 7003L));

        com.tiny.platform.infrastructure.menu.domain.MenuEntry menu = new com.tiny.platform.infrastructure.menu.domain.MenuEntry();
        menu.setId(11L);
        menu.setTenantId(8L);
        menu.setResourceLevel("TENANT");
        menu.setType(ResourceType.MENU.getCode());
        menu.setName("menu");
        menu.setTitle("菜单");
        menu.setPath("/menu");
        menu.setSort(1);
        menu.setRequiredPermissionId(7001L);

        UiActionEntry action = new UiActionEntry();
        action.setId(12L);
        action.setTenantId(8L);
        action.setResourceLevel("TENANT");
        action.setName("action");
        action.setTitle("按钮");
        action.setSort(2);
        action.setRequiredPermissionId(7002L);

        ApiEndpointEntry endpoint = new ApiEndpointEntry();
        endpoint.setId(13L);
        endpoint.setTenantId(8L);
        endpoint.setResourceLevel("TENANT");
        endpoint.setName("api");
        endpoint.setTitle("接口");
        endpoint.setUri("/sys/demo");
        endpoint.setMethod("GET");
        endpoint.setRequiredPermissionId(7003L);

        when(menuEntryRepository.findByRequiredPermissionIdInAndScope(any(), eq(8L), eq("TENANT")))
            .thenReturn(List.of(menu));
        when(uiActionEntryRepository.findByRequiredPermissionIdInAndScope(any(), eq(8L), eq("TENANT")))
            .thenReturn(List.of(action));
        when(apiEndpointEntryRepository.findByRequiredPermissionIdInAndScope(any(), eq(8L), eq("TENANT")))
            .thenReturn(List.of(endpoint));

        List<Resource> result = service.findByRoleId(200L);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(Resource::getType)
            .containsExactly(ResourceType.API, ResourceType.MENU, ResourceType.BUTTON);
        verify(roleRepository, never()).findResourceIdsByRoleId(any());
    }

    @Test
    void resources_should_expose_record_tenant_id_in_response_dto() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        MenuPermissionRequirementRepository menuPermissionRequirementRepository = mock(MenuPermissionRequirementRepository.class);
        UiActionPermissionRequirementRepository uiActionPermissionRequirementRepository = mock(UiActionPermissionRequirementRepository.class);
        ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository = mock(ApiEndpointPermissionRequirementRepository.class);
        CarrierPermissionRequirementEvaluator evaluator = new CarrierPermissionRequirementEvaluator(
            menuPermissionRequirementRepository,
            uiActionPermissionRequirementRepository,
            apiEndpointPermissionRequirementRepository
        );
        authorizationAuditService = mock(AuthorizationAuditService.class);
        ResourceServiceImpl service = new ResourceServiceImpl(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService,
            evaluator,
            authorizationAuditService
        );

        TenantContext.setActiveTenantId(8L);

        com.tiny.platform.infrastructure.menu.domain.MenuEntry menu = new com.tiny.platform.infrastructure.menu.domain.MenuEntry();
        menu.setId(3L);
        menu.setTenantId(8L);
        menu.setResourceLevel("TENANT");
        menu.setType(ResourceType.MENU.getCode());
        menu.setName("resource-3");
        menu.setTitle("资源3");
        menu.setPath("/resource-3");
        menu.setSort(1);

        PageRequest pageable = PageRequest.of(0, 10);
        when(menuEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(menu));
        when(uiActionEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of());
        when(apiEndpointEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of());

        var page = service.resources(new ResourceRequestDto(), pageable);

        assertThat(page.getContent()).hasSize(1);
        ResourceResponseDto dto = page.getContent().getFirst();
        assertThat(dto.getRecordTenantId()).isEqualTo(8L);
    }

    @Test
    void resources_should_apply_self_scoped_creator_filter() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        MenuPermissionRequirementRepository menuPermissionRequirementRepository = mock(MenuPermissionRequirementRepository.class);
        UiActionPermissionRequirementRepository uiActionPermissionRequirementRepository = mock(UiActionPermissionRequirementRepository.class);
        ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository = mock(ApiEndpointPermissionRequirementRepository.class);
        CarrierPermissionRequirementEvaluator evaluator = new CarrierPermissionRequirementEvaluator(
            menuPermissionRequirementRepository,
            uiActionPermissionRequirementRepository,
            apiEndpointPermissionRequirementRepository
        );
        authorizationAuditService = mock(AuthorizationAuditService.class);
        ResourceServiceImpl service = new ResourceServiceImpl(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService,
            evaluator,
            authorizationAuditService
        );

        TenantContext.setActiveTenantId(8L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new SecurityUser(21L, 8L, "resource-owner", "", List.of(), true, true, true, true),
            null,
            List.of()
        ));
        DataScopeContext.set(ResolvedDataScope.selfOnly());

        com.tiny.platform.infrastructure.menu.domain.MenuEntry menu = new com.tiny.platform.infrastructure.menu.domain.MenuEntry();
        menu.setId(5L);
        menu.setTenantId(8L);
        menu.setResourceLevel("TENANT");
        menu.setType(ResourceType.MENU.getCode());
        menu.setCreatedBy(21L);
        menu.setName("owned-resource");
        menu.setTitle("owned-resource");
        menu.setPath("/owned-resource");
        menu.setSort(1);

        PageRequest pageable = PageRequest.of(0, 10);
        when(tenantUserRepository.findUserIdsByTenantIdAndStatus(8L, "ACTIVE")).thenReturn(List.of(21L, 22L));
        when(menuEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(menu));
        when(uiActionEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of());
        when(apiEndpointEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of());

        var page = service.resources(new ResourceRequestDto(), pageable);

        assertThat(page.getContent()).extracting(ResourceResponseDto::getName).containsExactly("owned-resource");
        verify(tenantUserRepository).findUserIdsByTenantIdAndStatus(8L, "ACTIVE");
        verify(userUnitRepository, never()).findUserIdsByTenantIdAndUnitIdInAndStatus(any(), any(), any());
    }

    @Test
    void resources_should_apply_unit_scoped_creator_filter() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        MenuPermissionRequirementRepository menuPermissionRequirementRepository = mock(MenuPermissionRequirementRepository.class);
        UiActionPermissionRequirementRepository uiActionPermissionRequirementRepository = mock(UiActionPermissionRequirementRepository.class);
        ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository = mock(ApiEndpointPermissionRequirementRepository.class);
        CarrierPermissionRequirementEvaluator evaluator = new CarrierPermissionRequirementEvaluator(
            menuPermissionRequirementRepository,
            uiActionPermissionRequirementRepository,
            apiEndpointPermissionRequirementRepository
        );
        authorizationAuditService = mock(AuthorizationAuditService.class);
        ResourceServiceImpl service = new ResourceServiceImpl(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService,
            evaluator,
            authorizationAuditService
        );

        TenantContext.setActiveTenantId(8L);
        DataScopeContext.set(ResolvedDataScope.ofUnitsAndUsers(java.util.Set.of(300L), java.util.Set.of(), false));

        com.tiny.platform.infrastructure.menu.domain.MenuEntry menu = new com.tiny.platform.infrastructure.menu.domain.MenuEntry();
        menu.setId(6L);
        menu.setTenantId(8L);
        menu.setResourceLevel("TENANT");
        menu.setType(ResourceType.MENU.getCode());
        menu.setCreatedBy(22L);
        menu.setName("dept-resource");
        menu.setTitle("dept-resource");
        menu.setPath("/dept-resource");
        menu.setSort(1);

        PageRequest pageable = PageRequest.of(0, 10);
        when(tenantUserRepository.findUserIdsByTenantIdAndStatus(8L, "ACTIVE")).thenReturn(List.of(21L, 22L, 23L));
        when(userUnitRepository.findUserIdsByTenantIdAndUnitIdInAndStatus(8L, java.util.Set.of(300L), "ACTIVE"))
            .thenReturn(List.of(22L));
        when(menuEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(menu));
        when(uiActionEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of());
        when(apiEndpointEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of());

        var page = service.resources(new ResourceRequestDto(), pageable);

        assertThat(page.getContent()).extracting(ResourceResponseDto::getName).containsExactly("dept-resource");
        verify(userUnitRepository).findUserIdsByTenantIdAndUnitIdInAndStatus(8L, java.util.Set.of(300L), "ACTIVE");
    }

    @Test
    void resources_should_apply_custom_creator_filter() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        MenuPermissionRequirementRepository menuPermissionRequirementRepository = mock(MenuPermissionRequirementRepository.class);
        UiActionPermissionRequirementRepository uiActionPermissionRequirementRepository = mock(UiActionPermissionRequirementRepository.class);
        ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository = mock(ApiEndpointPermissionRequirementRepository.class);
        CarrierPermissionRequirementEvaluator evaluator = new CarrierPermissionRequirementEvaluator(
            menuPermissionRequirementRepository,
            uiActionPermissionRequirementRepository,
            apiEndpointPermissionRequirementRepository
        );
        authorizationAuditService = mock(AuthorizationAuditService.class);
        ResourceServiceImpl service = new ResourceServiceImpl(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService,
            evaluator,
            authorizationAuditService
        );

        TenantContext.setActiveTenantId(8L);
        DataScopeContext.set(ResolvedDataScope.ofUnitsAndUsers(java.util.Set.of(), java.util.Set.of(25L), false));

        com.tiny.platform.infrastructure.menu.domain.MenuEntry menu = new com.tiny.platform.infrastructure.menu.domain.MenuEntry();
        menu.setId(7L);
        menu.setTenantId(8L);
        menu.setResourceLevel("TENANT");
        menu.setType(ResourceType.MENU.getCode());
        menu.setCreatedBy(25L);
        menu.setName("custom-resource");
        menu.setTitle("custom-resource");
        menu.setPath("/custom-resource");
        menu.setSort(1);

        PageRequest pageable = PageRequest.of(0, 10);
        when(tenantUserRepository.findUserIdsByTenantIdAndStatus(8L, "ACTIVE")).thenReturn(List.of(24L, 25L));
        when(menuEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(menu));
        when(uiActionEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of());
        when(apiEndpointEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of());

        var page = service.resources(new ResourceRequestDto(), pageable);

        assertThat(page.getContent()).extracting(ResourceResponseDto::getName).containsExactly("custom-resource");
        verify(userUnitRepository, never()).findUserIdsByTenantIdAndUnitIdInAndStatus(any(), any(), any());
    }

    @Test
    void resources_should_ignore_tenant_membership_resolution_when_platform_scope() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        MenuPermissionRequirementRepository menuPermissionRequirementRepository = mock(MenuPermissionRequirementRepository.class);
        UiActionPermissionRequirementRepository uiActionPermissionRequirementRepository = mock(UiActionPermissionRequirementRepository.class);
        ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository = mock(ApiEndpointPermissionRequirementRepository.class);
        CarrierPermissionRequirementEvaluator evaluator = new CarrierPermissionRequirementEvaluator(
            menuPermissionRequirementRepository,
            uiActionPermissionRequirementRepository,
            apiEndpointPermissionRequirementRepository
        );
        authorizationAuditService = mock(AuthorizationAuditService.class);
        ResourceServiceImpl service = new ResourceServiceImpl(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService,
            evaluator,
            authorizationAuditService
        );

        TenantContext.setActiveTenantId(1L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new SecurityUser(21L, 1L, "platform-admin", "", List.of(), true, true, true, true),
            null,
            List.of()
        ));
        DataScopeContext.set(ResolvedDataScope.selfOnly());

        com.tiny.platform.infrastructure.menu.domain.MenuEntry menu = new com.tiny.platform.infrastructure.menu.domain.MenuEntry();
        menu.setId(15L);
        menu.setTenantId(null);
        menu.setResourceLevel("PLATFORM");
        menu.setType(ResourceType.MENU.getCode());
        menu.setCreatedBy(21L);
        menu.setName("platform-resource");
        menu.setTitle("平台资源");
        menu.setPath("/platform-resource");
        menu.setSort(1);

        PageRequest pageable = PageRequest.of(0, 10);
        when(menuEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(menu));
        when(uiActionEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of());
        when(apiEndpointEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of());

        var page = service.resources(new ResourceRequestDto(), pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().getRecordTenantId()).isNull();
        verify(tenantUserRepository, never()).findUserIdsByTenantIdAndStatus(any(), any());
        verify(userUnitRepository, never()).findUserIdsByTenantIdAndUnitIdInAndStatus(any(), any(), any());
    }

    @Test
    void resources_should_query_ui_action_projection_when_type_is_button() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        ResourceServiceImpl service = newService(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService
        );

        TenantContext.setActiveTenantId(8L);

        UiActionEntry action = new UiActionEntry();
        action.setId(81L);
        action.setTenantId(8L);
        action.setName("resource:button");
        action.setTitle("按钮资源");
        action.setPagePath("/resource");
        action.setPermission("system:resource:create");
        action.setParentMenuId(10L);
        action.setSort(2);
        action.setEnabled(true);

        ResourceRequestDto query = new ResourceRequestDto();
        query.setType(ResourceType.BUTTON.getCode());
        PageRequest pageable = PageRequest.of(0, 10);
        when(uiActionEntryRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(action), pageable, 1));

        var page = service.resources(query, pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().getType()).isEqualTo(ResourceType.BUTTON.getCode());
        assertThat(page.getContent().getFirst().getPermission()).isEqualTo("system:resource:create");
        assertThat(page.getContent().getFirst().getCarrierKind()).isEqualTo("ui_action");
        verify(uiActionEntryRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void resources_should_query_api_endpoint_projection_when_type_is_api() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        ResourceServiceImpl service = newService(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService
        );

        TenantContext.setActiveTenantId(8L);

        ApiEndpointEntry endpoint = new ApiEndpointEntry();
        endpoint.setId(91L);
        endpoint.setTenantId(8L);
        endpoint.setName("resource-api");
        endpoint.setTitle("资源 API");
        endpoint.setUri("/sys/resources");
        endpoint.setMethod("GET");
        endpoint.setPermission("system:resource:list");
        endpoint.setEnabled(true);

        ResourceRequestDto query = new ResourceRequestDto();
        query.setType(ResourceType.API.getCode());
        PageRequest pageable = PageRequest.of(0, 10);
        when(apiEndpointEntryRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(endpoint), pageable, 1));

        var page = service.resources(query, pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().getType()).isEqualTo(ResourceType.API.getCode());
        assertThat(page.getContent().getFirst().getUri()).isEqualTo("/sys/resources");
        assertThat(page.getContent().getFirst().getCarrierKind()).isEqualTo("api_endpoint");
        verify(apiEndpointEntryRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void findAllowedUiActionDtos_should_filter_runtime_actions_by_current_authorities() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        MenuPermissionRequirementRepository menuPermissionRequirementRepository = mock(MenuPermissionRequirementRepository.class);
        UiActionPermissionRequirementRepository uiActionPermissionRequirementRepository = mock(UiActionPermissionRequirementRepository.class);
        ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository = mock(ApiEndpointPermissionRequirementRepository.class);
        CarrierPermissionRequirementEvaluator evaluator = new CarrierPermissionRequirementEvaluator(
            menuPermissionRequirementRepository,
            uiActionPermissionRequirementRepository,
            apiEndpointPermissionRequirementRepository
        );
        authorizationAuditService = mock(AuthorizationAuditService.class);
        ResourceServiceImpl service = new ResourceServiceImpl(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService,
            evaluator,
            authorizationAuditService
        );

        TenantContext.setActiveTenantId(8L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new SecurityUser(21L, 8L, "resource-owner", "", List.of(() -> "system:resource:create"), true, true, true, true),
            null,
            List.of(() -> "system:resource:create")
        ));

        com.tiny.platform.infrastructure.menu.domain.MenuEntry menu = new com.tiny.platform.infrastructure.menu.domain.MenuEntry();
        menu.setId(10L);
        menu.setTenantId(8L);
        menu.setPath("/system/resource");
        menu.setType(ResourceType.MENU.getCode());
        menu.setResourceLevel("TENANT");
        menu.setEnabled(true);

        UiActionEntry create = new UiActionEntry();
        create.setId(81L);
        create.setTenantId(8L);
        create.setName("resource:create");
        create.setTitle("资源新增");
        create.setPermission("system:resource:create");
        create.setPagePath("/system/resource");
        create.setParentMenuId(10L);
        create.setSort(1);
        create.setEnabled(true);
        create.setResourceLevel("TENANT");

        UiActionEntry edit = new UiActionEntry();
        edit.setId(82L);
        edit.setTenantId(8L);
        edit.setName("resource:edit");
        edit.setTitle("资源编辑");
        edit.setPermission("system:resource:edit");
        edit.setPagePath("/system/resource");
        edit.setParentMenuId(10L);
        edit.setSort(2);
        edit.setEnabled(true);
        edit.setResourceLevel("TENANT");

        when(menuEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(menu));
        when(uiActionEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(create, edit));
        when(uiActionPermissionRequirementRepository.findRowsByUiActionIdIn(anyCollection())).thenReturn(List.of(
            requirementRow(81L, 1, 1, "system:resource:create", false, true),
            requirementRow(82L, 1, 1, "system:resource:edit", false, true)
        ));

        List<ResourceResponseDto> result = service.findAllowedUiActionDtos("/system/resource?tab=current");

        assertThat(result).extracting(ResourceResponseDto::getPermission).containsExactly("system:resource:create");
        assertThat(result).extracting(ResourceResponseDto::getCarrierKind).containsExactly("ui_action");

        ArgumentCaptor<RequirementAwareAuditDetail> detailCaptor =
            ArgumentCaptor.forClass(RequirementAwareAuditDetail.class);
        verify(authorizationAuditService, atLeastOnce()).logRequirementAware(
            eq(AuthorizationAuditEventType.REQUIREMENT_AWARE_ACCESS),
            eq(8L),
            eq("ui-action-runtime-gate"),
            detailCaptor.capture()
        );
        var details = detailCaptor.getAllValues();
        assertThat(details).anySatisfy(d -> {
            assertThat(d.carrierType()).isEqualTo("ui_action");
            assertThat(d.carrierId()).isEqualTo(81L);
            assertThat(d.decision()).isEqualTo("ALLOW");
            assertThat(d.matchedPermissionCodes()).contains("system:resource:create");
        });
        assertThat(details).anySatisfy(d -> {
            assertThat(d.carrierId()).isEqualTo(82L);
            assertThat(d.decision()).isEqualTo("DENY");
        });
    }

    @Test
    void findAllowedUiActionDtos_should_support_and_or_and_negated_requirements_with_audit_details() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);

        MenuPermissionRequirementRepository menuPermissionRequirementRepository =
            mock(MenuPermissionRequirementRepository.class);
        UiActionPermissionRequirementRepository uiActionPermissionRequirementRepository =
            mock(UiActionPermissionRequirementRepository.class);
        ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository =
            mock(ApiEndpointPermissionRequirementRepository.class);
        CarrierPermissionRequirementEvaluator evaluator = new CarrierPermissionRequirementEvaluator(
            menuPermissionRequirementRepository,
            uiActionPermissionRequirementRepository,
            apiEndpointPermissionRequirementRepository
        );
        authorizationAuditService = mock(AuthorizationAuditService.class);
        ResourceServiceImpl service = new ResourceServiceImpl(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService,
            evaluator,
            authorizationAuditService
        );

        TenantContext.setActiveTenantId(8L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new SecurityUser(
                21L,
                8L,
                "resource-owner",
                "",
                List.of(
                    () -> "system:resource:create",
                    () -> "system:tenant:list",
                    () -> "system:user:list"
                ),
                true,
                true,
                true,
                true
            ),
            null,
            List.of(
                () -> "system:resource:create",
                () -> "system:tenant:list",
                () -> "system:user:list"
            )
        ));

        com.tiny.platform.infrastructure.menu.domain.MenuEntry menu = new com.tiny.platform.infrastructure.menu.domain.MenuEntry();
        menu.setId(10L);
        menu.setTenantId(8L);
        menu.setPath("/system/resource");
        menu.setType(ResourceType.MENU.getCode());
        menu.setResourceLevel("TENANT");
        menu.setEnabled(true);

        UiActionEntry complex = new UiActionEntry();
        complex.setId(81L);
        complex.setTenantId(8L);
        complex.setName("resource:create");
        complex.setTitle("资源新增");
        complex.setPermission("system:resource:create");
        complex.setPagePath("/system/resource");
        complex.setParentMenuId(10L);
        complex.setSort(1);
        complex.setEnabled(true);
        complex.setResourceLevel("TENANT");

        UiActionEntry denied = new UiActionEntry();
        denied.setId(82L);
        denied.setTenantId(8L);
        denied.setName("resource:delete");
        denied.setTitle("资源删除");
        denied.setPermission("system:resource:delete");
        denied.setPagePath("/system/resource");
        denied.setParentMenuId(10L);
        denied.setSort(2);
        denied.setEnabled(true);
        denied.setResourceLevel("TENANT");

        when(menuEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(menu));
        when(uiActionEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(complex, denied));

        // complex ui_action requirements:
        // - group 1: require (system:resource:create AND system:resource:edit) -> will fail (edit missing)
        // - group 2: require (system:user:list AND NOT system:user:disable) -> will pass (list present, disable absent)
        when(uiActionPermissionRequirementRepository.findRowsByUiActionIdIn(anyCollection())).thenReturn(List.of(
            requirementRow(81L, 1, 1, "system:resource:create", false, true),
            requirementRow(81L, 1, 2, "system:resource:edit", false, true),
            requirementRow(81L, 2, 1, "system:user:list", false, true),
            requirementRow(81L, 2, 2, "system:user:disable", true, true),
            // denied ui_action requirements:
            // group 1: require (system:resource:delete AND system:tenant:list) -> will fail (delete missing)
            requirementRow(82L, 1, 1, "system:resource:delete", false, true),
            requirementRow(82L, 1, 2, "system:tenant:list", false, true)
        ));

        List<ResourceResponseDto> result = service.findAllowedUiActionDtos("/system/resource?tab=current");

        assertThat(result).extracting(ResourceResponseDto::getId).containsExactly(81L);

        ArgumentCaptor<RequirementAwareAuditDetail> detailCaptor = ArgumentCaptor.forClass(RequirementAwareAuditDetail.class);
        verify(authorizationAuditService, atLeastOnce()).logRequirementAware(
            eq(AuthorizationAuditEventType.REQUIREMENT_AWARE_ACCESS),
            eq(8L),
            eq("ui-action-runtime-gate"),
            detailCaptor.capture()
        );
        var details = detailCaptor.getAllValues();
        assertThat(details).anySatisfy(d -> {
            assertThat(d.carrierType()).isEqualTo("ui_action");
            assertThat(d.carrierId()).isEqualTo(81L);
            assertThat(d.requirementGroup()).isEqualTo(2);
            assertThat(d.decision()).isEqualTo("ALLOW");
            assertThat(d.reason()).isEqualTo("REQUIREMENT_GROUP_SATISFIED");
            assertThat(d.matchedPermissionCodes()).contains("system:user:list");
            assertThat(d.negatedPermissionCodes()).contains("system:user:disable");
        });
        assertThat(details).anySatisfy(d -> {
            assertThat(d.carrierType()).isEqualTo("ui_action");
            assertThat(d.carrierId()).isEqualTo(82L);
            assertThat(d.requirementGroup()).isEqualTo(1);
            assertThat(d.decision()).isEqualTo("DENY");
        });
    }

    @Test
    void canAccessApiEndpoint_should_respect_runtime_api_requirements() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        MenuPermissionRequirementRepository menuPermissionRequirementRepository =
            mock(MenuPermissionRequirementRepository.class);
        UiActionPermissionRequirementRepository uiActionPermissionRequirementRepository =
            mock(UiActionPermissionRequirementRepository.class);
        ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository =
            mock(ApiEndpointPermissionRequirementRepository.class);
        CarrierPermissionRequirementEvaluator evaluator = new CarrierPermissionRequirementEvaluator(
            menuPermissionRequirementRepository,
            uiActionPermissionRequirementRepository,
            apiEndpointPermissionRequirementRepository
        );
        authorizationAuditService = mock(AuthorizationAuditService.class);
        ResourceServiceImpl service = new ResourceServiceImpl(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService,
            evaluator,
            authorizationAuditService
        );

        TenantContext.setActiveTenantId(8L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new SecurityUser(21L, 8L, "resource-owner", "", List.of(() -> "system:resource:edit"), true, true, true, true),
            null,
            List.of(() -> "system:resource:edit")
        ));

        ApiEndpointEntry endpoint = new ApiEndpointEntry();
        endpoint.setId(91L);
        endpoint.setTenantId(8L);
        endpoint.setName("resource:update-api");
        endpoint.setTitle("资源更新 API");
        endpoint.setUri("/sys/resources/9");
        endpoint.setMethod("PUT");
        endpoint.setPermission("system:resource:edit");
        endpoint.setEnabled(true);
        endpoint.setResourceLevel("TENANT");

        when(apiEndpointEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(endpoint));
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection())).thenReturn(List.of(
            requirementRow(91L, 1, 1, "system:resource:edit", false, true)
        ));

        assertThat(service.canAccessApiEndpoint("PUT", "/sys/resources/9?confirm=true")).isTrue();

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new SecurityUser(21L, 8L, "resource-owner", "", List.of(() -> "system:resource:list"), true, true, true, true),
            null,
            List.of(() -> "system:resource:list")
        ));
        assertThat(service.canAccessApiEndpoint("PUT", "/sys/resources/9")).isFalse();
    }

    @Test
    void evaluateApiEndpointRequirement_should_log_deny_when_requiredPermissionId_missing() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);

        ResourceServiceImpl service = newService(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService
        );

        TenantContext.setActiveTenantId(8L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new SecurityUser(21L, 8L, "resource-owner", "", List.of(() -> "system:resource:edit"), true, true, true, true),
            null,
            List.of(() -> "system:resource:edit")
        ));

        ApiEndpointEntry endpoint = new ApiEndpointEntry();
        endpoint.setId(91L);
        endpoint.setTenantId(8L);
        endpoint.setName("resource:update-api");
        endpoint.setTitle("资源更新 API");
        endpoint.setUri("/sys/resources/9");
        endpoint.setMethod("PUT");
        endpoint.setPermission("system:resource:edit");
        endpoint.setEnabled(true);
        endpoint.setResourceLevel("TENANT");
        // requiredPermissionId is intentionally missing -> must fail-closed

        when(apiEndpointEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(endpoint));

        var decision = service.evaluateApiEndpointRequirement("PUT", "/sys/resources/9");
        assertThat(decision).isEqualTo(com.tiny.platform.infrastructure.auth.resource.service.ApiEndpointRequirementDecision.DENIED);

        ArgumentCaptor<RequirementAwareAuditDetail> detailCaptor = ArgumentCaptor.forClass(RequirementAwareAuditDetail.class);
        verify(authorizationAuditService, atLeastOnce()).logRequirementAware(
            eq(AuthorizationAuditEventType.REQUIREMENT_AWARE_ACCESS),
            eq(8L),
            eq("api-endpoint-unified-guard"),
            detailCaptor.capture()
        );
        var details = detailCaptor.getAllValues();
        assertThat(details).anySatisfy(d -> {
            assertThat(d.carrierType()).isEqualTo("api_endpoint");
            assertThat(d.carrierId()).isEqualTo(91L);
            assertThat(d.decision()).isEqualTo("DENY");
            assertThat(d.reason()).isEqualTo("MISSING_REQUIRED_PERMISSION_ID");
        });
    }

    @Test
    void evaluateApiEndpointRequirement_should_match_template_uri_allow() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);

        ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository =
            mock(ApiEndpointPermissionRequirementRepository.class);
        CarrierPermissionRequirementEvaluator evaluator = new CarrierPermissionRequirementEvaluator(
            mock(com.tiny.platform.infrastructure.menu.repository.MenuPermissionRequirementRepository.class),
            mock(com.tiny.platform.infrastructure.auth.resource.repository.UiActionPermissionRequirementRepository.class),
            apiEndpointPermissionRequirementRepository
        );
        authorizationAuditService = mock(AuthorizationAuditService.class);
        ResourceServiceImpl service = new ResourceServiceImpl(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService,
            evaluator,
            authorizationAuditService
        );

        TenantContext.setActiveTenantId(8L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new SecurityUser(21L, 8L, "resource-owner", "", List.of(() -> "system:resource:edit"), true, true, true, true),
            null,
            List.of(() -> "system:resource:edit")
        ));

        ApiEndpointEntry endpoint = new ApiEndpointEntry();
        endpoint.setId(91L);
        endpoint.setTenantId(8L);
        endpoint.setName("resource:update-api");
        endpoint.setTitle("资源更新 API");
        endpoint.setUri("/sys/resources/{id}");
        endpoint.setMethod("PUT");
        endpoint.setPermission("system:resource:edit");
        endpoint.setRequiredPermissionId(7001L);
        endpoint.setEnabled(true);
        endpoint.setResourceLevel("TENANT");

        CarrierPermissionRequirementRow row = mock(CarrierPermissionRequirementRow.class);
        when(row.getCarrierId()).thenReturn(91L);
        when(row.getRequirementGroup()).thenReturn(0);
        when(row.getSortOrder()).thenReturn(1);
        when(row.getPermissionCode()).thenReturn("system:resource:edit");
        when(row.getNegated()).thenReturn(false);
        when(row.getPermissionEnabled()).thenReturn(true);

        when(apiEndpointEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(endpoint));
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(List.of(91L)))
            .thenReturn(List.of(row));

        assertThat(service.evaluateApiEndpointRequirement("PUT", "/sys/resources/9"))
            .isEqualTo(com.tiny.platform.infrastructure.auth.resource.service.ApiEndpointRequirementDecision.ALLOWED);
    }

    @Test
    void evaluateApiEndpointRequirement_should_match_template_uri_deny_when_permission_disabled() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);

        ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository =
            mock(ApiEndpointPermissionRequirementRepository.class);
        CarrierPermissionRequirementEvaluator evaluator = new CarrierPermissionRequirementEvaluator(
            mock(com.tiny.platform.infrastructure.menu.repository.MenuPermissionRequirementRepository.class),
            mock(com.tiny.platform.infrastructure.auth.resource.repository.UiActionPermissionRequirementRepository.class),
            apiEndpointPermissionRequirementRepository
        );
        authorizationAuditService = mock(AuthorizationAuditService.class);
        ResourceServiceImpl service = new ResourceServiceImpl(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService,
            evaluator,
            authorizationAuditService
        );

        TenantContext.setActiveTenantId(8L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new SecurityUser(21L, 8L, "resource-owner", "", List.of(() -> "system:resource:edit"), true, true, true, true),
            null,
            List.of(() -> "system:resource:edit")
        ));

        ApiEndpointEntry endpoint = new ApiEndpointEntry();
        endpoint.setId(91L);
        endpoint.setTenantId(8L);
        endpoint.setName("resource:update-api");
        endpoint.setTitle("资源更新 API");
        endpoint.setUri("/sys/resources/{id}");
        endpoint.setMethod("PUT");
        endpoint.setPermission("system:resource:edit");
        endpoint.setRequiredPermissionId(7001L);
        endpoint.setEnabled(true);
        endpoint.setResourceLevel("TENANT");

        CarrierPermissionRequirementRow row = mock(CarrierPermissionRequirementRow.class);
        when(row.getCarrierId()).thenReturn(91L);
        when(row.getRequirementGroup()).thenReturn(0);
        when(row.getSortOrder()).thenReturn(1);
        when(row.getPermissionCode()).thenReturn("system:resource:edit");
        when(row.getNegated()).thenReturn(false);
        when(row.getPermissionEnabled()).thenReturn(false);

        when(apiEndpointEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(endpoint));
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(List.of(91L)))
            .thenReturn(List.of(row));

        assertThat(service.evaluateApiEndpointRequirement("PUT", "/sys/resources/9"))
            .isEqualTo(com.tiny.platform.infrastructure.auth.resource.service.ApiEndpointRequirementDecision.DENIED);
    }

    @Test
    void evaluateApiEndpointRequirement_should_return_not_registered_when_template_does_not_match() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        MenuPermissionRequirementRepository menuPermissionRequirementRepository =
            mock(MenuPermissionRequirementRepository.class);
        UiActionPermissionRequirementRepository uiActionPermissionRequirementRepository =
            mock(UiActionPermissionRequirementRepository.class);
        ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository =
            mock(ApiEndpointPermissionRequirementRepository.class);
        CarrierPermissionRequirementEvaluator evaluator = new CarrierPermissionRequirementEvaluator(
            menuPermissionRequirementRepository,
            uiActionPermissionRequirementRepository,
            apiEndpointPermissionRequirementRepository
        );
        authorizationAuditService = mock(AuthorizationAuditService.class);
        ResourceServiceImpl service = new ResourceServiceImpl(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService,
            evaluator,
            authorizationAuditService
        );

        TenantContext.setActiveTenantId(8L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new SecurityUser(21L, 8L, "resource-owner", "", List.of(() -> "system:resource:edit"), true, true, true, true),
            null,
            List.of(() -> "system:resource:edit")
        ));

        ApiEndpointEntry endpoint = new ApiEndpointEntry();
        endpoint.setId(91L);
        endpoint.setTenantId(8L);
        endpoint.setUri("/sys/resources/{id}");
        endpoint.setMethod("PUT");
        endpoint.setEnabled(true);
        endpoint.setResourceLevel("TENANT");

        when(apiEndpointEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(endpoint));

        // Segment count mismatch => template does not match => fail-closed
        assertThat(service.evaluateApiEndpointRequirement("PUT", "/sys/resources/9/extra"))
            .isEqualTo(com.tiny.platform.infrastructure.auth.resource.service.ApiEndpointRequirementDecision.DENIED);
    }

    @Test
    void findAllowedUiActionDtos_should_support_system_user_page_path() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        MenuPermissionRequirementRepository menuPermissionRequirementRepository = mock(MenuPermissionRequirementRepository.class);
        UiActionPermissionRequirementRepository uiActionPermissionRequirementRepository = mock(UiActionPermissionRequirementRepository.class);
        ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository = mock(ApiEndpointPermissionRequirementRepository.class);
        CarrierPermissionRequirementEvaluator evaluator = new CarrierPermissionRequirementEvaluator(
            menuPermissionRequirementRepository,
            uiActionPermissionRequirementRepository,
            apiEndpointPermissionRequirementRepository
        );
        authorizationAuditService = mock(AuthorizationAuditService.class);
        ResourceServiceImpl service = new ResourceServiceImpl(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService,
            evaluator,
            authorizationAuditService
        );

        TenantContext.setActiveTenantId(8L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new SecurityUser(21L, 8L, "user-owner", "", List.of(() -> "system:user:create"), true, true, true, true),
            null,
            List.of(() -> "system:user:create")
        ));

        com.tiny.platform.infrastructure.menu.domain.MenuEntry menu = new com.tiny.platform.infrastructure.menu.domain.MenuEntry();
        menu.setId(20L);
        menu.setTenantId(8L);
        menu.setPath("/system/user");
        menu.setType(ResourceType.MENU.getCode());
        menu.setResourceLevel("TENANT");
        menu.setEnabled(true);

        UiActionEntry create = new UiActionEntry();
        create.setId(201L);
        create.setTenantId(8L);
        create.setName("user:create");
        create.setTitle("用户新增");
        create.setPermission("system:user:create");
        create.setPagePath("/system/user");
        create.setParentMenuId(20L);
        create.setSort(1);
        create.setEnabled(true);
        create.setResourceLevel("TENANT");

        UiActionEntry delete = new UiActionEntry();
        delete.setId(202L);
        delete.setTenantId(8L);
        delete.setName("user:delete");
        delete.setTitle("用户删除");
        delete.setPermission("system:user:delete");
        delete.setPagePath("/system/user");
        delete.setParentMenuId(20L);
        delete.setSort(2);
        delete.setEnabled(true);
        delete.setResourceLevel("TENANT");

        when(menuEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(menu));
        when(uiActionEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(create, delete));
        when(uiActionPermissionRequirementRepository.findRowsByUiActionIdIn(anyCollection())).thenReturn(List.of(
            requirementRow(201L, 1, 1, "system:user:create", false, true),
            requirementRow(202L, 1, 1, "system:user:delete", false, true)
        ));

        List<ResourceResponseDto> result = service.findAllowedUiActionDtos("/system/user?tab=list");

        assertThat(result).extracting(ResourceResponseDto::getPermission).containsExactly("system:user:create");
        assertThat(result).extracting(ResourceResponseDto::getCarrierKind).containsExactly("ui_action");
    }

    @Test
    void create_should_set_created_by_from_authenticated_user() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        ResourceServiceImpl service = newService(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService
        );

        TenantContext.setActiveTenantId(8L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new SecurityUser(26L, 8L, "creator", "", List.of(), true, true, true, true),
            null,
            List.of()
        ));

        Resource resource = new Resource();
        resource.setName("new-resource");
        resource.setTitle("New Resource");
        resource.setType(ResourceType.MENU);
        when(menuEntryRepository.save(any(com.tiny.platform.infrastructure.menu.domain.MenuEntry.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Resource saved = service.create(resource);

        assertThat(saved.getTenantId()).isEqualTo(8L);
        assertThat(saved.getCreatedBy()).isEqualTo(26L);
        verify(resourcePermissionBindingService).bindResource(any(Resource.class), eq(26L));
    }

    @Test
    void createFromDto_should_write_platform_template_when_platform_scope() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        ResourceServiceImpl service = newService(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService
        );

        TenantContext.setActiveTenantId(1L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new SecurityUser(30L, 1L, "platform-admin", "", List.of(), true, true, true, true),
            null,
            List.of()
        ));

        ResourceCreateUpdateDto dto = new ResourceCreateUpdateDto();
        dto.setName("platform-resource");
        dto.setTitle("平台模板资源");
        dto.setUrl("/system/platform-resource");
        dto.setComponent("/views/resource/resource.vue");
        dto.setType(ResourceType.MENU.getCode());
        dto.setPermission("system:platform-resource:list");
        when(menuEntryRepository.save(any(com.tiny.platform.infrastructure.menu.domain.MenuEntry.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        // fail-closed 校验：mock bindResource 需要为 Resource 补齐 required_permission_id（void 方法使用 doAnswer）
        org.mockito.Mockito.doAnswer(invocation -> {
            Resource res = invocation.getArgument(0);
            res.setRequiredPermissionId(999L);
            return null;
        }).when(resourcePermissionBindingService).bindResource(any(Resource.class), eq(30L));

        Resource saved = service.createFromDto(dto);

        assertThat(saved.getTenantId()).isNull();
        assertThat(saved.getResourceLevel()).isEqualTo("PLATFORM");
        assertThat(saved.getCreatedBy()).isEqualTo(30L);
        verify(resourcePermissionBindingService).bindResource(any(Resource.class), eq(30L));
        verify(menuEntryRepository).save(any(com.tiny.platform.infrastructure.menu.domain.MenuEntry.class));
    }

    @Test
    void findByType_should_return_platform_templates_when_platform_scope() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        ResourceServiceImpl service = newService(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService
        );

        TenantContext.setActiveTenantId(1L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);

        com.tiny.platform.infrastructure.menu.domain.MenuEntry platformResource =
            new com.tiny.platform.infrastructure.menu.domain.MenuEntry();
        platformResource.setId(40L);
        platformResource.setTenantId(null);
        platformResource.setResourceLevel("PLATFORM");
        platformResource.setType(ResourceType.MENU.getCode());
        platformResource.setName("platform-menu");
        platformResource.setTitle("平台菜单");
        platformResource.setPath("/platform-menu");
        platformResource.setSort(1);

        when(menuEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(platformResource));

        List<Resource> result = service.findByType(ResourceType.MENU);

        assertThat(result).extracting(Resource::getId).containsExactly(40L);
    }

    @Test
    void findTopLevelDtos_should_read_platform_carriers_without_tenant_membership_resolution() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        ResourceServiceImpl service = newService(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService
        );

        TenantContext.setActiveTenantId(1L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        DataScopeContext.set(ResolvedDataScope.selfOnly());

        com.tiny.platform.infrastructure.menu.domain.MenuEntry root = new com.tiny.platform.infrastructure.menu.domain.MenuEntry();
        root.setId(51L);
        root.setTenantId(null);
        root.setResourceLevel("PLATFORM");
        root.setType(ResourceType.MENU.getCode());
        root.setName("platform-root");
        root.setTitle("平台根菜单");
        root.setPath("/platform-root");
        root.setSort(1);
        root.setEnabled(true);

        when(menuEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(root));
        when(uiActionEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of());
        when(apiEndpointEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of());

        List<ResourceResponseDto> result = service.findTopLevelDtos();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(51L);
        assertThat(result.getFirst().getRecordTenantId()).isNull();
        verify(tenantUserRepository, never()).findUserIdsByTenantIdAndStatus(any(), any());
        verify(userUnitRepository, never()).findUserIdsByTenantIdAndUnitIdInAndStatus(any(), any(), any());
    }

    @Test
    void findDetailById_should_read_menu_carrier_without_resource_table_main_read() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        ResourceServiceImpl service = newService(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService
        );

        TenantContext.setActiveTenantId(8L);

        com.tiny.platform.infrastructure.menu.domain.MenuEntry menu =
            new com.tiny.platform.infrastructure.menu.domain.MenuEntry();
        menu.setId(88L);
        menu.setTenantId(8L);
        menu.setResourceLevel("TENANT");
        menu.setType(ResourceType.MENU.getCode());
        menu.setName("resource-menu");
        menu.setTitle("资源菜单");
        menu.setPath("/system/resource");
        menu.setSort(1);

        when(menuEntryRepository.findById(88L)).thenReturn(Optional.of(menu));

        Optional<ResourceResponseDto> detail = service.findDetailById(88L);

        assertThat(detail).isPresent();
        assertThat(detail.get().getCarrierKind()).isEqualTo("menu");
    }

    @Test
    void existsByUri_should_check_api_carrier_without_resource_table_main_read() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        ResourceServiceImpl service = newService(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService
        );

        TenantContext.setActiveTenantId(8L);

        ApiEndpointEntry endpoint = new ApiEndpointEntry();
        endpoint.setId(91L);
        endpoint.setTenantId(8L);
        endpoint.setResourceLevel("TENANT");
        endpoint.setName("resource:list-api");
        endpoint.setTitle("资源列表接口");
        endpoint.setUri("/sys/resources");
        endpoint.setMethod("GET");

        when(apiEndpointEntryRepository.findAll(any(Specification.class))).thenReturn(List.of(endpoint));

        assertThat(service.existsByUri("/sys/resources", null)).isTrue();
    }

    @Test
    void findByType_should_read_ui_action_projection_for_button() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        ResourceServiceImpl service = newService(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService
        );

        TenantContext.setActiveTenantId(8L);
        UiActionEntry action = new UiActionEntry();
        action.setId(101L);
        action.setTenantId(8L);
        action.setResourceLevel("TENANT");
        action.setName("resource-button");
        action.setTitle("资源按钮");
        action.setPagePath("/resource");
        action.setPermission("system:resource:create");
        action.setParentMenuId(10L);
        action.setSort(1);
        action.setEnabled(true);

        when(uiActionEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(action));

        List<Resource> result = service.findByType(ResourceType.BUTTON);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getType()).isEqualTo(ResourceType.BUTTON);
        assertThat(result.getFirst().getPermission()).isEqualTo("system:resource:create");
        verify(uiActionEntryRepository).findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class));
    }

    @Test
    void findByType_should_read_api_endpoint_projection_for_api() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        ResourceServiceImpl service = newService(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService
        );

        TenantContext.setActiveTenantId(8L);
        ApiEndpointEntry endpoint = new ApiEndpointEntry();
        endpoint.setId(102L);
        endpoint.setTenantId(8L);
        endpoint.setResourceLevel("TENANT");
        endpoint.setName("resource-api");
        endpoint.setTitle("资源接口");
        endpoint.setUri("/sys/resources");
        endpoint.setMethod("GET");
        endpoint.setPermission("system:resource:list");
        endpoint.setEnabled(true);

        when(apiEndpointEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(endpoint));

        List<Resource> result = service.findByType(ResourceType.API);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getType()).isEqualTo(ResourceType.API);
        assertThat(result.getFirst().getUri()).isEqualTo("/sys/resources");
        verify(apiEndpointEntryRepository).findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class));
    }

    @Test
    void findByTypeIn_should_compose_resources_from_carrier_tables() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        ResourceServiceImpl service = newService(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService
        );

        TenantContext.setActiveTenantId(8L);

        com.tiny.platform.infrastructure.menu.domain.MenuEntry menu = new com.tiny.platform.infrastructure.menu.domain.MenuEntry();
        menu.setId(1L);
        menu.setTenantId(8L);
        menu.setResourceLevel("TENANT");
        menu.setType(ResourceType.DIRECTORY.getCode());
        menu.setName("menu-root");
        menu.setTitle("菜单根");
        menu.setPath("/menu");

        UiActionEntry action = new UiActionEntry();
        action.setId(2L);
        action.setTenantId(8L);
        action.setResourceLevel("TENANT");
        action.setName("button-root");
        action.setTitle("按钮根");
        action.setPermission("system:resource:create");

        ApiEndpointEntry endpoint = new ApiEndpointEntry();
        endpoint.setId(3L);
        endpoint.setTenantId(8L);
        endpoint.setResourceLevel("TENANT");
        endpoint.setName("api-root");
        endpoint.setTitle("接口根");
        endpoint.setUri("/sys/resources");
        endpoint.setMethod("GET");
        endpoint.setPermission("system:resource:list");

        when(menuEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(menu));
        when(uiActionEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(action));
        when(apiEndpointEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(endpoint));

        List<Resource> result = service.findByTypeIn(List.of(ResourceType.DIRECTORY, ResourceType.BUTTON, ResourceType.API));

        assertThat(result).extracting(Resource::getType)
            .containsExactly(ResourceType.DIRECTORY, ResourceType.BUTTON, ResourceType.API);
    }

    @Test
    void findTopLevel_should_compose_menu_ui_action_and_api_from_carriers() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        ResourceServiceImpl service = newService(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService
        );

        TenantContext.setActiveTenantId(8L);

        com.tiny.platform.infrastructure.menu.domain.MenuEntry menu = new com.tiny.platform.infrastructure.menu.domain.MenuEntry();
        menu.setId(1L);
        menu.setTenantId(8L);
        menu.setResourceLevel("TENANT");
        menu.setType(ResourceType.MENU.getCode());
        menu.setName("menu-root");
        menu.setTitle("菜单根");
        menu.setPath("/menu");
        menu.setSort(1);
        menu.setEnabled(true);

        UiActionEntry action = new UiActionEntry();
        action.setId(2L);
        action.setTenantId(8L);
        action.setResourceLevel("TENANT");
        action.setName("button-root");
        action.setTitle("按钮根");
        action.setPermission("system:resource:create");
        action.setSort(2);
        action.setEnabled(true);

        ApiEndpointEntry endpoint = new ApiEndpointEntry();
        endpoint.setId(3L);
        endpoint.setTenantId(8L);
        endpoint.setResourceLevel("TENANT");
        endpoint.setName("api-root");
        endpoint.setTitle("接口根");
        endpoint.setUri("/sys/resources");
        endpoint.setMethod("GET");
        endpoint.setPermission("system:resource:list");
        endpoint.setEnabled(true);

        when(menuEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(menu));
        when(uiActionEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(action));
        when(apiEndpointEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(endpoint));

        List<Resource> result = service.findTopLevel();

        assertThat(result).hasSize(3);
        assertThat(result).extracting(Resource::getType)
            .containsExactly(ResourceType.MENU, ResourceType.BUTTON, ResourceType.API);
    }

    @Test
    void findByParentId_should_compose_menu_and_ui_action_children_from_carriers() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        ResourceServiceImpl service = newService(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService
        );

        TenantContext.setActiveTenantId(8L);

        com.tiny.platform.infrastructure.menu.domain.MenuEntry childMenu = new com.tiny.platform.infrastructure.menu.domain.MenuEntry();
        childMenu.setId(11L);
        childMenu.setTenantId(8L);
        childMenu.setResourceLevel("TENANT");
        childMenu.setParentId(1L);
        childMenu.setType(ResourceType.MENU.getCode());
        childMenu.setName("child-menu");
        childMenu.setTitle("子菜单");
        childMenu.setPath("/menu/child");
        childMenu.setSort(1);
        childMenu.setEnabled(true);

        UiActionEntry childAction = new UiActionEntry();
        childAction.setId(12L);
        childAction.setTenantId(8L);
        childAction.setResourceLevel("TENANT");
        childAction.setParentMenuId(1L);
        childAction.setName("child-button");
        childAction.setTitle("子按钮");
        childAction.setPermission("system:resource:update");
        childAction.setSort(2);
        childAction.setEnabled(true);

        when(menuEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(childMenu));
        when(uiActionEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(childAction));

        List<Resource> result = service.findByParentId(1L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Resource::getType)
            .containsExactly(ResourceType.MENU, ResourceType.BUTTON);
    }

    @Test
    void findTopLevelDtos_returnsEmptyWhenRestrictedDataScopeYieldsNoVisibleCreators() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        ResourceServiceImpl service = newService(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService
        );

        TenantContext.setActiveTenantId(8L);
        DataScopeContext.set(ResolvedDataScope.ofUnitsAndUsers(Set.of(999L), Set.of(), false));
        SecurityUser principal = new SecurityUser(
            5L,
            8L,
            "u",
            "",
            List.of(new SimpleGrantedAuthority("system:resource:list")),
            true,
            true,
            true,
            true,
            "pv");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities()));

        when(tenantUserRepository.findUserIdsByTenantIdAndStatus(8L, "ACTIVE")).thenReturn(List.of(5L));
        when(userUnitRepository.findUserIdsByTenantIdAndUnitIdInAndStatus(8L, Set.of(999L), "ACTIVE")).thenReturn(List.of());

        when(menuEntryRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());
        when(uiActionEntryRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());
        when(apiEndpointEntryRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        assertThat(service.findTopLevelDtos()).isEmpty();
    }

    @Test
    void findResourceTreeDtos_should_build_tree_directly_from_carrier_read_models() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        ResourceServiceImpl service = newService(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService
        );

        TenantContext.setActiveTenantId(8L);

        com.tiny.platform.infrastructure.menu.domain.MenuEntry root = new com.tiny.platform.infrastructure.menu.domain.MenuEntry();
        root.setId(10L);
        root.setTenantId(8L);
        root.setResourceLevel("TENANT");
        root.setType(ResourceType.MENU.getCode());
        root.setName("menu-root");
        root.setTitle("菜单根");
        root.setPath("/menu");
        root.setSort(1);
        root.setEnabled(true);

        com.tiny.platform.infrastructure.menu.domain.MenuEntry childMenu = new com.tiny.platform.infrastructure.menu.domain.MenuEntry();
        childMenu.setId(11L);
        childMenu.setTenantId(8L);
        childMenu.setResourceLevel("TENANT");
        childMenu.setType(ResourceType.MENU.getCode());
        childMenu.setName("menu-child");
        childMenu.setTitle("菜单子项");
        childMenu.setPath("/menu/child");
        childMenu.setParentId(10L);
        childMenu.setSort(1);
        childMenu.setEnabled(true);

        UiActionEntry childButton = new UiActionEntry();
        childButton.setId(12L);
        childButton.setTenantId(8L);
        childButton.setResourceLevel("TENANT");
        childButton.setName("menu-button");
        childButton.setTitle("菜单按钮");
        childButton.setParentMenuId(10L);
        childButton.setSort(2);
        childButton.setPermission("system:resource:create");
        childButton.setEnabled(true);

        when(menuEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(root))
            .thenReturn(List.of(childMenu))
            .thenReturn(List.of());
        when(uiActionEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of())
            .thenReturn(List.of(childButton))
            .thenReturn(List.of());
        when(apiEndpointEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of())
            .thenReturn(List.of())
            .thenReturn(List.of());

        List<ResourceResponseDto> tree = service.findResourceTreeDtos();

        assertThat(tree).hasSize(1);
        ResourceResponseDto rootDto = tree.getFirst();
        assertThat(rootDto.getId()).isEqualTo(10L);
        assertThat(rootDto.getCarrierKind()).isEqualTo("menu");
        assertThat(rootDto.getLeaf()).isFalse();
        assertThat(rootDto.getChildren()).hasSize(2);
        assertThat(rootDto.getChildren()).extracting(ResourceResponseDto::getCarrierKind)
            .containsExactly("menu", "ui_action");
        assertThat(rootDto.getChildren()).extracting(ResourceResponseDto::getLeaf)
            .containsExactly(true, true);
    }

    @Test
    void findResourceTreeDtos_should_build_platform_tree_without_tenant_membership_resolution() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        ResourceServiceImpl service = newService(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService
        );

        TenantContext.setActiveTenantId(1L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        DataScopeContext.set(ResolvedDataScope.selfOnly());

        com.tiny.platform.infrastructure.menu.domain.MenuEntry root = new com.tiny.platform.infrastructure.menu.domain.MenuEntry();
        root.setId(60L);
        root.setTenantId(null);
        root.setResourceLevel("PLATFORM");
        root.setType(ResourceType.MENU.getCode());
        root.setName("platform-root");
        root.setTitle("平台根");
        root.setPath("/platform");
        root.setSort(1);
        root.setEnabled(true);

        com.tiny.platform.infrastructure.menu.domain.MenuEntry childMenu = new com.tiny.platform.infrastructure.menu.domain.MenuEntry();
        childMenu.setId(61L);
        childMenu.setTenantId(null);
        childMenu.setResourceLevel("PLATFORM");
        childMenu.setType(ResourceType.MENU.getCode());
        childMenu.setName("platform-child");
        childMenu.setTitle("平台子菜单");
        childMenu.setPath("/platform/child");
        childMenu.setParentId(60L);
        childMenu.setSort(1);
        childMenu.setEnabled(true);

        UiActionEntry childButton = new UiActionEntry();
        childButton.setId(62L);
        childButton.setTenantId(null);
        childButton.setResourceLevel("PLATFORM");
        childButton.setName("platform-button");
        childButton.setTitle("平台按钮");
        childButton.setParentMenuId(60L);
        childButton.setSort(2);
        childButton.setPermission("system:platform:create");
        childButton.setEnabled(true);

        when(menuEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(root))
            .thenReturn(List.of(childMenu))
            .thenReturn(List.of());
        when(uiActionEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of())
            .thenReturn(List.of(childButton))
            .thenReturn(List.of());
        when(apiEndpointEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of())
            .thenReturn(List.of())
            .thenReturn(List.of());

        List<ResourceResponseDto> tree = service.findResourceTreeDtos();

        assertThat(tree).hasSize(1);
        ResourceResponseDto rootDto = tree.getFirst();
        assertThat(rootDto.getId()).isEqualTo(60L);
        assertThat(rootDto.getRecordTenantId()).isNull();
        assertThat(rootDto.getChildren()).hasSize(2);
        assertThat(rootDto.getChildren()).extracting(ResourceResponseDto::getCarrierKind)
            .containsExactly("menu", "ui_action");
        verify(tenantUserRepository, never()).findUserIdsByTenantIdAndStatus(any(), any());
        verify(userUnitRepository, never()).findUserIdsByTenantIdAndUnitIdInAndStatus(any(), any(), any());
    }

    @Test
    void delete_should_check_child_conflict_from_carrier_reads_for_non_menu_resource() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        ResourceServiceImpl service = newService(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService
        );

        TenantContext.setActiveTenantId(8L);

        Resource button = new Resource();
        button.setId(10L);
        button.setTenantId(8L);
        button.setResourceLevel("TENANT");
        button.setType(ResourceType.BUTTON);
        UiActionEntry buttonEntry = new UiActionEntry();
        buttonEntry.setId(10L);
        buttonEntry.setTenantId(8L);
        buttonEntry.setResourceLevel("TENANT");
        buttonEntry.setName("button");
        buttonEntry.setTitle("按钮");
        buttonEntry.setEnabled(true);
        when(uiActionEntryRepository.findById(10L)).thenReturn(java.util.Optional.of(buttonEntry));

        UiActionEntry childAction = new UiActionEntry();
        childAction.setId(11L);
        childAction.setTenantId(8L);
        childAction.setResourceLevel("TENANT");
        childAction.setParentMenuId(10L);
        childAction.setName("child-action");
        childAction.setTitle("子按钮");
        childAction.setSort(1);
        childAction.setEnabled(true);
        when(menuEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of());
        when(uiActionEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(childAction));

        assertThatThrownBy(() -> service.delete(10L))
            .isInstanceOf(com.tiny.platform.infrastructure.core.exception.exception.BusinessException.class)
            .hasMessageContaining("无法删除有子资源的资源");

    }

    @Test
    void delete_should_cascade_menu_tree_using_carrier_children_reads() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        UiActionEntryRepository uiActionEntryRepository = mock(UiActionEntryRepository.class);
        ApiEndpointEntryRepository apiEndpointEntryRepository = mock(ApiEndpointEntryRepository.class);
        ResourcePermissionBindingService resourcePermissionBindingService = mock(ResourcePermissionBindingService.class);
        CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = mock(CarrierPermissionReferenceSafetyService.class);
        ResourceServiceImpl service = newService(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService
        );

        TenantContext.setActiveTenantId(8L);

        Resource root = new Resource();
        root.setId(10L);
        root.setTenantId(8L);
        root.setResourceLevel("TENANT");
        root.setType(ResourceType.MENU);
        root.setRequiredPermissionId(7000L);
        com.tiny.platform.infrastructure.menu.domain.MenuEntry rootMenu =
            new com.tiny.platform.infrastructure.menu.domain.MenuEntry();
        rootMenu.setId(10L);
        rootMenu.setTenantId(8L);
        rootMenu.setResourceLevel("TENANT");
        rootMenu.setName("root-menu");
        rootMenu.setTitle("根菜单");
        rootMenu.setPath("/menu/root");
        rootMenu.setType(ResourceType.MENU.getCode());
        rootMenu.setEnabled(true);
        rootMenu.setRequiredPermissionId(7000L);
        when(menuEntryRepository.findById(10L)).thenReturn(java.util.Optional.of(rootMenu));

        com.tiny.platform.infrastructure.menu.domain.MenuEntry childMenu =
            new com.tiny.platform.infrastructure.menu.domain.MenuEntry();
        childMenu.setId(11L);
        childMenu.setTenantId(8L);
        childMenu.setResourceLevel("TENANT");
        childMenu.setParentId(10L);
        childMenu.setType(ResourceType.MENU.getCode());
        childMenu.setName("child-menu");
        childMenu.setTitle("子菜单");
        childMenu.setPath("/menu/child");
        childMenu.setSort(1);
        childMenu.setEnabled(true);
        childMenu.setRequiredPermissionId(7001L);

        UiActionEntry childAction = new UiActionEntry();
        childAction.setId(12L);
        childAction.setTenantId(8L);
        childAction.setResourceLevel("TENANT");
        childAction.setParentMenuId(10L);
        childAction.setName("child-action");
        childAction.setTitle("子按钮");
        childAction.setSort(2);
        childAction.setEnabled(true);
        childAction.setRequiredPermissionId(7002L);

        when(menuEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(childMenu))
            .thenReturn(List.of())
            .thenReturn(List.of());
        when(uiActionEntryRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(childAction))
            .thenReturn(List.of())
            .thenReturn(List.of());
        when(carrierPermissionReferenceSafetyService.existsPermissionReference(any(Long.class), eq(8L)))
            .thenReturn(false);

        service.delete(10L);

        verify(menuEntryRepository, times(3)).deleteAllByIdInBatch(any());
        verify(uiActionEntryRepository, times(3)).deleteAllByIdInBatch(any());
        verify(apiEndpointEntryRepository, times(3)).deleteAllByIdInBatch(any());
        verify(roleRepository).deleteRolePermissionRelationsByPermissionIdAndTenantId(7001L, 8L);
        verify(roleRepository).deleteRolePermissionRelationsByPermissionIdAndTenantId(7002L, 8L);
        verify(roleRepository).deleteRolePermissionRelationsByPermissionIdAndTenantId(7000L, 8L);
    }

    private ResourceServiceImpl newService(RoleRepository roleRepository,
                                           EffectiveRoleResolutionService effectiveRoleResolutionService,
                                           TenantUserRepository tenantUserRepository,
                                           UserUnitRepository userUnitRepository,
                                           UiActionEntryRepository uiActionEntryRepository,
                                           ApiEndpointEntryRepository apiEndpointEntryRepository,
                                           ResourcePermissionBindingService resourcePermissionBindingService,
                                           CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService) {
        return newService(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            mock(MenuEntryRepository.class),
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService
        );
    }

    private ResourceServiceImpl newService(RoleRepository roleRepository,
                                           EffectiveRoleResolutionService effectiveRoleResolutionService,
                                           TenantUserRepository tenantUserRepository,
                                           UserUnitRepository userUnitRepository,
                                           MenuEntryRepository menuEntryRepository,
                                           UiActionEntryRepository uiActionEntryRepository,
                                           ApiEndpointEntryRepository apiEndpointEntryRepository,
                                           ResourcePermissionBindingService resourcePermissionBindingService,
                                           CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService) {
        CarrierPermissionRequirementEvaluator evaluator = new CarrierPermissionRequirementEvaluator(
            mock(com.tiny.platform.infrastructure.menu.repository.MenuPermissionRequirementRepository.class),
            mock(com.tiny.platform.infrastructure.auth.resource.repository.UiActionPermissionRequirementRepository.class),
            mock(com.tiny.platform.infrastructure.auth.resource.repository.ApiEndpointPermissionRequirementRepository.class)
        );

        authorizationAuditService = mock(AuthorizationAuditService.class);
        return new ResourceServiceImpl(
            roleRepository,
            effectiveRoleResolutionService,
            tenantUserRepository,
            userUnitRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService,
            evaluator,
            authorizationAuditService
        );
    }

    private CarrierPermissionRequirementRow requirementRow(
        Long carrierId,
        Integer requirementGroup,
        Integer sortOrder,
        String permissionCode,
        boolean negated,
        boolean permissionEnabled
    ) {
        return new CarrierPermissionRequirementRow() {
            @Override
            public Long getCarrierId() {
                return carrierId;
            }

            @Override
            public Integer getRequirementGroup() {
                return requirementGroup;
            }

            @Override
            public Integer getSortOrder() {
                return sortOrder;
            }

            @Override
            public String getPermissionCode() {
                return permissionCode;
            }

            @Override
            public Boolean getNegated() {
                return negated;
            }

            @Override
            public Boolean getPermissionEnabled() {
                return permissionEnabled;
            }
        };
    }
}
