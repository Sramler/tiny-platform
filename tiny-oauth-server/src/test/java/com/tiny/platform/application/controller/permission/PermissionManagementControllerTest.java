package com.tiny.platform.application.controller.permission;

import com.tiny.platform.infrastructure.auth.permission.dto.PermissionManagementDtos.PermissionDetailDto;
import com.tiny.platform.infrastructure.auth.permission.dto.PermissionManagementDtos.PermissionListItemDto;
import com.tiny.platform.infrastructure.auth.permission.dto.PermissionManagementDtos.PermissionRoleBindingDto;
import com.tiny.platform.infrastructure.auth.permission.service.PermissionManagementService;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PermissionManagementControllerTest {

    private PermissionManagementService permissionManagementService;
    private PermissionManagementController controller;

    @BeforeEach
    void setUp() {
        permissionManagementService = mock(PermissionManagementService.class);
        controller = new PermissionManagementController(permissionManagementService);
    }

    @Test
    void list_should_delegate_to_service() {
        when(permissionManagementService.list("role", "system", true)).thenReturn(List.of(
            new PermissionListItemDto(1L, "system:role:list", "角色列表", "system", true, 2, LocalDateTime.now())
        ));

        ResponseEntity<List<PermissionListItemDto>> response = controller.list("role", "system", true);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        verify(permissionManagementService).list("role", "system", true);
    }

    @Test
    void get_should_return_not_found_when_missing() {
        when(permissionManagementService.get(99L)).thenReturn(Optional.empty());

        ResponseEntity<PermissionDetailDto> response = controller.get(99L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void get_should_return_detail_when_exists() {
        when(permissionManagementService.get(2L)).thenReturn(Optional.of(
            new PermissionDetailDto(
                2L,
                "system:role:permission:assign",
                "角色权限分配",
                "system",
                true,
                LocalDateTime.now(),
                List.of(new PermissionRoleBindingDto(10L, "ROLE_ADMIN", "管理员"))
            )
        ));

        ResponseEntity<PermissionDetailDto> response = controller.get(2L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("system:role:permission:assign", response.getBody().permissionCode());
    }

    @Test
    void patch_enabled_should_reject_missing_enabled() {
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> controller.updateEnabled(2L, null)
        );
        assertTrue(exception.getMessage().contains("enabled"));
        verifyNoInteractions(permissionManagementService);
    }

    @Test
    void patch_enabled_should_return_not_found_when_service_returns_false() {
        when(permissionManagementService.updateEnabled(2L, false)).thenReturn(false);

        ResponseEntity<Void> response = controller.updateEnabled(
            2L,
            new PermissionManagementController.PermissionEnabledUpdateRequest(false)
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
