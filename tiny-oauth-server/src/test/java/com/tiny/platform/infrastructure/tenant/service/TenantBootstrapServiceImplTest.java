package com.tiny.platform.infrastructure.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import com.tiny.platform.infrastructure.auth.resource.repository.ResourceRepository;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleResourceRelationProjection;
import com.tiny.platform.infrastructure.tenant.config.PlatformTenantProperties;
import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TenantBootstrapServiceImplTest {

    private TenantRepository tenantRepository;
    private ResourceRepository resourceRepository;
    private RoleRepository roleRepository;
    private PlatformTenantProperties platformTenantProperties;
    private TenantBootstrapServiceImpl service;

    @BeforeEach
    void setUp() {
        tenantRepository = org.mockito.Mockito.mock(TenantRepository.class);
        resourceRepository = org.mockito.Mockito.mock(ResourceRepository.class);
        roleRepository = org.mockito.Mockito.mock(RoleRepository.class);
        platformTenantProperties = new PlatformTenantProperties();
        service = new TenantBootstrapServiceImpl(tenantRepository, resourceRepository, roleRepository, platformTenantProperties);
    }

    @Test
    void bootstrapFromDefaultTenant_shouldCloneResourcesRolesAndRelations() {
        Tenant defaultTenant = tenant(1L, "default");
        Tenant targetTenant = tenant(9L, "tenant-9");

        Resource sourceRoot = resource(10L, 1L, "scheduling", null, "scheduling", "", "", ResourceType.DIRECTORY);
        Resource sourceRead = resource(11L, 1L, "scheduling-authority-read", 10L, "", "", "scheduling:console:view", ResourceType.BUTTON);
        Role sourceAdmin = role(20L, 1L, "ROLE_ADMIN", "管理员");

        when(tenantRepository.findByCode("default")).thenReturn(Optional.of(defaultTenant));
        when(resourceRepository.findByTenantIdOrderBySortAscIdAsc(1L)).thenReturn(List.of(sourceRoot, sourceRead));
        when(roleRepository.findByTenantIdOrderByIdAsc(1L)).thenReturn(List.of(sourceAdmin));
        when(roleRepository.findRoleResourceRelationsByTenantId(1L)).thenReturn(List.of(relation(20L, 11L)));

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

        service.bootstrapFromDefaultTenant(targetTenant);

        ArgumentCaptor<List<Resource>> resourceCaptor = ArgumentCaptor.forClass(List.class);
        verify(resourceRepository, times(2)).saveAll(resourceCaptor.capture());
        List<List<Resource>> resourceSaves = resourceCaptor.getAllValues();
        assertThat(resourceSaves.get(0)).hasSize(2);
        assertThat(resourceSaves.get(0)).allMatch(resource -> resource.getTenantId().equals(9L));
        assertThat(resourceSaves.get(1)).hasSize(2);
        assertThat(resourceSaves.get(1).stream()
            .filter(resource -> "scheduling-authority-read".equals(resource.getName()))
            .findFirst()
            .orElseThrow()
            .getParentId()).isEqualTo(100L);

        ArgumentCaptor<List<Role>> roleCaptor = ArgumentCaptor.forClass(List.class);
        verify(roleRepository).saveAll(roleCaptor.capture());
        assertThat(roleCaptor.getValue()).singleElement().satisfies(clonedRole -> {
            assertThat(clonedRole.getTenantId()).isEqualTo(9L);
            assertThat(clonedRole.getCode()).isEqualTo("ROLE_ADMIN");
            assertThat(clonedRole.getName()).isEqualTo("管理员");
        });

        verify(roleRepository).addRoleResourceRelation(9L, 200L, 101L);
    }

    @Test
    void bootstrapFromDefaultTenant_whenDefaultTenantMissing_shouldFail() {
        Tenant targetTenant = tenant(9L, "tenant-9");
        when(tenantRepository.findByCode("default")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bootstrapFromDefaultTenant(targetTenant))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("平台/模板租户不存在");
    }

    @Test
    void bootstrapFromDefaultTenant_shouldSkipPlatformOnlyResources() {
        Tenant defaultTenant = tenant(1L, "default");
        Tenant targetTenant = tenant(9L, "tenant-9");

        Resource sourceSystem = resource(1L, 1L, "system", null, "/system", "", "system", ResourceType.DIRECTORY);
        Resource sourceUser = resource(2L, 1L, "user", 1L, "/system/user", "/api/users", "system:user:list", ResourceType.MENU);
        Resource sourceTenantMenu = resource(3L, 1L, "tenant", 1L, "/system/tenant", "/sys/tenants", "system:tenant:list", ResourceType.MENU);
        Resource sourceIdempotentMenu = resource(4L, 1L, "idempotentOps", 1L, "/ops/idempotent", "/metrics/idempotent", "idempotent:ops:view", ResourceType.MENU);
        Role sourceAdmin = role(20L, 1L, "ROLE_ADMIN", "管理员");

        when(tenantRepository.findByCode("default")).thenReturn(Optional.of(defaultTenant));
        when(resourceRepository.findByTenantIdOrderBySortAscIdAsc(1L))
            .thenReturn(List.of(sourceSystem, sourceUser, sourceTenantMenu, sourceIdempotentMenu));
        when(roleRepository.findByTenantIdOrderByIdAsc(1L)).thenReturn(List.of(sourceAdmin));
        when(roleRepository.findRoleResourceRelationsByTenantId(1L))
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

        service.bootstrapFromDefaultTenant(targetTenant);

        ArgumentCaptor<List<Resource>> resourceCaptor = ArgumentCaptor.forClass(List.class);
        verify(resourceRepository, times(2)).saveAll(resourceCaptor.capture());
        List<Resource> clonedResources = resourceCaptor.getAllValues().get(0);
        assertThat(clonedResources)
            .extracting(Resource::getName)
            .containsExactlyInAnyOrder("system", "user");
        assertThat(clonedResources)
            .extracting(Resource::getTenantId)
            .containsOnly(9L);

        verify(roleRepository).addRoleResourceRelation(9L, 200L, 101L);
        verify(roleRepository, times(1)).addRoleResourceRelation(any(), any(), any());
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
}
