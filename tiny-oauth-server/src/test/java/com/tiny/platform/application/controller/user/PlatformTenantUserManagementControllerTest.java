package com.tiny.platform.application.controller.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tiny.platform.core.oauth.tenant.TenantLifecycleAccessGuard;
import com.tiny.platform.infrastructure.auth.user.dto.UserRequestDto;
import com.tiny.platform.infrastructure.auth.user.dto.UserResponseDto;
import com.tiny.platform.infrastructure.auth.user.service.PlatformTenantUserManagementService;
import com.tiny.platform.infrastructure.core.dto.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class PlatformTenantUserManagementControllerTest {

    private PlatformTenantUserManagementService platformTenantUserManagementService;
    private TenantLifecycleAccessGuard tenantLifecycleAccessGuard;
    private PlatformTenantUserManagementController controller;

    @BeforeEach
    void setUp() {
        platformTenantUserManagementService = mock(PlatformTenantUserManagementService.class);
        tenantLifecycleAccessGuard = mock(TenantLifecycleAccessGuard.class);
        controller = new PlatformTenantUserManagementController(
            platformTenantUserManagementService,
            tenantLifecycleAccessGuard
        );
    }

    @Test
    void list_shouldDelegateToServiceAndLifecycleGuard() {
        UserRequestDto query = new UserRequestDto();
        query.setUsername("alice");
        PageRequest pageable = PageRequest.of(0, 10);
        UserResponseDto dto = new UserResponseDto(
            9L,
            "alice",
            "Alice",
            true,
            true,
            true,
            true,
            LocalDateTime.now(),
            0,
            null,
            false,
            null
        );
        when(platformTenantUserManagementService.list(7L, query, pageable))
            .thenReturn(new PageImpl<>(List.of(dto), pageable, 1));

        ResponseEntity<PageResponse<UserResponseDto>> response = controller.list(7L, query, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).containsExactly(dto);
        verify(tenantLifecycleAccessGuard).assertPlatformTargetTenantReadable(7L, "system:user:list");
        verify(platformTenantUserManagementService).list(7L, query, pageable);
    }

    @Test
    void get_shouldReturnNotFoundWhenUserMissing() {
        when(platformTenantUserManagementService.get(7L, 99L)).thenReturn(Optional.empty());

        ResponseEntity<UserResponseDto> response = controller.get(7L, 99L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(tenantLifecycleAccessGuard).assertPlatformTargetTenantReadable(7L, "system:user:view");
    }

    @Test
    void get_shouldReturnDetailWhenUserExists() {
        UserResponseDto dto = new UserResponseDto(
            9L,
            "alice",
            "Alice",
            true,
            true,
            true,
            true,
            LocalDateTime.now(),
            1,
            LocalDateTime.now(),
            false,
            null
        );
        when(platformTenantUserManagementService.get(7L, 9L)).thenReturn(Optional.of(dto));

        ResponseEntity<UserResponseDto> response = controller.get(7L, 9L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(dto);
        verify(tenantLifecycleAccessGuard).assertPlatformTargetTenantReadable(7L, "system:user:view");
    }
}
