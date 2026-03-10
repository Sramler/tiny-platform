package com.tiny.platform.application.controller.menu;

import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceRequestDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import com.tiny.platform.infrastructure.menu.service.MenuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MenuControllerTest {

    private MenuService menuService;
    private MenuController controller;

    @BeforeEach
    void setUp() {
        menuService = mock(MenuService.class);
        controller = new MenuController(menuService);
    }

    @Test
    void getMenus_shouldDelegate() {
        ResourceRequestDto query = new ResourceRequestDto();
        when(menuService.list(eq(query))).thenReturn(List.of());

        ResponseEntity<List<ResourceResponseDto>> resp = controller.getMenus(query);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(menuService).list(eq(query));
    }

    @Test
    void treeEndpoints_shouldDelegate() {
        when(menuService.menuTree()).thenReturn(List.of());
        when(menuService.menuTreeAll()).thenReturn(List.of());

        assertEquals(HttpStatus.OK, controller.getMenuTree().getStatusCode());
        assertEquals(HttpStatus.OK, controller.getFullMenuTree().getStatusCode());
        verify(menuService).menuTree();
        verify(menuService).menuTreeAll();
    }

    @Test
    void getMenusByParentId_shouldDelegate() {
        when(menuService.getMenusByParentId(1L)).thenReturn(List.of());

        ResponseEntity<List<ResourceResponseDto>> resp = controller.getMenusByParentId(1L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(menuService).getMenusByParentId(1L);
    }

    @Test
    void create_update_delete_shouldDelegate() {
        ResourceCreateUpdateDto dto = new ResourceCreateUpdateDto();
        Resource created = mock(Resource.class);
        when(menuService.createMenu(dto)).thenReturn(created);

        ResponseEntity<Resource> createResp = controller.createMenu(dto);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        verify(menuService).createMenu(dto);

        ResourceCreateUpdateDto updateDto = new ResourceCreateUpdateDto();
        when(menuService.updateMenu(updateDto)).thenReturn(mock(Resource.class));
        ResponseEntity<?> updateResp = controller.updateMenu(9L, updateDto);
        assertEquals(HttpStatus.NO_CONTENT, updateResp.getStatusCode());
        assertEquals(9L, updateDto.getId());
        verify(menuService).updateMenu(updateDto);

        ResponseEntity<Void> deleteResp = controller.deleteMenu(10L);
        assertEquals(HttpStatus.NO_CONTENT, deleteResp.getStatusCode());
        verify(menuService).deleteMenu(10L);
    }

    @Test
    void batchDelete_shouldDelegateAndReturnSuccessMap() {
        ResponseEntity<Map<String, Object>> resp = controller.batchDeleteMenus(List.of(1L, 2L));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(Boolean.TRUE, resp.getBody().get("success"));
        verify(menuService).batchDeleteMenus(List.of(1L, 2L));
    }
}

