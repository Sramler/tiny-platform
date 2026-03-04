package com.tiny.platform.application.controller;

import com.tiny.platform.application.controller.idempotent.controller.IdempotentConsoleController;
import com.tiny.platform.application.controller.idempotent.controller.IdempotentMetricsController;
import com.tiny.platform.application.controller.menu.MenuController;
import com.tiny.platform.application.controller.role.RoleController;
import com.tiny.platform.application.controller.tenant.TenantController;
import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceRequestDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.dto.RoleCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleRequestDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleResponseDto;
import com.tiny.platform.infrastructure.auth.role.service.RoleService;
import com.tiny.platform.infrastructure.core.dto.PageResponse;
import com.tiny.platform.infrastructure.menu.service.MenuService;
import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.dto.TenantCreateUpdateDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantRequestDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantResponseDto;
import com.tiny.platform.infrastructure.tenant.service.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationControllerCoverageTest {

    @Test
    void menuController_should_cover_all_endpoints() {
        MenuService menuService = mock(MenuService.class);
        MenuController controller = new MenuController(menuService);
        ResourceRequestDto query = new ResourceRequestDto();
        ResourceCreateUpdateDto createDto = new ResourceCreateUpdateDto();
        createDto.setName("menu");
        createDto.setTitle("Menu");
        createDto.setType(1);
        Pageable pageable = PageRequest.of(0, 10);

        ResourceResponseDto responseDto = resourceResponse(1L, "menu-1");
        Resource resource = resource(1L, "menu-1");
        List<ResourceResponseDto> menuList = List.of(responseDto);

        when(menuService.list(query)).thenReturn(menuList);
        when(menuService.menuTree()).thenReturn(menuList);
        when(menuService.menuTreeAll()).thenReturn(menuList);
        when(menuService.getMenusByParentId(9L)).thenReturn(menuList);
        when(menuService.createMenu(createDto)).thenReturn(resource);
        when(menuService.updateMenu(any(ResourceCreateUpdateDto.class))).thenReturn(resource);

        assertThat(controller.getMenus(query).getBody()).containsExactly(responseDto);
        assertThat(controller.getMenuTree().getBody()).containsExactly(responseDto);
        assertThat(controller.getFullMenuTree().getBody()).containsExactly(responseDto);
        assertThat(controller.getMenusByParentId(9L).getBody()).containsExactly(responseDto);
        assertThat(controller.createMenu(createDto).getBody()).isEqualTo(resource);

        ResponseEntity<?> updateResponse = controller.updateMenu(7L, createDto);
        assertThat(updateResponse.getStatusCode().value()).isEqualTo(204);
        assertThat(createDto.getId()).isEqualTo(7L);
        verify(menuService).updateMenu(createDto);

        ResponseEntity<Void> deleteResponse = controller.deleteMenu(7L);
        assertThat(deleteResponse.getStatusCode().value()).isEqualTo(204);
        verify(menuService).deleteMenu(7L);

        ResponseEntity<Map<String, Object>> batchDelete = controller.batchDeleteMenus(List.of(1L, 2L));
        assertThat(batchDelete.getBody()).containsEntry("success", true).containsEntry("message", "批量删除成功");
        verify(menuService).batchDeleteMenus(List.of(1L, 2L));

        assertThat(pageable).isNotNull();
    }

    @Test
    void roleController_should_cover_crud_assignment_and_not_found() {
        RoleService roleService = mock(RoleService.class);
        RoleController controller = new RoleController(roleService);
        RoleRequestDto query = new RoleRequestDto();
        Pageable pageable = PageRequest.of(0, 10);
        RoleCreateUpdateDto dto = new RoleCreateUpdateDto();
        RoleResponseDto responseDto = new RoleResponseDto(1L, "Admin", "ROLE_ADMIN", "desc", true, true,
            LocalDateTime.of(2026, 3, 1, 10, 0), LocalDateTime.of(2026, 3, 1, 11, 0));
        PageImpl<RoleResponseDto> page = new PageImpl<>(List.of(responseDto), pageable, 1);
        Role role = new Role();
        role.setId(2L);
        role.setName("User");
        role.setCode("ROLE_USER");
        role.setDescription("user");
        role.setBuiltin(false);
        role.setEnabled(true);
        role.setCreatedAt(LocalDateTime.of(2026, 3, 2, 9, 0));
        role.setUpdatedAt(LocalDateTime.of(2026, 3, 2, 10, 0));

        when(roleService.roles(query, pageable)).thenReturn(page);
        when(roleService.findById(2L)).thenReturn(Optional.of(role));
        when(roleService.findById(99L)).thenReturn(Optional.empty());
        when(roleService.create(dto)).thenReturn(responseDto);
        when(roleService.update(3L, dto)).thenReturn(responseDto);
        when(roleService.getUserIdsByRoleId(1L)).thenReturn(List.of(10L, 11L));
        when(roleService.getResourceIdsByRoleId(1L)).thenReturn(List.of(20L, 21L));
        when(roleService.roles(any(RoleRequestDto.class), eq(Pageable.unpaged()))).thenReturn(page);

        PageResponse<RoleResponseDto> listBody = controller.list(query, pageable).getBody();
        assertThat(listBody).isNotNull();
        assertThat(listBody.getContent()).containsExactly(responseDto);
        assertThat(listBody.getTotalElements()).isEqualTo(1);

        RoleResponseDto getBody = controller.get(2L).getBody();
        assertThat(getBody).isNotNull();
        assertThat(getBody.getId()).isEqualTo(2L);
        assertThat(getBody.getName()).isEqualTo("User");
        assertThat(controller.get(99L).getStatusCode().value()).isEqualTo(404);

        assertThat(controller.create(dto).getBody()).isEqualTo(responseDto);
        assertThat(controller.update(3L, dto).getBody()).isEqualTo(responseDto);

        assertThat(controller.delete(4L).getStatusCode().value()).isEqualTo(204);
        verify(roleService).delete(4L);

        assertThat(controller.getAllRoles().getBody()).containsExactly(responseDto);

        assertThat(controller.getRoleUsers(1L).getBody()).containsExactly(10L, 11L);
        assertThat(controller.updateRoleUsers(1L, List.of(10L)).getStatusCode().value()).isEqualTo(200);
        verify(roleService).updateRoleUsers(1L, List.of(10L));

        assertThat(controller.getRoleResources(1L).getBody()).containsExactly(20L, 21L);
        assertThat(controller.updateRoleResources(1L, List.of(20L)).getStatusCode().value()).isEqualTo(200);
        verify(roleService).updateRoleResources(1L, List.of(20L));
    }

    @Test
    void tenantController_should_cover_crud_and_optional_mapping() {
        TenantService tenantService = mock(TenantService.class);
        TenantController controller = new TenantController(tenantService);
        TenantRequestDto query = new TenantRequestDto();
        Pageable pageable = PageRequest.of(0, 5);
        TenantCreateUpdateDto dto = new TenantCreateUpdateDto();

        TenantResponseDto tenantResponse = new TenantResponseDto();
        tenantResponse.setId(1L);
        tenantResponse.setCode("t1");
        tenantResponse.setName("Tenant 1");
        tenantResponse.setEnabled(true);

        Tenant tenant = new Tenant();
        tenant.setId(2L);
        tenant.setCode("t2");
        tenant.setName("Tenant 2");
        tenant.setDomain("t2.example.com");
        tenant.setEnabled(false);
        tenant.setPlanCode("pro");
        tenant.setExpiresAt(LocalDateTime.of(2026, 5, 1, 12, 0));
        tenant.setMaxUsers(100);
        tenant.setMaxStorageGb(50);
        tenant.setContactName("Alice");
        tenant.setContactEmail("alice@example.com");
        tenant.setContactPhone("123");
        tenant.setRemark("remark");
        tenant.setCreatedAt(LocalDateTime.of(2026, 1, 1, 8, 0));
        tenant.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 8, 0));

        when(tenantService.list(query, pageable)).thenReturn(new PageImpl<>(List.of(tenantResponse), pageable, 1));
        when(tenantService.findById(2L)).thenReturn(Optional.of(tenant));
        when(tenantService.findById(99L)).thenReturn(Optional.empty());
        when(tenantService.create(dto)).thenReturn(tenantResponse);
        when(tenantService.update(2L, dto)).thenReturn(tenantResponse);

        PageResponse<TenantResponseDto> listBody = controller.list(query, pageable).getBody();
        assertThat(listBody).isNotNull();
        assertThat(listBody.getContent()).containsExactly(tenantResponse);

        TenantResponseDto getBody = controller.get(2L).getBody();
        assertThat(getBody).isNotNull();
        assertThat(getBody.getId()).isEqualTo(2L);
        assertThat(getBody.getCode()).isEqualTo("t2");
        assertThat(getBody.getExpiresAt()).isEqualTo("2026-05-01T12:00");
        assertThat(getBody.getContactEmail()).isEqualTo("alice@example.com");
        assertThat(controller.get(99L).getStatusCode().value()).isEqualTo(404);

        assertThat(controller.create(dto).getBody()).isEqualTo(tenantResponse);
        assertThat(controller.update(2L, dto).getBody()).isEqualTo(tenantResponse);

        assertThat(controller.delete(3L).getStatusCode().value()).isEqualTo(204);
        verify(tenantService).delete(3L);
    }

    @Test
    void idempotent_controllers_should_cover_stub_endpoints() {
        IdempotentConsoleController consoleController = new IdempotentConsoleController();
        IdempotentMetricsController metricsController = new IdempotentMetricsController();

        assertThat(consoleController.getRules("sceneA", "bizA", 2, 20).getBody())
            .containsEntry("success", true)
            .containsEntry("message", "规则查询功能待实现")
            .containsEntry("page", 2)
            .containsEntry("size", 20);
        assertThat(consoleController.createRule(Map.of("k", "v")).getBody()).containsEntry("message", "规则创建功能待实现");
        assertThat(consoleController.updateRule(5L, Map.of("k", "v")).getBody())
            .containsEntry("message", "规则更新功能待实现")
            .containsEntry("id", 5L);
        assertThat(consoleController.deleteRule(6L).getBody())
            .containsEntry("message", "规则删除功能待实现")
            .containsEntry("id", 6L);
        assertThat(consoleController.enableRules(Map.of("ids", List.of(1))).getBody()).containsEntry("message", "批量启用功能待实现");
        assertThat(consoleController.disableRules(Map.of("ids", List.of(1))).getBody()).containsEntry("message", "批量禁用功能待实现");
        assertThat(consoleController.getRecords("key1", "SUCCESS", 1, 10).getBody())
            .containsEntry("message", "记录查询功能待实现")
            .containsEntry("page", 1)
            .containsEntry("size", 10);
        assertThat(consoleController.retryRecord(Map.of("id", 1)).getBody()).containsEntry("message", "重试功能待实现");
        assertThat(consoleController.getMetrics("2026-03-03", "sceneA").getBody()).containsEntry("message", "统计指标查询功能待实现");
        assertThat(consoleController.getBlacklist(3, 30).getBody())
            .containsEntry("success", true)
            .containsEntry("message", "黑名单查询功能待实现");
        assertThat(consoleController.addBlacklist(Map.of("key", "v")).getBody()).containsEntry("message", "黑名单添加功能待实现");
        assertThat(consoleController.deleteBlacklist(8L).getBody())
            .containsEntry("message", "黑名单删除功能待实现")
            .containsEntry("id", 8L);

        assertThat(metricsController.getMetrics().getBody())
            .containsEntry("success", true)
            .containsEntry("message", "统计指标功能待实现")
            .containsEntry("hitCount", 0)
            .containsEntry("passCount", 0)
            .containsEntry("rejectCount", 0)
            .containsEntry("conflictRate", 0.0);
        assertThat(metricsController.getTopKeys(15).getBody())
            .containsEntry("message", "热点 Key 统计功能待实现")
            .containsEntry("limit", 15);
        assertThat(metricsController.getMqMetrics().getBody())
            .containsEntry("message", "MQ 统计功能待实现")
            .containsEntry("successCount", 0)
            .containsEntry("failureCount", 0)
            .containsEntry("duplicateRate", 0.0);
    }

    private static ResourceResponseDto resourceResponse(Long id, String name) {
        ResourceResponseDto dto = new ResourceResponseDto();
        dto.setId(id);
        dto.setName(name);
        dto.setTitle(name);
        return dto;
    }

    private static Resource resource(Long id, String name) {
        Resource resource = new Resource();
        resource.setId(id);
        resource.setName(name);
        resource.setTitle(name);
        return resource;
    }
}
