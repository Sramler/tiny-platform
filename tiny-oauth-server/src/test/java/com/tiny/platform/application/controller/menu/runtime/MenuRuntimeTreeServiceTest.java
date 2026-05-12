package com.tiny.platform.application.controller.menu.runtime;

import com.tiny.platform.core.oauth.security.PermissionVersionService;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import com.tiny.platform.infrastructure.menu.service.MenuService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class MenuRuntimeTreeServiceTest {

    @Test
    void shouldReturn304WhenClientEtagMatchesCurrentRuntimeVersion() {
        MenuService menuService = mock(MenuService.class);
        MenuConfigVersionProvider versionProvider = mock(MenuConfigVersionProvider.class);
        PermissionVersionService permissionVersionService = mock(PermissionVersionService.class);
        MenuRuntimeTreeService service = new MenuRuntimeTreeService(
            menuService,
            versionProvider,
            new InMemoryMenuRuntimeTreeCacheStore(),
            permissionVersionService
        );
        when(versionProvider.resolveMenuConfigVersion(null, "TENANT", null)).thenReturn("menu-v1");

        MenuRuntimeTreeSnapshot first = service.loadRuntimeTree(null, null);
        MenuRuntimeTreeSnapshot second = service.loadRuntimeTree(null, first.etag());

        assertThat(second.notModified()).isTrue();
        assertThat(second.etag()).isEqualTo(first.etag());
        verify(menuService, times(1)).menuTree();
    }

    @Test
    void shouldReuseServerSideCacheWhenVersionedKeyIsStable() {
        MenuService menuService = mock(MenuService.class);
        MenuConfigVersionProvider versionProvider = mock(MenuConfigVersionProvider.class);
        PermissionVersionService permissionVersionService = mock(PermissionVersionService.class);
        MenuRuntimeTreeService service = new MenuRuntimeTreeService(
            menuService,
            versionProvider,
            new InMemoryMenuRuntimeTreeCacheStore(),
            permissionVersionService
        );
        ResourceResponseDto menu = new ResourceResponseDto();
        menu.setName("system");
        when(versionProvider.resolveMenuConfigVersion(null, "TENANT", null)).thenReturn("menu-v1");
        when(menuService.menuTree()).thenReturn(List.of(menu));

        MenuRuntimeTreeSnapshot first = service.loadRuntimeTree(null, null);
        MenuRuntimeTreeSnapshot second = service.loadRuntimeTree(null, null);

        assertThat(first.cacheHit()).isFalse();
        assertThat(second.cacheHit()).isTrue();
        assertThat(second.menus()).extracting(ResourceResponseDto::getName).containsExactly("system");
        verify(menuService, times(1)).menuTree();
    }
}
