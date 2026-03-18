package com.tiny.platform.infrastructure.menu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import com.tiny.platform.infrastructure.auth.resource.repository.ResourceRepository;
import com.tiny.platform.infrastructure.tenant.config.PlatformTenantProperties;
import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class MenuServiceImplTest {

    private ResourceRepository resourceRepository;
    private TenantRepository tenantRepository;
    private PlatformTenantProperties platformTenantProperties;
    private MenuServiceImpl service;

    @BeforeEach
    void setUp() {
        resourceRepository = Mockito.mock(ResourceRepository.class);
        tenantRepository = Mockito.mock(TenantRepository.class);
        platformTenantProperties = new PlatformTenantProperties();
        service = new MenuServiceImpl(resourceRepository, tenantRepository, platformTenantProperties);

        Tenant platformTenant = new Tenant();
        platformTenant.setId(1L);
        platformTenant.setCode("default");
        when(tenantRepository.findByCode("default")).thenReturn(Optional.of(platformTenant));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void menuTree_shouldHidePlatformOnlyMenusForNonPlatformTenant() {
        TenantContext.setActiveTenantId(2L);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "tenant-admin",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
            )
        );

        when(resourceRepository.findByTypeInAndTenantIdOrderBySortAsc(anyList(), eq(2L)))
            .thenReturn(List.of(
                resource(1L, 2L, "system", null, "/system", "", "system", ResourceType.DIRECTORY),
                resource(2L, 2L, "user", 1L, "/system/user", "/api/users", "system:user:list", ResourceType.MENU),
                resource(3L, 2L, "tenant", 1L, "/system/tenant", "/sys/tenants", "system:tenant:list", ResourceType.MENU),
                resource(4L, 2L, "idempotentOps", 1L, "/ops/idempotent", "/metrics/idempotent", "idempotent:ops:view", ResourceType.MENU)
            ));
        when(resourceRepository.existsByParentIdAndTenantId(1L, 2L)).thenReturn(true);
        when(resourceRepository.existsByParentIdAndTenantId(2L, 2L)).thenReturn(false);
        when(resourceRepository.existsByParentIdAndTenantId(3L, 2L)).thenReturn(false);
        when(resourceRepository.existsByParentIdAndTenantId(4L, 2L)).thenReturn(false);

        List<ResourceResponseDto> tree = service.menuTree();
        List<ResourceResponseDto> fullTree = service.menuTreeAll();
        List<ResourceResponseDto> children = tree.get(0).getChildren();

        assertThat(tree).singleElement().extracting(ResourceResponseDto::getName).isEqualTo("system");
        assertThat(children).extracting(ResourceResponseDto::getName).containsExactly("user");
        assertThat(fullTree).singleElement().extracting(ResourceResponseDto::getName).isEqualTo("system");
        assertThat(fullTree.get(0).getChildren()).extracting(ResourceResponseDto::getName).containsExactly("user");
    }

    @Test
    void menuTree_shouldKeepPlatformOnlyMenusForPlatformAdminWithAuthority() {
        TenantContext.setActiveTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "platform-admin",
                "n/a",
                List.of(
                    new SimpleGrantedAuthority("ROLE_ADMIN"),
                    new SimpleGrantedAuthority("idempotent:ops:view")
                )
            )
        );

        when(resourceRepository.findByTypeInAndTenantIdOrderBySortAsc(anyList(), eq(1L)))
            .thenReturn(List.of(
                resource(1L, 1L, "system", null, "/system", "", "system", ResourceType.DIRECTORY),
                resource(2L, 1L, "user", 1L, "/system/user", "/api/users", "system:user:list", ResourceType.MENU),
                resource(3L, 1L, "tenant", 1L, "/system/tenant", "/sys/tenants", "system:tenant:list", ResourceType.MENU),
                resource(4L, 1L, "idempotentOps", 1L, "/ops/idempotent", "/metrics/idempotent", "idempotent:ops:view", ResourceType.MENU)
            ));
        when(resourceRepository.existsByParentIdAndTenantId(1L, 1L)).thenReturn(true);
        when(resourceRepository.existsByParentIdAndTenantId(2L, 1L)).thenReturn(false);
        when(resourceRepository.existsByParentIdAndTenantId(3L, 1L)).thenReturn(false);
        when(resourceRepository.existsByParentIdAndTenantId(4L, 1L)).thenReturn(false);

        List<ResourceResponseDto> tree = service.menuTree();

        assertThat(tree).singleElement().extracting(ResourceResponseDto::getName).isEqualTo("system");
        assertThat(tree.get(0).getChildren())
            .extracting(ResourceResponseDto::getName)
            .containsExactly("user", "tenant", "idempotentOps");
    }

    @Test
    void getMenusByParentId_shouldFilterPlatformOnlyChildrenForNonPlatformTenant() {
        TenantContext.setActiveTenantId(2L);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "tenant-admin",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
            )
        );

        when(resourceRepository.findByTypeInAndParentIdAndTenantIdOrderBySortAsc(anyList(), eq(1L), eq(2L)))
            .thenReturn(List.of(
                resource(2L, 2L, "user", 1L, "/system/user", "/api/users", "system:user:list", ResourceType.MENU),
                resource(3L, 2L, "tenant", 1L, "/system/tenant", "/sys/tenants", "system:tenant:list", ResourceType.MENU),
                resource(4L, 2L, "idempotentOps", 1L, "/ops/idempotent", "/metrics/idempotent", "idempotent:ops:view", ResourceType.MENU)
            ));
        when(resourceRepository.existsByParentIdAndTenantId(2L, 2L)).thenReturn(false);

        List<ResourceResponseDto> children = service.getMenusByParentId(1L);

        assertThat(children).extracting(ResourceResponseDto::getName).containsExactly("user");
    }

    @Test
    void menuTree_shouldFilterRegularMenusByCurrentUserAuthorities() {
        TenantContext.setActiveTenantId(2L);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "tenant-user",
                "n/a",
                List.of(
                    new SimpleGrantedAuthority("ROLE_USER"),
                    new SimpleGrantedAuthority("system:user:list")
                )
            )
        );

        when(resourceRepository.findByTypeAndTenantIdOrderBySortAsc(ResourceType.DIRECTORY, 2L))
            .thenReturn(List.of(
                resource(1L, 2L, "system", null, "/system", "", "system", ResourceType.DIRECTORY)
            ));
        when(resourceRepository.findGrantedResourcesByUsernameAndTenantId("tenant-user", 2L, List.of(ResourceType.MENU.getCode())))
            .thenReturn(List.of(
                resource(2L, 2L, "user", 1L, "/system/user", "/api/users", "system:user:list", ResourceType.MENU)
            ));
        when(resourceRepository.existsByParentIdAndTenantId(1L, 2L)).thenReturn(true);
        when(resourceRepository.existsByParentIdAndTenantId(2L, 2L)).thenReturn(false);

        List<ResourceResponseDto> tree = service.menuTree();

        assertThat(tree).singleElement().extracting(ResourceResponseDto::getName).isEqualTo("system");
        assertThat(tree.get(0).getChildren())
            .extracting(ResourceResponseDto::getName)
            .containsExactly("user");
        verify(resourceRepository).findGrantedResourcesByUsernameAndTenantId("tenant-user", 2L, List.of(ResourceType.MENU.getCode()));
        verify(resourceRepository, never()).findByTypeInAndTenantIdOrderBySortAsc(
            eq(List.of(ResourceType.DIRECTORY, ResourceType.MENU)),
            eq(2L)
        );
    }

    @Test
    void getMenusByParentId_shouldLoadGrantedMenusForCurrentUser() {
        TenantContext.setActiveTenantId(2L);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "tenant-user",
                "n/a",
                List.of(
                    new SimpleGrantedAuthority("ROLE_USER"),
                    new SimpleGrantedAuthority("system:user:list")
                )
            )
        );

        when(resourceRepository.findByTypeInAndParentIdAndTenantIdOrderBySortAsc(
                List.of(ResourceType.DIRECTORY),
                1L,
                2L
            ))
            .thenReturn(List.of());
        when(resourceRepository.findGrantedResourcesByUsernameAndTenantIdAndParentId(
                "tenant-user",
                2L,
                List.of(ResourceType.MENU.getCode()),
                1L
            ))
            .thenReturn(List.of(
                resource(2L, 2L, "user", 1L, "/system/user", "/api/users", "system:user:list", ResourceType.MENU)
            ));
        when(resourceRepository.existsByParentIdAndTenantId(2L, 2L)).thenReturn(false);

        List<ResourceResponseDto> children = service.getMenusByParentId(1L);

        assertThat(children).extracting(ResourceResponseDto::getName).containsExactly("user");
        verify(resourceRepository).findGrantedResourcesByUsernameAndTenantIdAndParentId(
            "tenant-user",
            2L,
            List.of(ResourceType.MENU.getCode()),
            1L
        );
        verify(resourceRepository, never()).findByTypeInAndParentIdAndTenantIdOrderBySortAsc(
            eq(List.of(ResourceType.DIRECTORY, ResourceType.MENU)),
            eq(1L),
            eq(2L)
        );
    }

    private Resource resource(
        Long id,
        Long tenantId,
        String name,
        Long parentId,
        String url,
        String uri,
        String permission,
        ResourceType type
    ) {
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
        resource.setShowIcon(false);
        resource.setKeepAlive(false);
        resource.setSort(1);
        return resource;
    }
}
