package com.tiny.platform.infrastructure.menu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.auth.datascope.framework.DataScopeContext;
import com.tiny.platform.infrastructure.auth.datascope.framework.ResolvedDataScope;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import com.tiny.platform.infrastructure.auth.resource.repository.ApiEndpointPermissionRequirementRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.CarrierPermissionRequirementRow;
import com.tiny.platform.infrastructure.auth.resource.repository.ApiEndpointEntryRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.UiActionEntryRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.UiActionPermissionRequirementRepository;
import com.tiny.platform.infrastructure.auth.audit.domain.AuthorizationAuditEventType;
import com.tiny.platform.infrastructure.auth.audit.domain.RequirementAwareAuditDetail;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditService;
import com.tiny.platform.infrastructure.auth.resource.service.CarrierPermissionRequirementEvaluator;
import com.tiny.platform.infrastructure.auth.resource.service.CarrierPermissionReferenceSafetyService;
import com.tiny.platform.infrastructure.auth.resource.service.ResourcePermissionBindingService;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.menu.domain.MenuEntry;
import com.tiny.platform.infrastructure.menu.repository.MenuEntryRepository;
import com.tiny.platform.infrastructure.menu.repository.MenuPermissionRequirementRepository;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.mockito.ArgumentCaptor;

class MenuServiceImplTest {

    private MenuEntryRepository menuEntryRepository;
    private UiActionEntryRepository uiActionEntryRepository;
    private ApiEndpointEntryRepository apiEndpointEntryRepository;
    private TenantUserRepository tenantUserRepository;
    private UserUnitRepository userUnitRepository;
    private ResourcePermissionBindingService resourcePermissionBindingService;
    private CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService;
    private MenuPermissionRequirementRepository menuPermissionRequirementRepository;
    private UiActionPermissionRequirementRepository uiActionPermissionRequirementRepository;
    private ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository;
    private AuthorizationAuditService authorizationAuditService;
    private RoleRepository roleRepository;
    private MenuServiceImpl service;

    @BeforeEach
    void setUp() {
        menuEntryRepository = Mockito.mock(MenuEntryRepository.class);
        uiActionEntryRepository = Mockito.mock(UiActionEntryRepository.class);
        apiEndpointEntryRepository = Mockito.mock(ApiEndpointEntryRepository.class);
        tenantUserRepository = Mockito.mock(TenantUserRepository.class);
        userUnitRepository = Mockito.mock(UserUnitRepository.class);
        resourcePermissionBindingService = Mockito.mock(ResourcePermissionBindingService.class);
        carrierPermissionReferenceSafetyService = Mockito.mock(CarrierPermissionReferenceSafetyService.class);
        menuPermissionRequirementRepository = Mockito.mock(MenuPermissionRequirementRepository.class);
        uiActionPermissionRequirementRepository = Mockito.mock(UiActionPermissionRequirementRepository.class);
        apiEndpointPermissionRequirementRepository = Mockito.mock(ApiEndpointPermissionRequirementRepository.class);
        authorizationAuditService = Mockito.mock(AuthorizationAuditService.class);
        roleRepository = Mockito.mock(RoleRepository.class);

        CarrierPermissionRequirementEvaluator evaluator = new CarrierPermissionRequirementEvaluator(
            menuPermissionRequirementRepository,
            uiActionPermissionRequirementRepository,
            apiEndpointPermissionRequirementRepository
        );
        service = new MenuServiceImpl(
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            tenantUserRepository,
            userUnitRepository,
            resourcePermissionBindingService,
            carrierPermissionReferenceSafetyService,
            evaluator,
            authorizationAuditService,
            roleRepository
        );

        when(menuPermissionRequirementRepository.findRowsByMenuIdIn(anyCollection())).thenReturn(List.of());
        when(uiActionPermissionRequirementRepository.findRowsByUiActionIdIn(anyCollection())).thenReturn(List.of());
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        DataScopeContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void menusShouldApplyCreatedByFilterWhenDataScopeRestricted() {
        TenantContext.setActiveTenantId(2L);
        DataScopeContext.set(ResolvedDataScope.ofUnitsAndUsers(Set.of(30L), Set.of(2L), true));
        authenticate(5L, 2L, "tenant-admin", "system:menu:list");

        when(tenantUserRepository.findUserIdsByTenantIdAndStatus(2L, "ACTIVE")).thenReturn(List.of(2L, 3L, 5L));
        when(tenantUserRepository.findUserIdsByTenantIdAndUserIdInAndStatus(2L, Set.of(2L), "ACTIVE")).thenReturn(List.of(2L));
        when(userUnitRepository.findUserIdsByTenantIdAndUnitIdInAndStatus(2L, Set.of(30L), "ACTIVE")).thenReturn(List.of(3L));
        MenuEntry entry = menuEntry(2L, 2L, "user", 1L, "/system/user", "system:user:list", ResourceType.MENU.getCode());
        when(menuEntryRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 10), 1));
        when(menuEntryRepository.existsByParentIdAndTenantId(2L, 2L)).thenReturn(false);

