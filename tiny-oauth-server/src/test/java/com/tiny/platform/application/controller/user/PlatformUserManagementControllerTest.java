package com.tiny.platform.application.controller.user;

import com.tiny.platform.infrastructure.auth.user.dto.PlatformUserManagementDtos.PlatformUserCreateDto;
import com.tiny.platform.infrastructure.auth.user.dto.PlatformUserManagementDtos.PlatformUserDetailDto;
import com.tiny.platform.infrastructure.auth.user.dto.PlatformUserManagementDtos.PlatformUserListItemDto;
import com.tiny.platform.infrastructure.auth.user.dto.PlatformUserManagementDtos.PlatformUserRoleDto;
import com.tiny.platform.infrastructure.auth.user.service.PlatformUserManagementService;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlatformUserManagementControllerTest {

    private PlatformUserManagementService platformUserManagementService;
    private PlatformUserManagementController controller;

    @BeforeEach
    void setUp() {
        platformUserManagementService = mock(PlatformUserManagementService.class);
        controller = new PlatformUserManagementController(platformUserManagementService);
    }

    @Test
    void list_shouldDelegateToService() {
        when(platformUserManagementService.list("platform", true, "ACTIVE", PageRequest.of(0, 10)))
            .thenReturn(new PageImpl<>(
                List.of(new PlatformUserListItemDto(
                    9L, "platform_admin", "平台管理员", "平台管理员", true, "ACTIVE", true, LocalDateTime.now()
                )),
                PageRequest.of(0, 10),
                1
            ));

        ResponseEntity<?> response = controller.list("platform", true, "ACTIVE", PageRequest.of(0, 10));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(platformUserManagementService).list("platform", true, "ACTIVE", PageRequest.of(0, 10));
    }

    @Test
    void get_shouldReturnNotFoundWhenMissing() {
        when(platformUserManagementService.get(88L)).thenReturn(Optional.empty());

        ResponseEntity<PlatformUserDetailDto> response = controller.get(88L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void create_shouldRejectMissingUserId() {
        assertThatThrownBy(() -> controller.create(new PlatformUserManagementController.PlatformUserCreateRequest(null, "平台管理员", "ACTIVE")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("userId");
        verifyNoInteractions(platformUserManagementService);
    }

    @Test
    void create_shouldDelegateToService() {
        PlatformUserDetailDto detail = new PlatformUserDetailDto(
            9L,
            "platform_admin",
            "平台管理员",
            "平台管理员",
            "platform@example.com",
            "13800000000",
            true,
            true,
            true,
            true,
            "ACTIVE",
            true,
            LocalDateTime.now(),
            LocalDateTime.now(),
            LocalDateTime.now(),
            List.of()
        );
        when(platformUserManagementService.create(any(PlatformUserCreateDto.class))).thenReturn(detail);

        ResponseEntity<PlatformUserDetailDto> response = controller.create(
            new PlatformUserManagementController.PlatformUserCreateRequest(9L, "平台管理员", "ACTIVE")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(detail);
        verify(platformUserManagementService).create(new PlatformUserCreateDto(9L, "平台管理员", "ACTIVE"));
    }

    @Test
    void updateStatus_shouldRejectMissingStatus() {
        assertThatThrownBy(() -> controller.updateStatus(9L, new PlatformUserManagementController.PlatformUserStatusUpdateRequest(" ")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("status");
        verifyNoInteractions(platformUserManagementService);
    }

    @Test
    void updateStatus_shouldReturnNotFoundWhenServiceReturnsFalse() {
        when(platformUserManagementService.updateStatus(9L, "DISABLED")).thenReturn(false);

        ResponseEntity<Void> response = controller.updateStatus(
            9L,
            new PlatformUserManagementController.PlatformUserStatusUpdateRequest("DISABLED")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getRoles_shouldDelegateToService() {
        when(platformUserManagementService.getRoles(9L)).thenReturn(List.of(
            new PlatformUserRoleDto(1L, "ROLE_PLATFORM_ADMIN", "平台管理员", "desc", true, false)
        ));

        ResponseEntity<List<PlatformUserRoleDto>> response = controller.getRoles(9L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(platformUserManagementService).getRoles(9L);
    }

    @Test
    void replaceRoles_shouldRejectMissingRoleIds() {
        assertThatThrownBy(() -> controller.replaceRoles(9L, new PlatformUserManagementController.PlatformUserRolesReplaceRequest(null)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("roleIds");
        verifyNoInteractions(platformUserManagementService);
    }

    @Test
    void replaceRoles_shouldDelegateToService() {
        when(platformUserManagementService.replaceRoles(9L, List.of(1L, 2L))).thenReturn(List.of(
            new PlatformUserRoleDto(1L, "ROLE_PLATFORM_ADMIN", "平台管理员", "desc", true, false),
            new PlatformUserRoleDto(2L, "ROLE_PLATFORM_AUDITOR", "平台审计员", "desc2", true, false)
        ));

        ResponseEntity<List<PlatformUserRoleDto>> response = controller.replaceRoles(
            9L,
            new PlatformUserManagementController.PlatformUserRolesReplaceRequest(List.of(1L, 2L))
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        verify(platformUserManagementService).replaceRoles(9L, List.of(1L, 2L));
    }
}
