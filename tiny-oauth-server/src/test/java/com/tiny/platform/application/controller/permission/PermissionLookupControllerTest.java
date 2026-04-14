package com.tiny.platform.application.controller.permission;

import com.tiny.platform.infrastructure.auth.permission.service.PermissionLookupService;
import com.tiny.platform.infrastructure.auth.resource.dto.PermissionOptionDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionLookupControllerTest {

    @Test
    void should_cover_permission_lookup_endpoint() {
        PermissionLookupService permissionLookupService = mock(PermissionLookupService.class);
        PermissionLookupController controller = new PermissionLookupController(permissionLookupService);
        PermissionOptionDto option = new PermissionOptionDto(7001L, "system:menu:list", "菜单读取");
        when(permissionLookupService.findPermissionOptions("menu", 10)).thenReturn(List.of(option));
        when(permissionLookupService.findPermissionOptions(null, 50)).thenReturn(List.of(option));

        assertThat(controller.getPermissionOptions("menu", 10).getBody()).containsExactly(option);
        assertThat(controller.getPermissionOptions(null, null).getBody()).containsExactly(option);
        verify(permissionLookupService).findPermissionOptions("menu", 10);
        verify(permissionLookupService).findPermissionOptions(null, 50);
    }
}