        Page<ResourceResponseDto> page = service.menus(new com.tiny.platform.infrastructure.auth.resource.dto.ResourceRequestDto(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1L);
        assertThat(page.getContent()).extracting(ResourceResponseDto::getName).containsExactly("user");
        verify(menuEntryRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void listShouldApplyCreatedByFilterWhenDataScopeRestricted() {
        TenantContext.setActiveTenantId(2L);
        DataScopeContext.set(ResolvedDataScope.ofUnitsAndUsers(Set.of(30L), Set.of(2L), true));
        authenticate(5L, 2L, "tenant-admin", "system:menu:list");

        when(tenantUserRepository.findUserIdsByTenantIdAndStatus(2L, "ACTIVE")).thenReturn(List.of(2L, 3L, 5L));
        when(tenantUserRepository.findUserIdsByTenantIdAndUserIdInAndStatus(2L, Set.of(2L), "ACTIVE")).thenReturn(List.of(2L));
        when(userUnitRepository.findUserIdsByTenantIdAndUnitIdInAndStatus(2L, Set.of(30L), "ACTIVE")).thenReturn(List.of(3L));
        MenuEntry entry = menuEntry(2L, 2L, "sys", null, "/", "", ResourceType.DIRECTORY.getCode());
        when(menuEntryRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(entry));
        when(menuPermissionRequirementRepository.findRowsByMenuIdIn(anyCollection())).thenReturn(List.of());

        List<ResourceResponseDto> rows = service.list(new com.tiny.platform.infrastructure.auth.resource.dto.ResourceRequestDto());

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getName()).isEqualTo("sys");
        verify(menuEntryRepository).findAll(
            any(org.springframework.data.jpa.domain.Specification.class),
            any(org.springframework.data.domain.Sort.class));
    }

    @Test
    void existsChecksShouldUseMenuCarrierWithinCurrentScope() {
        TenantContext.setActiveTenantId(2L);
        authenticate(5L, 2L, "tenant-admin", "system:menu:list");
        when(menuEntryRepository.existsByNameAndTenantScope("menu", 9L, 2L)).thenReturn(true);
        when(menuEntryRepository.existsByPathAndTenantScope("/menu", 9L, 2L)).thenReturn(false);

        assertThat(service.existsByName("menu", 9L)).isTrue();
        assertThat(service.existsByUrl("/menu", 9L)).isFalse();
        assertThat(service.existsByName(" ", 9L)).isFalse();
        assertThat(service.existsByUrl("", 9L)).isFalse();
        verify(menuEntryRepository).existsByNameAndTenantScope("menu", 9L, 2L);
        verify(menuEntryRepository).existsByPathAndTenantScope("/menu", 9L, 2L);
    }

    @Test
    void createMenuShouldCaptureCurrentUserAsCreatedBy() {
        TenantContext.setActiveTenantId(2L);
        authenticate(7L, 2L, "creator", "system:menu:create");
        when(menuEntryRepository.save(any(MenuEntry.class))).thenAnswer(invocation -> {
            MenuEntry saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(501L);
            }
            return saved;
        });

        ResourceCreateUpdateDto dto = new ResourceCreateUpdateDto();
        dto.setName("sys-user");
        dto.setTitle("User");
        dto.setType(ResourceType.MENU.getCode());

        Resource created = service.createMenu(dto);

        assertThat(created.getCreatedBy()).isEqualTo(7L);
        assertThat(created.getTenantId()).isEqualTo(2L);
        verify(resourcePermissionBindingService).bindResource(any(Resource.class), eq(7L));
        verify(menuEntryRepository).save(any(MenuEntry.class));
    }

