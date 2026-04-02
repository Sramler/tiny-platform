package com.tiny.platform.infrastructure.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.domain.ApiEndpointEntry;
import com.tiny.platform.infrastructure.auth.resource.domain.UiActionEntry;
import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import com.tiny.platform.infrastructure.auth.resource.repository.CarrierProjectionRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.RoleResourcePermissionBindingView;
import com.tiny.platform.infrastructure.auth.resource.repository.ResourceRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.ApiEndpointEntryRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.UiActionEntryRepository;
import com.tiny.platform.infrastructure.auth.resource.service.ResourcePermissionBindingService;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleResourceRelationProjection;
import com.tiny.platform.infrastructure.menu.domain.MenuEntry;
import com.tiny.platform.infrastructure.menu.repository.MenuEntryRepository;
import com.tiny.platform.infrastructure.tenant.config.PlatformTenantProperties;
import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TenantBootstrapServiceImplTest {

    private TenantRepository tenantRepository;
    private ResourceRepository resourceRepository;
    private CarrierProjectionRepository carrierProjectionRepository;
    private MenuEntryRepository menuEntryRepository;
    private UiActionEntryRepository uiActionEntryRepository;
    private ApiEndpointEntryRepository apiEndpointEntryRepository;
    private RoleRepository roleRepository;
    private PlatformTenantProperties platformTenantProperties;
    private ResourcePermissionBindingService resourcePermissionBindingService;
    private TenantBootstrapServiceImpl service;

    @BeforeEach
    void setUp() {
        tenantRepository = org.mockito.Mockito.mock(TenantRepository.class);
        resourceRepository = org.mockito.Mockito.mock(ResourceRepository.class);
        carrierProjectionRepository = org.mockito.Mockito.mock(CarrierProjectionRepository.class);
        menuEntryRepository = org.mockito.Mockito.mock(MenuEntryRepository.class);
        uiActionEntryRepository = org.mockito.Mockito.mock(UiActionEntryRepository.class);
        apiEndpointEntryRepository = org.mockito.Mockito.mock(ApiEndpointEntryRepository.class);
        roleRepository = org.mockito.Mockito.mock(RoleRepository.class);
        platformTenantProperties = new PlatformTenantProperties();
        resourcePermissionBindingService = org.mockito.Mockito.mock(ResourcePermissionBindingService.class);
        AtomicLong nextCarrierId = new AtomicLong(100L);
        when(menuEntryRepository.saveAll(any())).thenAnswer(invocation -> {
            List<MenuEntry> entries = invocation.getArgument(0);
            for (MenuEntry entry : entries) {
                if (entry.getId() == null) {
                    entry.setId(nextCarrierId.getAndIncrement());
                }
            }
            return entries;
        });
        when(uiActionEntryRepository.saveAll(any())).thenAnswer(invocation -> {
            List<UiActionEntry> entries = invocation.getArgument(0);
            for (UiActionEntry entry : entries) {
                if (entry.getId() == null) {
                    entry.setId(nextCarrierId.getAndIncrement());
                }
            }
            return entries;
        });
        when(apiEndpointEntryRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ApiEndpointEntry> entries = invocation.getArgument(0);
            for (ApiEndpointEntry entry : entries) {
                if (entry.getId() == null) {
                    entry.setId(nextCarrierId.getAndIncrement());
                }
            }
            return entries;
        });
        service = new TenantBootstrapServiceImpl(
            tenantRepository,
            carrierProjectionRepository,
            menuEntryRepository,
            uiActionEntryRepository,
            apiEndpointEntryRepository,
            roleRepository,
            platformTenantProperties,
            resourcePermissionBindingService
        );
    }

    @Test
    void bootstrapFromPlatformTemplate_shouldCloneResourcesRolesAndRelations() {
        Tenant targetTenant = tenant(9L, "tenant-9");

        Resource sourceRoot = resource(10L, null, "scheduling", null, "scheduling", "", "", ResourceType.DIRECTORY);
        sourceRoot.setResourceLevel("PLATFORM");
        Resource sourceRead = resource(11L, null, "scheduling-authority-read", 10L, "", "", "scheduling:console:view", ResourceType.BUTTON);
        sourceRead.setResourceLevel("PLATFORM");
        Role sourceAdmin = role(20L, null, "ROLE_ADMIN", "管理员");
        sourceAdmin.setRoleLevel("PLATFORM");

        when(carrierProjectionRepository.findTemplateSnapshotViewsByScope(null, "PLATFORM"))
            .thenReturn(List.of(snapshot(sourceRoot), snapshot(sourceRead)));
        when(roleRepository.findByTenantIdIsNullOrderByIdAsc()).thenReturn(List.of(sourceAdmin));
        when(roleRepository.findGrantedRoleCarrierPairsForPlatformTemplate()).thenReturn(List.of(relation(20L, 11L)));

        AtomicLong nextRoleId = new AtomicLong(200L);
        when(roleRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Role> roles = invocation.getArgument(0);
            for (Role role : roles) {
                if (role.getId() == null) {
                    role.setId(nextRoleId.getAndIncrement());
                }
            }
            return roles;
        });
        when(carrierProjectionRepository.findPermissionBindingViewsByIdsAndScope(List.of(100L, 101L), 9L, "TENANT"))
            .thenReturn(List.of(binding(100L, null, 9000L), binding(101L, "scheduling:console:view", 9001L)));

        service.bootstrapFromPlatformTemplate(targetTenant);

        verify(menuEntryRepository, times(2)).saveAll(any());
        ArgumentCaptor<List<UiActionEntry>> uiActionCaptor = ArgumentCaptor.forClass(List.class);
        verify(uiActionEntryRepository, times(2)).saveAll(uiActionCaptor.capture());
        assertThat(uiActionCaptor.getAllValues().get(1))
            .singleElement()
            .satisfies(clonedAction -> assertThat(clonedAction.getParentMenuId()).isEqualTo(100L));

        ArgumentCaptor<List<Role>> roleCaptor = ArgumentCaptor.forClass(List.class);
        verify(roleRepository).saveAll(roleCaptor.capture());
        assertThat(roleCaptor.getValue()).singleElement().satisfies(clonedRole -> {
            assertThat(clonedRole.getTenantId()).isEqualTo(9L);
            assertThat(clonedRole.getCode()).isEqualTo("ROLE_ADMIN");
            assertThat(clonedRole.getName()).isEqualTo("管理员 · tenant:9");
        });

        verify(roleRepository).addRolePermissionRelationByPermissionId(9L, 200L, 9001L);
        verify(carrierProjectionRepository, times(2)).findTemplateSnapshotViewsByScope(null, "PLATFORM");
        verify(carrierProjectionRepository).findTemplateSnapshotViewsByScope(9L, "TENANT");
        verify(carrierProjectionRepository).findPermissionBindingViewsByIdsAndScope(List.of(100L, 101L), 9L, "TENANT");
        verify(resourcePermissionBindingService).backfillPermissionCatalogFromResources(9L);
        verify(resourcePermissionBindingService).bindRequiredPermissionIdsForResources(9L);
    }

    @Test
    void bootstrapFromPlatformTemplate_should_clone_button_and_api_fields_from_carrier_template_snapshot() {
        Tenant targetTenant = tenant(9L, "tenant-9");

        Resource sourceRoot = resource(10L, null, "system", null, "/system", "", "system:entry:view", ResourceType.DIRECTORY);
        sourceRoot.setResourceLevel("PLATFORM");
        sourceRoot.setComponent("Layout");
        sourceRoot.setIcon("setting");
        sourceRoot.setShowIcon(true);
        Resource sourceButton = resource(11L, null, "userCreate", 10L, "/system/user", "", "system:user:create", ResourceType.BUTTON);
        sourceButton.setResourceLevel("PLATFORM");
        sourceButton.setTitle("创建用户");
        sourceButton.setSort(20);
        Resource sourceApi = resource(12L, null, "userListApi", null, "", "/sys/users", "system:user:list", ResourceType.API);
        sourceApi.setResourceLevel("PLATFORM");
        sourceApi.setTitle("用户列表接口");
        sourceApi.setMethod("GET");
        Role sourceAdmin = role(20L, null, "ROLE_ADMIN", "管理员");
        sourceAdmin.setRoleLevel("PLATFORM");

        when(carrierProjectionRepository.findTemplateSnapshotViewsByScope(null, "PLATFORM"))
            .thenReturn(List.of(snapshot(sourceRoot), snapshot(sourceButton), snapshot(sourceApi)));
        when(roleRepository.findByTenantIdIsNullOrderByIdAsc()).thenReturn(List.of(sourceAdmin));
        when(roleRepository.findGrantedRoleCarrierPairsForPlatformTemplate()).thenReturn(List.of(relation(20L, 12L)));

        AtomicLong nextRoleId = new AtomicLong(200L);
        when(roleRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Role> roles = invocation.getArgument(0);
            for (Role role : roles) {
                if (role.getId() == null) {
                    role.setId(nextRoleId.getAndIncrement());
                }
            }
            return roles;
        });
        when(carrierProjectionRepository.findPermissionBindingViewsByIdsAndScope(List.of(100L, 101L, 102L), 9L, "TENANT"))
            .thenReturn(List.of(
                binding(100L, "system:entry:view", 9000L),
                binding(101L, "system:user:create", 9001L),
                binding(102L, "system:user:list", 9002L)
            ));

        service.bootstrapFromPlatformTemplate(targetTenant);

        verify(resourceRepository, never()).saveAll(any());
        ArgumentCaptor<List<UiActionEntry>> uiActionCaptor = ArgumentCaptor.forClass(List.class);
        verify(uiActionEntryRepository, times(2)).saveAll(uiActionCaptor.capture());
        UiActionEntry clonedButton = uiActionCaptor.getAllValues().getFirst().stream()
            .filter(entry -> "userCreate".equals(entry.getName()))
            .findFirst()
            .orElseThrow();
        assertThat(clonedButton.getPagePath()).isEqualTo("/system/user");
        assertThat(clonedButton.getTitle()).isEqualTo("创建用户");
        assertThat(clonedButton.getSort()).isEqualTo(20);

        ArgumentCaptor<List<ApiEndpointEntry>> apiCaptor = ArgumentCaptor.forClass(List.class);
        verify(apiEndpointEntryRepository).saveAll(apiCaptor.capture());
        ApiEndpointEntry clonedApi = apiCaptor.getValue().stream()
            .filter(entry -> "userListApi".equals(entry.getName()))
            .findFirst()
            .orElseThrow();
        assertThat(clonedApi.getUri()).isEqualTo("/sys/users");
        assertThat(clonedApi.getMethod()).isEqualTo("GET");
        assertThat(clonedApi.getTitle()).isEqualTo("用户列表接口");

        verify(roleRepository).addRolePermissionRelationByPermissionId(9L, 200L, 9002L);
    }

    @Test
    void bootstrapFromPlatformTemplate_should_fallback_to_resource_snapshot_when_carrier_snapshot_incomplete() {
        Tenant targetTenant = tenant(9L, "tenant-9");

        Resource sourceRoot = resource(10L, null, "scheduling", null, "scheduling", "", "", ResourceType.DIRECTORY);
        sourceRoot.setResourceLevel("PLATFORM");
        Resource sourceRead = resource(11L, null, "scheduling-authority-read", 10L, "", "", "scheduling:console:view", ResourceType.BUTTON);
        sourceRead.setResourceLevel("PLATFORM");
        Role sourceAdmin = role(20L, null, "ROLE_ADMIN", "管理员");
        sourceAdmin.setRoleLevel("PLATFORM");

        when(carrierProjectionRepository.findTemplateSnapshotViewsByScope(null, "PLATFORM"))
            .thenReturn(List.of(snapshot(sourceRoot), snapshot(sourceRead)));
        when(roleRepository.findByTenantIdIsNullOrderByIdAsc()).thenReturn(List.of(sourceAdmin));
        when(roleRepository.findGrantedRoleCarrierPairsForPlatformTemplate()).thenReturn(List.of(relation(20L, 11L)));

        AtomicLong nextResourceId = new AtomicLong(100L);
        when(resourceRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Resource> resources = invocation.getArgument(0);
            for (Resource resource : resources) {
                if (resource.getId() == null) {
                    resource.setId(nextResourceId.getAndIncrement());
                }
            }
            return resources;
        });

        AtomicLong nextRoleId = new AtomicLong(200L);
        when(roleRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Role> roles = invocation.getArgument(0);
            for (Role role : roles) {
                if (role.getId() == null) {
                    role.setId(nextRoleId.getAndIncrement());
                }
            }
            return roles;
        });
        when(carrierProjectionRepository.findPermissionBindingViewsByIdsAndScope(List.of(100L, 101L), 9L, "TENANT"))
            .thenReturn(List.of(binding(101L, "scheduling:console:view", 9001L)));

        assertThatThrownBy(() -> service.bootstrapFromPlatformTemplate(targetTenant))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("平台模板权限绑定快照不完整");

        verify(carrierProjectionRepository).findPermissionBindingViewsByIdsAndScope(List.of(100L, 101L), 9L, "TENANT");
        verify(resourceRepository, never()).findRolePermissionBindingViewsByIdsAndScope(any(), any(), any());
    }

    @Test
    void bootstrapFromPlatformTemplate_should_fail_when_carrier_required_permission_binding_is_null() {
        Tenant targetTenant = tenant(9L, "tenant-9");

        Resource sourceRoot = resource(10L, null, "scheduling", null, "scheduling", "", "", ResourceType.DIRECTORY);
        sourceRoot.setResourceLevel("PLATFORM");
        Resource sourceRead = resource(11L, null, "scheduling-authority-read", 10L, "", "", "scheduling:console:view", ResourceType.BUTTON);
        sourceRead.setResourceLevel("PLATFORM");
        Role sourceAdmin = role(20L, null, "ROLE_ADMIN", "管理员");
        sourceAdmin.setRoleLevel("PLATFORM");

        when(carrierProjectionRepository.findTemplateSnapshotViewsByScope(null, "PLATFORM"))
            .thenReturn(List.of(snapshot(sourceRoot), snapshot(sourceRead)));
        when(roleRepository.findByTenantIdIsNullOrderByIdAsc()).thenReturn(List.of(sourceAdmin));
        when(roleRepository.findGrantedRoleCarrierPairsForPlatformTemplate()).thenReturn(List.of(relation(20L, 11L)));

        AtomicLong nextResourceId = new AtomicLong(100L);
        when(resourceRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Resource> resources = invocation.getArgument(0);
            for (Resource resource : resources) {
                if (resource.getId() == null) {
                    resource.setId(nextResourceId.getAndIncrement());
                }
            }
            return resources;
        });

        AtomicLong nextRoleId = new AtomicLong(200L);
        when(roleRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Role> roles = invocation.getArgument(0);
            for (Role role : roles) {
                if (role.getId() == null) {
                    role.setId(nextRoleId.getAndIncrement());
                }
            }
            return roles;
        });
        when(carrierProjectionRepository.findPermissionBindingViewsByIdsAndScope(List.of(100L, 101L), 9L, "TENANT"))
            .thenReturn(List.of(binding(100L, null, null), binding(101L, "scheduling:console:view", 9001L)));

        assertThatThrownBy(() -> service.bootstrapFromPlatformTemplate(targetTenant))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("平台模板权限绑定快照不完整");

        verify(carrierProjectionRepository).findPermissionBindingViewsByIdsAndScope(List.of(100L, 101L), 9L, "TENANT");
        verify(resourceRepository, never()).findRolePermissionBindingViewsByIdsAndScope(any(), any(), any());
    }

    @Test
    void bootstrapFromPlatformTemplate_should_fail_closed_when_carrier_snapshot_conflicts_on_same_resource_id() {
        Tenant targetTenant = tenant(9L, "tenant-9");

        Resource sourceRoot = resource(10L, null, "scheduling", null, "scheduling", "", "", ResourceType.DIRECTORY);
        sourceRoot.setResourceLevel("PLATFORM");
        Resource sourceRead = resource(11L, null, "scheduling-authority-read", 10L, "", "", "scheduling:console:view", ResourceType.BUTTON);
        sourceRead.setResourceLevel("PLATFORM");
        Role sourceAdmin = role(20L, null, "ROLE_ADMIN", "管理员");
        sourceAdmin.setRoleLevel("PLATFORM");

        when(carrierProjectionRepository.findTemplateSnapshotViewsByScope(null, "PLATFORM"))
            .thenReturn(List.of(snapshot(sourceRoot), snapshot(sourceRead)));
        when(roleRepository.findByTenantIdIsNullOrderByIdAsc()).thenReturn(List.of(sourceAdmin));
        when(roleRepository.findGrantedRoleCarrierPairsForPlatformTemplate()).thenReturn(List.of(relation(20L, 11L)));

        AtomicLong nextResourceId = new AtomicLong(100L);
        when(resourceRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Resource> resources = invocation.getArgument(0);
            for (Resource resource : resources) {
                if (resource.getId() == null) {
                    resource.setId(nextResourceId.getAndIncrement());
                }
            }
            return resources;
        });

        AtomicLong nextRoleId = new AtomicLong(200L);
        when(roleRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Role> roles = invocation.getArgument(0);
            for (Role role : roles) {
                if (role.getId() == null) {
                    role.setId(nextRoleId.getAndIncrement());
                }
            }
            return roles;
        });
        when(carrierProjectionRepository.findPermissionBindingViewsByIdsAndScope(List.of(100L, 101L), 9L, "TENANT"))
            .thenReturn(List.of(
                binding(101L, "scheduling:console:view", 9001L),
                binding(101L, "scheduling:console:view", 9011L)
            ));

        assertThatThrownBy(() -> service.bootstrapFromPlatformTemplate(targetTenant))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("权限快照冲突");
    }

    @Test
    void bootstrapFromPlatformTemplate_whenTemplatesMissing_shouldBackfillFromConfiguredTenant() {
        Tenant defaultTenant = tenant(1L, "default");
        Tenant targetTenant = tenant(9L, "tenant-9");

        Resource sourceRoot = resource(10L, 1L, "system", null, "/system", "", "system:entry:view", ResourceType.DIRECTORY);
        Resource sourceUser = resource(11L, 1L, "user", 10L, "/system/user", "/sys/users", "system:user:list", ResourceType.MENU);
        Resource platformRoot = resource(100L, null, "system", null, "/system", "", "system:entry:view", ResourceType.DIRECTORY);
        platformRoot.setRequiredPermissionId(1000L);
        Resource platformUser = resource(101L, null, "user", 100L, "/system/user", "/sys/users", "system:user:list", ResourceType.MENU);
        platformUser.setRequiredPermissionId(1001L);
        Role sourceAdmin = role(20L, 1L, "ROLE_ADMIN", "管理员");

        when(carrierProjectionRepository.findTemplateSnapshotViewsByScope(1L, "TENANT"))
            .thenReturn(List.of(snapshot(sourceRoot), snapshot(sourceUser)));
        when(carrierProjectionRepository.findTemplateSnapshotViewsByScope(null, "PLATFORM"))
            .thenReturn(List.of())
            .thenReturn(List.of(snapshot(platformRoot), snapshot(platformUser)));
        when(roleRepository.findByTenantIdIsNullOrderByIdAsc())
            .thenReturn(List.of())
            .thenReturn(List.of(role(200L, null, "ROLE_ADMIN", "管理员")));
        when(roleRepository.findGrantedRoleCarrierPairsForPlatformTemplate()).thenReturn(List.of(relation(200L, 101L)));
        when(tenantRepository.findByCode("default")).thenReturn(Optional.of(defaultTenant));
        when(roleRepository.findByTenantIdOrderByIdAsc(1L)).thenReturn(List.of(sourceAdmin));
        when(roleRepository.findGrantedRoleCarrierPairsByTenantId(1L)).thenReturn(List.of(relation(20L, 11L)));

        AtomicLong nextResourceId = new AtomicLong(100L);
        when(resourceRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Resource> resources = invocation.getArgument(0);
            for (Resource resource : resources) {
                if (resource.getId() == null) {
                    resource.setId(nextResourceId.getAndIncrement());
                }
            }
            return resources;
        });

        AtomicLong nextRoleId = new AtomicLong(200L);
        when(roleRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Role> roles = invocation.getArgument(0);
            for (Role role : roles) {
                if (role.getId() == null) {
                    role.setId(nextRoleId.getAndIncrement());
                }
            }
            return roles;
        });
        when(carrierProjectionRepository.findPermissionBindingViewsByIdsAndScope(List.of(100L, 101L), null, "PLATFORM"))
            .thenReturn(List.of(binding(100L, "system:entry:view", 1000L), binding(101L, "system:user:list", 1001L)));
        when(carrierProjectionRepository.findPermissionBindingViewsByIdsAndScope(List.of(102L, 103L), 9L, "TENANT"))
            .thenReturn(List.of(binding(102L, "system:entry:view", 2000L), binding(103L, "system:user:list", 2001L)));

        service.bootstrapFromPlatformTemplate(targetTenant);

        verify(tenantRepository).findByCode("default");
        verify(resourcePermissionBindingService, times(2)).backfillPermissionCatalogFromResources(null);
        verify(resourcePermissionBindingService, times(2)).bindRequiredPermissionIdsForResources(null);
        verify(roleRepository).addRolePermissionRelationByPermissionId(null, 200L, 1001L);
        verify(roleRepository).addRolePermissionRelationByPermissionId(9L, 201L, 2001L);
    }

    @Test
    void ensurePlatformTemplatesInitialized_whenTemplatesExist_shouldNoop() {
        Resource template = resource(100L, null, "system", null, "/system", "", "system:entry:view", ResourceType.DIRECTORY);
        template.setResourceLevel("PLATFORM");
        when(carrierProjectionRepository.findTemplateSnapshotViewsByScope(null, "PLATFORM"))
            .thenReturn(List.of(snapshot(template)));
        when(roleRepository.findByTenantIdIsNullOrderByIdAsc())
            .thenReturn(List.of(role(200L, null, "ROLE_ADMIN", "管理员")));

        boolean initialized = service.ensurePlatformTemplatesInitialized();

        assertThat(initialized).isFalse();
        verify(resourcePermissionBindingService).backfillPermissionCatalogFromResources(null);
        verify(resourcePermissionBindingService).bindRequiredPermissionIdsForResources(null);
        verify(tenantRepository, times(0)).findByCode(any());
        verify(resourceRepository, times(0)).saveAll(any());
        verify(roleRepository, times(0)).saveAll(any());
    }

    @Test
    void ensurePlatformTemplatesInitialized_whenTemplatesMissing_shouldBackfillFromConfiguredTenant() {
        Tenant defaultTenant = tenant(1L, "default");
        Resource sourceRoot = resource(10L, 1L, "system", null, "/system", "", "system:entry:view", ResourceType.DIRECTORY);
        Role sourceAdmin = role(20L, 1L, "ROLE_ADMIN", "管理员");

        when(carrierProjectionRepository.findTemplateSnapshotViewsByScope(null, "PLATFORM")).thenReturn(List.of());
        when(roleRepository.findByTenantIdIsNullOrderByIdAsc()).thenReturn(List.of());
        when(tenantRepository.findByCode("default")).thenReturn(Optional.of(defaultTenant));
        when(carrierProjectionRepository.findTemplateSnapshotViewsByScope(1L, "TENANT"))
            .thenReturn(List.of(snapshot(sourceRoot)));
        when(roleRepository.findByTenantIdOrderByIdAsc(1L)).thenReturn(List.of(sourceAdmin));
        when(roleRepository.findGrantedRoleCarrierPairsByTenantId(1L)).thenReturn(List.of(relation(20L, 10L)));

        AtomicLong nextResourceId = new AtomicLong(100L);
        when(resourceRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Resource> resources = invocation.getArgument(0);
            for (Resource resource : resources) {
                if (resource.getId() == null) {
                    resource.setId(nextResourceId.getAndIncrement());
                }
            }
            return resources;
        });

        AtomicLong nextRoleId = new AtomicLong(200L);
        when(roleRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Role> roles = invocation.getArgument(0);
            for (Role role : roles) {
                if (role.getId() == null) {
                    role.setId(nextRoleId.getAndIncrement());
                }
            }
            return roles;
        });
        when(carrierProjectionRepository.findPermissionBindingViewsByIdsAndScope(List.of(100L), null, "PLATFORM"))
            .thenReturn(List.of(binding(100L, "system:entry:view", 3001L)));

        boolean initialized = service.ensurePlatformTemplatesInitialized();

        assertThat(initialized).isTrue();
        verify(tenantRepository).findByCode("default");
        verify(roleRepository).addRolePermissionRelationByPermissionId(null, 200L, 3001L);
    }

    @Test
    void ensurePlatformTemplatesInitialized_shouldNormalizeRoleNamesAndSuffixPlatformTemplateCodes() {
        Tenant defaultTenant = tenant(1L, "default");
        Resource sourceRoot = resource(10L, 1L, "system", null, "/system", "", "system:entry:view", ResourceType.DIRECTORY);
        Role dirtyAdmin = role(20L, 1L, "ROLE_ADMIN", " 超级管理员 \n\n ");
        Role userRole = role(21L, 1L, "ROLE_USER", "普通用户");


        when(roleRepository.findByTenantIdIsNullOrderByIdAsc()).thenReturn(List.of());
        when(tenantRepository.findByCode("default")).thenReturn(Optional.of(defaultTenant));
        when(carrierProjectionRepository.findTemplateSnapshotViewsByScope(null, "PLATFORM")).thenReturn(List.of());
        when(carrierProjectionRepository.findTemplateSnapshotViewsByScope(1L, "TENANT"))
            .thenReturn(List.of(snapshot(sourceRoot)));
        when(roleRepository.findByTenantIdOrderByIdAsc(1L)).thenReturn(List.of(dirtyAdmin, userRole));
        when(roleRepository.findGrantedRoleCarrierPairsByTenantId(1L)).thenReturn(List.of(relation(20L, 10L)));

        AtomicLong nextResourceId = new AtomicLong(100L);
        when(resourceRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Resource> resources = invocation.getArgument(0);
            for (Resource resource : resources) {
                if (resource.getId() == null) {
                    resource.setId(nextResourceId.getAndIncrement());
                }
            }
            return resources;
        });

        AtomicLong nextRoleId = new AtomicLong(200L);
        when(roleRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Role> roles = invocation.getArgument(0);
            for (Role role : roles) {
                if (role.getId() == null) {
                    role.setId(nextRoleId.getAndIncrement());
                }
            }
            return roles;
        });
        when(carrierProjectionRepository.findPermissionBindingViewsByIdsAndScope(List.of(100L), null, "PLATFORM"))
            .thenReturn(List.of(binding(100L, "system:entry:view", 3100L)));

        assertThat(service.ensurePlatformTemplatesInitialized()).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Role>> roleCaptor = ArgumentCaptor.forClass(List.class);
        verify(roleRepository).saveAll(roleCaptor.capture());
        List<Role> saved = roleCaptor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getName()).isEqualTo("超级管理员 · ROLE_ADMIN");
        assertThat(saved.get(1).getName()).isEqualTo("普通用户 · ROLE_USER");
    }

    @Test
    void ensurePlatformTemplatesInitialized_shouldTruncateLongPlatformRoleNamesToFitColumn() {
        Tenant defaultTenant = tenant(1L, "default");
        Resource sourceRoot = resource(10L, 1L, "system", null, "/system", "", "system:entry:view", ResourceType.DIRECTORY);
        String longBase = "x".repeat(50);
        Role longNameAdmin = role(20L, 1L, "ROLE_ADMIN", longBase);
        String longCode = "C".repeat(50);
        Role longCodeRole = role(21L, 1L, longCode, "短名");


        when(roleRepository.findByTenantIdIsNullOrderByIdAsc()).thenReturn(List.of());
        when(tenantRepository.findByCode("default")).thenReturn(Optional.of(defaultTenant));
        when(carrierProjectionRepository.findTemplateSnapshotViewsByScope(null, "PLATFORM")).thenReturn(List.of());
        when(carrierProjectionRepository.findTemplateSnapshotViewsByScope(1L, "TENANT"))
            .thenReturn(List.of(snapshot(sourceRoot)));
        when(roleRepository.findByTenantIdOrderByIdAsc(1L)).thenReturn(List.of(longNameAdmin, longCodeRole));
        when(roleRepository.findGrantedRoleCarrierPairsByTenantId(1L)).thenReturn(List.of(relation(20L, 10L)));

        AtomicLong nextResourceId = new AtomicLong(100L);
        when(resourceRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Resource> resources = invocation.getArgument(0);
            for (Resource resource : resources) {
                if (resource.getId() == null) {
                    resource.setId(nextResourceId.getAndIncrement());
                }
            }
            return resources;
        });

        AtomicLong nextRoleId = new AtomicLong(200L);
        when(roleRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Role> roles = invocation.getArgument(0);
            for (Role role : roles) {
                if (role.getId() == null) {
                    role.setId(nextRoleId.getAndIncrement());
                }
            }
            return roles;
        });
        when(carrierProjectionRepository.findPermissionBindingViewsByIdsAndScope(List.of(100L), null, "PLATFORM"))
            .thenReturn(List.of(binding(100L, "system:entry:view", 3200L)));

        assertThat(service.ensurePlatformTemplatesInitialized()).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Role>> roleCaptor = ArgumentCaptor.forClass(List.class);
        verify(roleRepository).saveAll(roleCaptor.capture());
        List<Role> saved = roleCaptor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getName()).hasSize(50).startsWith("x").endsWith(" · ROLE_ADMIN");
        assertThat(saved.get(1).getName()).hasSize(50);
    }

    @Test
    void ensurePlatformTemplatesInitialized_whenTemplatesIncomplete_shouldFail() {
        Resource template = resource(100L, null, "system", null, "/system", "", "system:entry:view", ResourceType.DIRECTORY);
        template.setResourceLevel("PLATFORM");
        when(carrierProjectionRepository.findTemplateSnapshotViewsByScope(null, "PLATFORM"))
            .thenReturn(List.of(snapshot(template)));
        when(roleRepository.findByTenantIdIsNullOrderByIdAsc()).thenReturn(List.of());

        assertThatThrownBy(() -> service.ensurePlatformTemplatesInitialized())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("平台模板数据不完整");
    }

    @Test
    void bootstrapFromPlatformTemplate_whenTemplatesAndConfiguredTenantMissing_shouldFail() {
        Tenant targetTenant = tenant(9L, "tenant-9");
        when(carrierProjectionRepository.findTemplateSnapshotViewsByScope(null, "PLATFORM")).thenReturn(List.of());
        when(roleRepository.findByTenantIdIsNullOrderByIdAsc()).thenReturn(List.of());
        when(tenantRepository.findByCode("default")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bootstrapFromPlatformTemplate(targetTenant))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("平台模板缺失");
    }

    @Test
    void bootstrapFromPlatformTemplate_whenTargetTenantAlreadyHasDerivedTemplates_shouldFailClosed() {
        Tenant targetTenant = tenant(9L, "tenant-9");

        Resource template = resource(100L, null, "system", null, "/system", "", "system:entry:view", ResourceType.DIRECTORY);
        template.setResourceLevel("PLATFORM");
        when(carrierProjectionRepository.findTemplateSnapshotViewsByScope(null, "PLATFORM"))
            .thenReturn(List.of(snapshot(template)));
        Role templateRole = role(200L, null, "ROLE_ADMIN", "管理员");
        templateRole.setRoleLevel("PLATFORM");
        when(roleRepository.findByTenantIdIsNullOrderByIdAsc()).thenReturn(List.of(templateRole));

        when(menuEntryRepository.count(org.mockito.Mockito.<org.springframework.data.jpa.domain.Specification<MenuEntry>>any()))
            .thenReturn(1L);

        assertThatThrownBy(() -> service.bootstrapFromPlatformTemplate(targetTenant))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("不允许重复从平台模板派生");
    }

    @Test
    void diffPlatformTemplateForTenant_shouldDetectMissingAndChangedEntries() {
        Long tenantId = 9L;

        Resource platformMenu = resource(10L, null, "system", null, "/system", "", "system:entry:view", ResourceType.DIRECTORY);
        platformMenu.setResourceLevel("PLATFORM");
        platformMenu.setTitle("系统");
        platformMenu.setEnabled(true);
        platformMenu.setRequiredPermissionId(1000L);

        Resource platformButton = resource(11L, null, "userCreate", 10L, "/system/user", "", "system:user:create", ResourceType.BUTTON);
        platformButton.setResourceLevel("PLATFORM");
        platformButton.setTitle("创建用户");
        platformButton.setSort(1);
        platformButton.setEnabled(true);
        platformButton.setRequiredPermissionId(1001L);

        Role platformRole = role(20L, null, "ROLE_ADMIN", "管理员");
        platformRole.setRoleLevel("PLATFORM");

        when(carrierProjectionRepository.findTemplateSnapshotViewsByScope(null, "PLATFORM"))
            .thenReturn(List.of(snapshot(platformMenu), snapshot(platformButton)));
        when(roleRepository.findByTenantIdIsNullOrderByIdAsc()).thenReturn(List.of(platformRole));

        Resource tenantMenu = resource(100L, tenantId, "system", null, "/system", "", "system:entry:view", ResourceType.DIRECTORY);
        tenantMenu.setResourceLevel("TENANT");
        tenantMenu.setTitle("系统(租户改名)");
        tenantMenu.setEnabled(true);
        tenantMenu.setRequiredPermissionId(1000L);

        when(carrierProjectionRepository.findTemplateSnapshotViewsByScope(tenantId, "TENANT"))
            .thenReturn(List.of(snapshot(tenantMenu)));

        PlatformTemplateDiffResult diff = service.diffPlatformTemplateForTenant(tenantId);
        assertThat(diff.tenantId()).isEqualTo(tenantId);
        assertThat(diff.summary().missingInTenant()).isEqualTo(1);
        assertThat(diff.summary().changed()).isEqualTo(1);
        assertThat(diff.diffs()).anySatisfy(d -> {
            assertThat(d.diffType()).isEqualTo(PlatformTemplateDiffResult.DiffType.MISSING_IN_TENANT);
            assertThat(d.carrierType()).isEqualTo("ui_action");
            assertThat(d.key()).contains("system:user:create");
        });
        assertThat(diff.diffs()).anySatisfy(d -> {
            assertThat(d.diffType()).isEqualTo(PlatformTemplateDiffResult.DiffType.CHANGED);
            assertThat(d.carrierType()).isEqualTo("menu");
            assertThat(d.fieldDiffs()).containsKey("title");
        });
    }

    @Test
    void bootstrapFromPlatformTemplate_shouldSkipPlatformOnlyResources() {
        Tenant targetTenant = tenant(9L, "tenant-9");

        Resource sourceSystem = resource(1L, null, "system", null, "/system", "", "system", ResourceType.DIRECTORY);
        sourceSystem.setResourceLevel("PLATFORM");
        Resource sourceUser = resource(2L, null, "user", 1L, "/system/user", "/api/users", "system:user:list", ResourceType.MENU);
        sourceUser.setResourceLevel("PLATFORM");
        Resource sourceTenantMenu = resource(3L, null, "tenant", 1L, "/system/tenant", "/sys/tenants", "system:tenant:list", ResourceType.MENU);
        sourceTenantMenu.setResourceLevel("PLATFORM");
        Resource sourceIdempotentMenu = resource(4L, null, "idempotentOps", 1L, "/ops/idempotent", "/metrics/idempotent", "idempotent:ops:view", ResourceType.MENU);
        sourceIdempotentMenu.setResourceLevel("PLATFORM");
        Role sourceAdmin = role(20L, null, "ROLE_ADMIN", "管理员");
        sourceAdmin.setRoleLevel("PLATFORM");

        when(carrierProjectionRepository.findTemplateSnapshotViewsByScope(null, "PLATFORM"))
            .thenReturn(List.of(
                snapshot(sourceSystem),
                snapshot(sourceUser),
                snapshot(sourceTenantMenu),
                snapshot(sourceIdempotentMenu)
            ));
        when(roleRepository.findByTenantIdIsNullOrderByIdAsc()).thenReturn(List.of(sourceAdmin));
        when(roleRepository.findGrantedRoleCarrierPairsForPlatformTemplate())
            .thenReturn(List.of(relation(20L, 2L), relation(20L, 3L), relation(20L, 4L)));

        AtomicLong nextResourceId = new AtomicLong(100L);
        when(resourceRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Resource> resources = invocation.getArgument(0);
            for (Resource resource : resources) {
                if (resource.getId() == null) {
                    resource.setId(nextResourceId.getAndIncrement());
                }
            }
            return resources;
        });

        AtomicLong nextRoleId = new AtomicLong(200L);
        when(roleRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Role> roles = invocation.getArgument(0);
            for (Role role : roles) {
                if (role.getId() == null) {
                    role.setId(nextRoleId.getAndIncrement());
                }
            }
            return roles;
        });
        when(carrierProjectionRepository.findPermissionBindingViewsByIdsAndScope(List.of(100L, 101L), 9L, "TENANT"))
            .thenReturn(List.of(binding(100L, "system", 7000L), binding(101L, "system:user:list", 7001L)));

        service.bootstrapFromPlatformTemplate(targetTenant);

        verify(resourceRepository, never()).saveAll(any());
        ArgumentCaptor<List<MenuEntry>> menuCaptor = ArgumentCaptor.forClass(List.class);
        verify(menuEntryRepository, times(2)).saveAll(menuCaptor.capture());
        List<MenuEntry> clonedMenus = menuCaptor.getAllValues().getFirst();
        assertThat(clonedMenus)
            .extracting(MenuEntry::getName)
            .containsExactlyInAnyOrder("system", "user");
        assertThat(clonedMenus)
            .extracting(MenuEntry::getTenantId)
            .containsOnly(9L);

        verify(roleRepository).addRolePermissionRelationByPermissionId(9L, 200L, 7001L);
        verify(roleRepository, times(1)).addRolePermissionRelationByPermissionId(any(), any(), any());
    }

    @Test
    void bootstrapFromPlatformTemplate_should_fail_when_target_resources_still_missing_required_permission_binding() {
        Tenant targetTenant = tenant(9L, "tenant-9");

        Resource sourceRoot = resource(10L, null, "scheduling", null, "scheduling", "", "", ResourceType.DIRECTORY);
        sourceRoot.setResourceLevel("PLATFORM");
        Resource sourceRead = resource(11L, null, "scheduling-authority-read", 10L, "", "", "scheduling:console:view", ResourceType.BUTTON);
        sourceRead.setResourceLevel("PLATFORM");
        Role sourceAdmin = role(20L, null, "ROLE_ADMIN", "管理员");
        sourceAdmin.setRoleLevel("PLATFORM");

        when(carrierProjectionRepository.findTemplateSnapshotViewsByScope(null, "PLATFORM"))
            .thenReturn(List.of(snapshot(sourceRoot), snapshot(sourceRead)));
        when(roleRepository.findByTenantIdIsNullOrderByIdAsc()).thenReturn(List.of(sourceAdmin));
        when(roleRepository.findGrantedRoleCarrierPairsForPlatformTemplate()).thenReturn(List.of(relation(20L, 11L)));

        AtomicLong nextResourceId = new AtomicLong(100L);
        when(resourceRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Resource> resources = invocation.getArgument(0);
            for (Resource resource : resources) {
                if (resource.getId() == null) {
                    resource.setId(nextResourceId.getAndIncrement());
                }
            }
            return resources;
        });

        AtomicLong nextRoleId = new AtomicLong(200L);
        when(roleRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Role> roles = invocation.getArgument(0);
            for (Role role : roles) {
                if (role.getId() == null) {
                    role.setId(nextRoleId.getAndIncrement());
                }
            }
            return roles;
        });

        Resource clonedBroken = resource(101L, 9L, "scheduling-authority-read", 100L, "", "", "scheduling:console:view", ResourceType.BUTTON);
        clonedBroken.setRequiredPermissionId(null);
        when(carrierProjectionRepository.findTemplateSnapshotViewsByScope(9L, "TENANT"))
            .thenReturn(List.of(snapshot(clonedBroken)));

        assertThatThrownBy(() -> service.bootstrapFromPlatformTemplate(targetTenant))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("未完成权限绑定");
    }

    @Test
    void bootstrapFromPlatformTemplate_whenTargetTenantAlreadyHasDerivedTemplates_shouldFail() {
        Tenant targetTenant = tenant(9L, "tenant-9");
        when(menuEntryRepository.count(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.bootstrapFromPlatformTemplate(targetTenant))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("目标租户已存在角色或资源副本");
    }

    @Test
    void ensurePlatformTemplatesInitialized_whenPlatformSnapshotContainsTenantLevelRows_shouldFail() {
        Resource invalidPlatformResource = resource(100L, null, "system", null, "/system", "", "system:entry:view", ResourceType.DIRECTORY);
        invalidPlatformResource.setResourceLevel("TENANT");
        Role invalidPlatformRole = role(200L, null, "ROLE_ADMIN", "管理员");
        invalidPlatformRole.setRoleLevel("TENANT");

        when(carrierProjectionRepository.findTemplateSnapshotViewsByScope(null, "PLATFORM"))
            .thenReturn(List.of(snapshot(invalidPlatformResource)));
        when(roleRepository.findByTenantIdIsNullOrderByIdAsc()).thenReturn(List.of(invalidPlatformRole));

        assertThatThrownBy(() -> service.ensurePlatformTemplatesInitialized())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("平台模板");
    }

    private Tenant tenant(Long id, String code) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setCode(code);
        tenant.setName(code);
        return tenant;
    }

    private Resource resource(Long id, Long tenantId, String name, Long parentId, String url, String uri,
                              String permission, ResourceType type) {
        Resource resource = new Resource();
        resource.setId(id);
        resource.setTenantId(tenantId);
        resource.setResourceLevel(tenantId == null ? "PLATFORM" : "TENANT");
        resource.setName(name);
        resource.setTitle(name);
        resource.setParentId(parentId);
        resource.setUrl(url);
        resource.setUri(uri);
        resource.setMethod("");
        resource.setPermission(permission);
        resource.setType(type);
        resource.setEnabled(true);
        resource.setHidden(false);
        return resource;
    }

    private Role role(Long id, Long tenantId, String code, String name) {
        Role role = new Role();
        role.setId(id);
        role.setTenantId(tenantId);
        role.setRoleLevel(tenantId == null ? "PLATFORM" : "TENANT");
        role.setCode(code);
        role.setName(name);
        role.setEnabled(true);
        role.setBuiltin(true);
        return role;
    }

    private RoleResourceRelationProjection relation(Long roleId, Long resourceId) {
        return new RoleResourceRelationProjection() {
            @Override
            public Long getRoleId() {
                return roleId;
            }

            @Override
            public Long getResourceId() {
                return resourceId;
            }
        };
    }

    private RoleResourcePermissionBindingView binding(Long id, String permission, Long requiredPermissionId) {
        return new RoleResourcePermissionBindingView() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public String getPermission() {
                return permission;
            }

            @Override
            public Long getRequiredPermissionId() {
                return requiredPermissionId;
            }
        };
    }

    private com.tiny.platform.infrastructure.auth.resource.repository.CarrierTemplateResourceSnapshotView snapshot(Resource source) {
        return new com.tiny.platform.infrastructure.auth.resource.repository.CarrierTemplateResourceSnapshotView() {
            @Override
            public Long getId() {
                return source.getId();
            }

            @Override
            public Long getTenantId() {
                return source.getTenantId();
            }

            @Override
            public String getResourceLevel() {
                return source.getResourceLevel();
            }

            @Override
            public String getName() {
                return source.getName();
            }

            @Override
            public String getUrl() {
                return source.getUrl();
            }

            @Override
            public String getUri() {
                return source.getUri();
            }

            @Override
            public String getMethod() {
                return source.getMethod();
            }

            @Override
            public String getIcon() {
                return source.getIcon();
            }

            @Override
            public Long getShowIcon() {
                return source.getShowIcon() == null ? null : (source.getShowIcon() ? 1L : 0L);
            }

            @Override
            public Integer getSort() {
                return source.getSort();
            }

            @Override
            public String getComponent() {
                return source.getComponent();
            }

            @Override
            public String getRedirect() {
                return source.getRedirect();
            }

            @Override
            public Long getHidden() {
                return source.getHidden() == null ? null : (source.getHidden() ? 1L : 0L);
            }

            @Override
            public Long getKeepAlive() {
                return source.getKeepAlive() == null ? null : (source.getKeepAlive() ? 1L : 0L);
            }

            @Override
            public String getTitle() {
                return source.getTitle();
            }

            @Override
            public String getPermission() {
                return source.getPermission();
            }

            @Override
            public Long getRequiredPermissionId() {
                return source.getRequiredPermissionId();
            }

            @Override
            public Integer getTypeCode() {
                return source.getType().getCode();
            }

            @Override
            public Long getParentId() {
                return source.getParentId();
            }

            @Override
            public Long getEnabled() {
                return source.getEnabled() == null ? null : (source.getEnabled() ? 1L : 0L);
            }
        };
    }
}
