package com.tiny.platform.application.controller.role;

import com.tiny.platform.infrastructure.auth.role.dto.PlatformRoleOptionDto;
import com.tiny.platform.infrastructure.auth.role.service.PlatformRoleLookupService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformRoleLookupControllerTest {

    @Test
    void should_cover_platform_role_lookup_endpoint() {
        PlatformRoleLookupService platformRoleLookupService = mock(PlatformRoleLookupService.class);
        PlatformRoleLookupController controller = new PlatformRoleLookupController(platformRoleLookupService);
        PlatformRoleOptionDto option = new PlatformRoleOptionDto(
            7001L,
            "ROLE_PLATFORM_ADMIN",
            "平台管理员",
            "平台角色候选",
            true,
            true
        );
        when(platformRoleLookupService.findOptions("admin", 20)).thenReturn(List.of(option));
        when(platformRoleLookupService.findOptions(null, 200)).thenReturn(List.of(option));

        assertThat(controller.getOptions("admin", 20).getBody()).containsExactly(option);
        assertThat(controller.getOptions(null, null).getBody()).containsExactly(option);
        verify(platformRoleLookupService).findOptions("admin", 20);
        verify(platformRoleLookupService).findOptions(null, 200);
    }
}