    @Test
    void createMenuShouldValidateParentByMenuCarrierInsteadOfResourceTreeRead() {
        TenantContext.setActiveTenantId(2L);
        authenticate(7L, 2L, "creator", "system:menu:create");
        when(menuEntryRepository.save(any(MenuEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(menuEntryRepository.findById(100L)).thenReturn(java.util.Optional.of(
            menuEntry(100L, 2L, "system", null, "/system", "", ResourceType.DIRECTORY.getCode())
        ));

        ResourceCreateUpdateDto dto = new ResourceCreateUpdateDto();
        dto.setName("sys-user");
        dto.setTitle("User");
        dto.setType(ResourceType.MENU.getCode());
        dto.setParentId(100L);

        service.createMenu(dto);

        verify(menuEntryRepository, atLeastOnce()).findById(100L);
    }

    @Test
    void createMenuShouldUseExplicitRequiredPermissionIdAsWriteEntry() {
        TenantContext.setActiveTenantId(2L);
        authenticate(7L, 2L, "creator", "system:menu:create");
        Mockito.doAnswer(invocation -> {
            Resource resource = invocation.getArgument(0);
            assertThat(resource.getRequiredPermissionId()).isEqualTo(88L);
            resource.setPermission("system:menu:list");
            return null;
        }).when(resourcePermissionBindingService).bindResource(any(Resource.class), eq(7L));
        when(menuEntryRepository.save(any(MenuEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResourceCreateUpdateDto dto = new ResourceCreateUpdateDto();
        dto.setName("sys-menu");
        dto.setTitle("Menu");
        dto.setType(ResourceType.MENU.getCode());
        dto.setRequiredPermissionId(88L);

        Resource created = service.createMenu(dto);

        assertThat(created.getRequiredPermissionId()).isEqualTo(88L);
        assertThat(created.getPermission()).isEqualTo("system:menu:list");
        verify(menuEntryRepository).save(argThat(saved ->
            Objects.equals(saved.getRequiredPermissionId(), 88L)
                && Objects.equals(saved.getPermission(), "system:menu:list")
        ));
    }

    @Test
    void createMenuShouldRejectLegacyPermissionOnlyPayload() {
        TenantContext.setActiveTenantId(2L);
        authenticate(7L, 2L, "creator", "system:menu:create");

        ResourceCreateUpdateDto dto = new ResourceCreateUpdateDto();
        dto.setName("sys-menu");
        dto.setTitle("Menu");
        dto.setType(ResourceType.MENU.getCode());
        dto.setPermission("system:menu:list");

        assertThatThrownBy(() -> service.createMenu(dto))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Missing required_permission_id");
        verify(menuEntryRepository, never()).save(any(MenuEntry.class));
    }

    @Test
    void updateMenuShouldUseExplicitRequiredPermissionIdAsWriteEntry() {
        TenantContext.setActiveTenantId(2L);
        authenticate(7L, 2L, "editor", "system:menu:edit");
        MenuEntry existing = menuEntry(66L, 2L, "sys-menu", null, "/system/menu", "", ResourceType.MENU.getCode());
        when(menuEntryRepository.findById(66L)).thenReturn(java.util.Optional.of(existing));
        Mockito.doAnswer(invocation -> {
            Resource resource = invocation.getArgument(0);
            assertThat(resource.getRequiredPermissionId()).isEqualTo(99L);
            resource.setPermission("system:menu:edit");
            return null;
        }).when(resourcePermissionBindingService).bindResource(any(Resource.class), eq(7L));
        when(menuEntryRepository.save(any(MenuEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResourceCreateUpdateDto dto = new ResourceCreateUpdateDto();
        dto.setId(66L);
        dto.setName("sys-menu");
        dto.setTitle("Menu");
        dto.setType(ResourceType.MENU.getCode());
        dto.setRequiredPermissionId(99L);

        Resource updated = service.updateMenu(dto);

        assertThat(updated.getRequiredPermissionId()).isEqualTo(99L);
        assertThat(updated.getPermission()).isEqualTo("system:menu:edit");
        verify(menuEntryRepository).save(argThat(saved ->
            Objects.equals(saved.getRequiredPermissionId(), 99L)
                && Objects.equals(saved.getPermission(), "system:menu:edit")
        ));
    }

    @Test
    void updateMenuShouldRejectLegacyPermissionOnlyPayload() {
        TenantContext.setActiveTenantId(2L);
        authenticate(7L, 2L, "editor", "system:menu:edit");
        MenuEntry existing = menuEntry(66L, 2L, "sys-menu", null, "/system/menu", "", ResourceType.MENU.getCode());
        when(menuEntryRepository.findById(66L)).thenReturn(java.util.Optional.of(existing));

        ResourceCreateUpdateDto dto = new ResourceCreateUpdateDto();
        dto.setId(66L);
        dto.setName("sys-menu");
        dto.setTitle("Menu");
        dto.setType(ResourceType.MENU.getCode());
        dto.setPermission("system:menu:edit");

        assertThatThrownBy(() -> service.updateMenu(dto))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Missing required_permission_id");
        verify(menuEntryRepository, never()).save(any(MenuEntry.class));
    }

    @Test
    void deleteMenuShouldKeepRolePermissionWhenPermissionStillReferenced() {
        TenantContext.setActiveTenantId(2L);
        MenuEntry menu = menuEntry(9L, 2L, "user", null, "/system/user", "system:user:list", ResourceType.MENU.getCode());
        menu.setRequiredPermissionId(88L);

        when(menuEntryRepository.findById(9L)).thenReturn(java.util.Optional.of(menu));
        when(menuEntryRepository.findByTenantIdAndTypeInAndParentIdOrderBySortAsc(2L, List.of(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode()), 9L))
            .thenReturn(List.of());
        when(carrierPermissionReferenceSafetyService.existsPermissionReference(88L, 2L)).thenReturn(true);

        service.deleteMenu(9L);

        verify(menuEntryRepository).deleteAllByIdInBatch(List.of(9L));
        verify(uiActionEntryRepository).deleteAllByIdInBatch(List.of(9L));
        verify(apiEndpointEntryRepository).deleteAllByIdInBatch(List.of(9L));
    }

    @Test
    void deleteMenuShouldRemoveRolePermissionWhenLastReferenceDeleted() {
        TenantContext.setActiveTenantId(2L);
        MenuEntry menu = menuEntry(10L, 2L, "user", null, "/system/user", "system:user:list", ResourceType.MENU.getCode());
        menu.setRequiredPermissionId(99L);

        when(menuEntryRepository.findById(10L)).thenReturn(java.util.Optional.of(menu));
        when(menuEntryRepository.findByTenantIdAndTypeInAndParentIdOrderBySortAsc(2L, List.of(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode()), 10L))
            .thenReturn(List.of());
        when(carrierPermissionReferenceSafetyService.existsPermissionReference(99L, 2L)).thenReturn(false);

        service.deleteMenu(10L);

        verify(menuEntryRepository).deleteAllByIdInBatch(List.of(10L));
        verify(uiActionEntryRepository).deleteAllByIdInBatch(List.of(10L));
        verify(apiEndpointEntryRepository).deleteAllByIdInBatch(List.of(10L));
        verify(roleRepository).deleteRolePermissionRelationsByPermissionIdAndTenantId(99L, 2L);
    }

    @Test
    void deleteMenuShouldEnumerateChildrenFromMenuCarrierRepository() {
        TenantContext.setActiveTenantId(2L);

        MenuEntry root = menuEntry(20L, 2L, "system", null, "/system", "system:menu:list", ResourceType.DIRECTORY.getCode());
        root.setRequiredPermissionId(200L);
        MenuEntry child = menuEntry(21L, 2L, "user", 20L, "/system/user", "system:user:list", ResourceType.MENU.getCode());
        child.setRequiredPermissionId(201L);

        when(menuEntryRepository.findById(20L)).thenReturn(java.util.Optional.of(root));
        when(menuEntryRepository.findByTenantIdAndTypeInAndParentIdOrderBySortAsc(
            2L, List.of(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode()), 20L
        )).thenReturn(List.of(child));
        when(menuEntryRepository.findByTenantIdAndTypeInAndParentIdOrderBySortAsc(
            2L, List.of(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode()), 21L
        )).thenReturn(List.of());
        when(carrierPermissionReferenceSafetyService.existsPermissionReference(200L, 2L)).thenReturn(true);
        when(carrierPermissionReferenceSafetyService.existsPermissionReference(201L, 2L)).thenReturn(true);

        service.deleteMenu(20L);

        verify(menuEntryRepository).findByTenantIdAndTypeInAndParentIdOrderBySortAsc(
            2L, List.of(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode()), 20L
        );
        verify(menuEntryRepository).findByTenantIdAndTypeInAndParentIdOrderBySortAsc(
            2L, List.of(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode()), 21L
        );
        verify(menuEntryRepository).deleteAllByIdInBatch(List.of(21L));
        verify(menuEntryRepository).deleteAllByIdInBatch(List.of(20L));
        verify(uiActionEntryRepository).deleteAllByIdInBatch(List.of(21L));
        verify(uiActionEntryRepository).deleteAllByIdInBatch(List.of(20L));
        verify(apiEndpointEntryRepository).deleteAllByIdInBatch(List.of(21L));
        verify(apiEndpointEntryRepository).deleteAllByIdInBatch(List.of(20L));
    }

    @Test
    void updateMenuSortShouldNotUpsertCompatibilityResource() {
        TenantContext.setActiveTenantId(2L);
        MenuEntry menu = menuEntry(40L, 2L, "system", null, "/system", "system:menu:list", ResourceType.MENU.getCode());
        menu.setResourceLevel("TENANT");
        menu.setRequiredPermissionId(4100L);

        when(menuEntryRepository.findById(40L)).thenReturn(java.util.Optional.of(menu));
        when(menuEntryRepository.save(any(MenuEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateMenuSort(40L, 8);

        verify(menuEntryRepository).save(argThat(saved ->
            Objects.equals(saved.getId(), 40L) && saved.getSort().equals(8)
        ));
    }

    @Test
    void menuTreeShouldFilterMenusByCurrentAuthoritiesWithoutLegacyGrantedQueries() {
        TenantContext.setActiveTenantId(2L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);
        authenticate(5L, 2L, "tenant-user", "system:user:list");

        when(menuEntryRepository.findByTenantIdAndTypeInOrderBySortAsc(2L, List.of(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode())))
            .thenReturn(List.of(
                menuEntry(1L, 2L, "system", null, "/system", "", ResourceType.DIRECTORY.getCode()),
                menuEntry(2L, 2L, "user", 1L, "/system/user", "system:user:list", ResourceType.MENU.getCode()),
                menuEntry(3L, 2L, "tenant", 1L, "/system/tenant", "system:tenant:list", ResourceType.MENU.getCode()),
                menuEntry(4L, 2L, "idempotentOps", 1L, "/ops/idempotent", "idempotent:ops:view", ResourceType.MENU.getCode())
            ));
        when(menuPermissionRequirementRepository.findRowsByMenuIdIn(anyCollection())).thenReturn(List.of(
            row(1L, 1, 1, "system:user:list", false),
            row(2L, 1, 1, "system:user:list", false),
            row(3L, 1, 1, "system:tenant:list", false),
            row(4L, 1, 1, "idempotent:ops:view", false)
        ));

        List<ResourceResponseDto> tree = service.menuTree();

        assertThat(tree).singleElement().extracting(ResourceResponseDto::getName).isEqualTo("system");
        assertThat(tree.get(0).getChildren()).extracting(ResourceResponseDto::getName).containsExactly("user");
        verify(menuEntryRepository).findByTenantIdAndTypeInOrderBySortAsc(2L, List.of(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode()));
    }

    @Test
    void menuTreeShouldKeepPlatformRuntimeMenusForPlatformAdmin() {
        TenantContext.setActiveTenantId(null);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        authenticate(
            1L,
            null,
            "platform-admin",
            "system:user:list",
            "system:tenant:list",
            "idempotent:ops:view",
            "system:audit:authentication:view",
            "system:audit:auth:view"
        );

        when(menuEntryRepository.findByTenantIdIsNullAndTypeInOrderBySortAsc(List.of(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode())))
            .thenReturn(List.of(
                menuEntry(1L, null, "system", null, "/system", "", ResourceType.DIRECTORY.getCode()),
                menuEntry(2L, null, "user", 1L, "/system/user", "system:user:list", ResourceType.MENU.getCode()),
                menuEntry(3L, null, "tenant", 1L, "/system/tenant", "system:tenant:list", ResourceType.MENU.getCode()),
                menuEntry(4L, null, "idempotentOps", 1L, "/ops/idempotent", "idempotent:ops:view", ResourceType.MENU.getCode()),
                menuEntry(5L, null, "authenticationAudit", 1L, "/system/audit/authentication", "system:audit:authentication:view", ResourceType.MENU.getCode()),
                menuEntry(6L, null, "authorizationAudit", 1L, "/system/audit/authorization", "system:audit:auth:view", ResourceType.MENU.getCode())
            ));
        when(menuPermissionRequirementRepository.findRowsByMenuIdIn(anyCollection())).thenReturn(List.of(
            row(1L, 1, 1, "system:user:list", false),
            row(2L, 1, 1, "system:user:list", false),
            row(3L, 1, 1, "system:tenant:list", false),
            row(4L, 1, 1, "idempotent:ops:view", false),
            row(5L, 1, 1, "system:audit:authentication:view", false),
            row(6L, 1, 1, "system:audit:auth:view", false)
        ));

        List<ResourceResponseDto> tree = service.menuTree();

        assertThat(tree).singleElement().extracting(ResourceResponseDto::getName).isEqualTo("system");
        assertThat(tree.get(0).getChildren()).extracting(ResourceResponseDto::getName)
            .containsExactly("user", "tenant", "idempotentOps", "authenticationAudit", "authorizationAudit");
    }

    @Test
    void menuTreeAllShouldReadPlatformCarrierWithoutResolvingPlatformTenantId() {
        TenantContext.setActiveTenantId(null);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        authenticate(1L, null, "platform-admin", "system:menu:list", "system:user:list");

        when(menuEntryRepository.findByTenantIdIsNullAndTypeInOrderBySortAsc(
            List.of(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode())
        )).thenReturn(List.of(
            menuEntry(1L, null, "system", null, "/system", "", ResourceType.DIRECTORY.getCode()),
            menuEntry(2L, null, "user", 1L, "/system/user", "system:user:list", ResourceType.MENU.getCode())
        ));
        when(menuEntryRepository.existsByParentIdAndTenantIdIsNull(1L)).thenReturn(true);
        when(menuEntryRepository.existsByParentIdAndTenantIdIsNull(2L)).thenReturn(false);

        List<ResourceResponseDto> tree = service.menuTreeAll();

        assertThat(tree).singleElement().extracting(ResourceResponseDto::getName).isEqualTo("system");
        assertThat(tree.get(0).getChildren()).extracting(ResourceResponseDto::getName).containsExactly("user");
        verify(menuEntryRepository).findByTenantIdIsNullAndTypeInOrderBySortAsc(
            List.of(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode())
        );
    }

    @Test
    void menuTreeShouldSupportAndOrAndNegatedRequirements() {
        TenantContext.setActiveTenantId(2L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);
        authenticate(5L, 2L, "tenant-user", "system:user:list", "system:tenant:list");

        when(menuEntryRepository.findByTenantIdAndTypeInOrderBySortAsc(2L, List.of(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode())))
            .thenReturn(List.of(
                menuEntry(1L, 2L, "system", null, "/system", "", ResourceType.DIRECTORY.getCode()),
                menuEntry(2L, 2L, "audit", 1L, "/system/audit", "system:audit:auth:view", ResourceType.MENU.getCode()),
                menuEntry(3L, 2L, "forbidden", 1L, "/system/forbidden", "system:audit:authentication:view", ResourceType.MENU.getCode())
            ));
        when(menuPermissionRequirementRepository.findRowsByMenuIdIn(anyCollection())).thenReturn(List.of(
            row(1L, 1, 1, "system:user:list", false),
            row(2L, 1, 1, "system:audit:auth:view", false),
            row(2L, 1, 2, "system:tenant:list", false),
            row(2L, 2, 1, "system:user:list", false),
            row(2L, 2, 2, "system:audit:auth:view", true),
            row(3L, 1, 1, "system:user:list", false),
            row(3L, 1, 2, "system:tenant:list", false),
            row(3L, 1, 3, "system:audit:auth:view", false)
        ));

        List<ResourceResponseDto> tree = service.menuTree();

        assertThat(tree).singleElement().extracting(ResourceResponseDto::getName).isEqualTo("system");
        assertThat(tree.get(0).getChildren()).extracting(ResourceResponseDto::getName).containsExactly("audit");

        ArgumentCaptor<RequirementAwareAuditDetail> detailCaptor = ArgumentCaptor.forClass(RequirementAwareAuditDetail.class);
        verify(authorizationAuditService, Mockito.atLeastOnce()).logRequirementAware(
            eq(AuthorizationAuditEventType.REQUIREMENT_AWARE_ACCESS),
            eq(2L),
            eq("menu-runtime-tree"),
            detailCaptor.capture()
        );

        var details = detailCaptor.getAllValues();
        assertThat(details).anySatisfy(d -> {
            assertThat(d.carrierType()).isEqualTo("menu");
            assertThat(d.carrierId()).isEqualTo(2L);
            assertThat(d.requirementGroup()).isEqualTo(2);
            assertThat(d.decision()).isEqualTo("ALLOW");
            assertThat(d.matchedPermissionCodes()).contains("system:user:list");
        });
        assertThat(details).anySatisfy(d -> {
            assertThat(d.carrierId()).isEqualTo(3L);
            assertThat(d.decision()).isEqualTo("DENY");
            assertThat(d.requirementGroup()).isEqualTo(1);
        });
    }

    @Test
    void getMenusByParentIdShouldFilterChildrenByRequirements() {
        TenantContext.setActiveTenantId(2L);
        authenticate(5L, 2L, "tenant-user", "system:user:list");

        when(menuEntryRepository.findByTenantIdAndTypeInAndParentIdOrderBySortAsc(2L, List.of(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode()), 1L))
            .thenReturn(List.of(
                menuEntry(2L, 2L, "user", 1L, "/system/user", "system:user:list", ResourceType.MENU.getCode()),
                menuEntry(3L, 2L, "tenant", 1L, "/system/tenant", "system:tenant:list", ResourceType.MENU.getCode())
            ));
        when(menuPermissionRequirementRepository.findRowsByMenuIdIn(anyCollection())).thenReturn(List.of(
            row(2L, 1, 1, "system:user:list", false),
            row(3L, 1, 1, "system:tenant:list", false)
        ));

        List<ResourceResponseDto> children = service.getMenusByParentId(1L);

        assertThat(children).extracting(ResourceResponseDto::getName).containsExactly("user");
    }

    private MenuEntry menuEntry(
        Long id,
        Long tenantId,
        String name,
        Long parentId,
        String path,
        String permission,
        Integer type
    ) {
        MenuEntry entry = new MenuEntry();
        entry.setId(id);
        entry.setTenantId(tenantId);
        entry.setName(name);
        entry.setTitle(name);
        entry.setParentId(parentId);
        entry.setPath(path);
        entry.setPermission(permission);
        entry.setType(type);
        entry.setEnabled(true);
        entry.setHidden(false);
        entry.setShowIcon(false);
        entry.setKeepAlive(false);
        entry.setSort(1);
        return entry;
    }

    private CarrierPermissionRequirementRow row(Long carrierId, Integer group, Integer sortOrder, String permissionCode, boolean negated) {
        return new CarrierPermissionRequirementRow() {
            @Override
            public Long getCarrierId() {
                return carrierId;
            }

            @Override
            public Integer getRequirementGroup() {
                return group;
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
                return true;
            }
        };
    }

    private void authenticate(Long userId, Long tenantId, String username, String... authorities) {
        List<SimpleGrantedAuthority> grantedAuthorities = java.util.Arrays.stream(authorities)
            .map(SimpleGrantedAuthority::new)
            .toList();
        SecurityUser principal = new SecurityUser(
            userId,
            tenantId,
            username,
            "",
            grantedAuthorities,
            true,
            true,
            true,
            true,
            "pv-1"
        );
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities())
        );
    }
}
