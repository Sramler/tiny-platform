package com.tiny.platform.application.controller.role;

import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.dto.RoleCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleRequestDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleResponseDto;
import com.tiny.platform.infrastructure.auth.role.service.RoleService;
import com.tiny.platform.infrastructure.core.dto.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RoleControllerTest {

    private RoleService roleService;
    private RoleController controller;

    @BeforeEach
    void setUp() {
        roleService = mock(RoleService.class);
        controller = new RoleController(roleService);
    }

    @Test
    void list_shouldDelegateAndWrapPageResponse() {
        RoleRequestDto query = new RoleRequestDto();
        RoleResponseDto dto = new RoleResponseDto(1L, "Admin", "ADMIN", "d", false, true, null, null);
        when(roleService.roles(eq(query), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(dto)));

        ResponseEntity<PageResponse<RoleResponseDto>> resp = controller.list(query, Pageable.unpaged());

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(1, resp.getBody().getContent().size());
        verify(roleService).roles(eq(query), any(Pageable.class));
    }

    @Test
    void get_whenNotFound_shouldReturn404() {
        when(roleService.findById(9L)).thenReturn(Optional.empty());

        ResponseEntity<RoleResponseDto> resp = controller.get(9L);

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        verify(roleService).findById(9L);
    }

    @Test
    void get_whenFound_shouldMapToResponseDto() {
        Role role = mock(Role.class);
        when(role.getId()).thenReturn(2L);
        when(role.getName()).thenReturn("Role");
        when(role.getCode()).thenReturn("ROLE");
        when(role.getDescription()).thenReturn("desc");
        when(role.isBuiltin()).thenReturn(true);
        when(role.isEnabled()).thenReturn(false);
        when(role.getCreatedAt()).thenReturn(null);
        when(role.getUpdatedAt()).thenReturn(null);
        when(roleService.findById(2L)).thenReturn(Optional.of(role));

        ResponseEntity<RoleResponseDto> resp = controller.get(2L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(2L, resp.getBody().getId());
        assertEquals("ROLE", resp.getBody().getCode());
        assertTrue(resp.getBody().isBuiltin());
        assertFalse(resp.getBody().isEnabled());
        verify(roleService).findById(2L);
    }

    @Test
    void create_update_delete_shouldDelegate() {
        RoleCreateUpdateDto dto = new RoleCreateUpdateDto();
        RoleResponseDto created = new RoleResponseDto(1L, "n", "c", "d", false, true, null, null);
        when(roleService.create(dto)).thenReturn(created);

        ResponseEntity<RoleResponseDto> createResp = controller.create(dto);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        verify(roleService).create(dto);

        RoleResponseDto updated = new RoleResponseDto(3L, "n2", "c2", "d2", false, true, null, null);
        when(roleService.update(3L, dto)).thenReturn(updated);
        ResponseEntity<RoleResponseDto> updateResp = controller.update(3L, dto);
        assertEquals(HttpStatus.OK, updateResp.getStatusCode());
        verify(roleService).update(3L, dto);

        ResponseEntity<Void> deleteResp = controller.delete(4L);
        assertEquals(HttpStatus.NO_CONTENT, deleteResp.getStatusCode());
        verify(roleService).delete(4L);
    }

    @Test
    void getAllRoles_shouldQueryUnpaged() {
        RoleResponseDto dto = new RoleResponseDto(1L, "n", "c", "d", false, true, null, null);
        when(roleService.roles(any(RoleRequestDto.class), eq(Pageable.unpaged())))
            .thenReturn(new PageImpl<>(List.of(dto)));

        ResponseEntity<List<RoleResponseDto>> resp = controller.getAllRoles();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(1, resp.getBody().size());
        verify(roleService).roles(any(RoleRequestDto.class), eq(Pageable.unpaged()));
    }

    @Test
    void roleUsers_and_resources_shouldDelegate() {
        when(roleService.getUserIdsByRoleId(10L)).thenReturn(List.of(1L, 2L));
        ResponseEntity<List<Long>> users = controller.getRoleUsers(10L);
        assertEquals(List.of(1L, 2L), users.getBody());

        controller.updateRoleUsers(10L, List.of(3L));
        verify(roleService).updateRoleUsers(10L, List.of(3L));

        when(roleService.getResourceIdsByRoleId(10L)).thenReturn(List.of(7L));
        ResponseEntity<List<Long>> resources = controller.getRoleResources(10L);
        assertEquals(List.of(7L), resources.getBody());

        controller.updateRoleResources(10L, List.of(8L, 9L));
        verify(roleService).updateRoleResources(10L, List.of(8L, 9L));
    }
}

