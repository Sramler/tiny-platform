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
import com.tiny.platform.infrastructure.idempotent.console.IdempotentConsoleService;
import com.tiny.platform.infrastructure.idempotent.console.dto.IdempotentBlacklistDto;
import com.tiny.platform.infrastructure.idempotent.console.dto.IdempotentRecordDto;
import com.tiny.platform.infrastructure.idempotent.console.dto.IdempotentRuleDto;
import com.tiny.platform.infrastructure.idempotent.metrics.IdempotentMetricsService;
import com.tiny.platform.infrastructure.menu.service.MenuService;
import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.dto.TenantCreateUpdateDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantRequestDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantResponseDto;
import com.tiny.platform.infrastructure.tenant.service.TenantService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    void idempotent_metrics_controller_should_cover_endpoints() {
        IdempotentMetricsController metricsController =
            new IdempotentMetricsController(new IdempotentMetricsService(new SimpleMeterRegistry()));

        assertThat(metricsController.getMetrics(null).getBody())
            .containsEntry("success", true)
            .containsEntry("message", "OK")
            .containsEntry("hitCount", 0L)
            .containsEntry("passCount", 0L)
            .containsEntry("rejectCount", 0L)
            .containsEntry("conflictRate", 0.0);
        assertThat(metricsController.getTopKeys(15, null).getBody())
            .containsEntry("message", "OK")
            .containsEntry("limit", 15)
            .containsEntry("topKeys", List.of());
        assertThat(metricsController.getMqMetrics(null).getBody())
            .containsEntry("message", "OK")
            .containsEntry("successCount", 0L)
            .containsEntry("failureCount", 0L)
            .containsEntry("duplicateRate", 0.0);
    }

    @Test
    void idempotent_console_controller_should_cover_endpoints() {
        IdempotentConsoleService consoleService = mock(IdempotentConsoleService.class);
        IdempotentConsoleController controller = new IdempotentConsoleController(consoleService);

        IdempotentRuleDto ruleDto = new IdempotentRuleDto();
        ruleDto.setId(1L);
        ruleDto.setScope("http:POST:/api/orders");
        ruleDto.setEnabled(true);
        PageResponse<IdempotentRuleDto> rulePage = new PageResponse<>(new PageImpl<>(List.of(ruleDto), PageRequest.of(0, 10), 1));
        PageResponse<IdempotentRecordDto> recordPage = new PageResponse<>(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        PageResponse<IdempotentBlacklistDto> blacklistPage = new PageResponse<>(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        when(consoleService.getRules(null, null, 0, 10)).thenReturn(rulePage);
        when(consoleService.createRule(any())).thenReturn(ruleDto);
        when(consoleService.updateRule(eq(5L), any())).thenReturn(ruleDto);
        when(consoleService.getRecords(null, null, 0, 10)).thenReturn(recordPage);
        when(consoleService.getRecords("key1", "FAILED", 1, 10)).thenReturn(recordPage);
        when(consoleService.getMetricsMap(null, null, null)).thenReturn(Map.of("success", true, "message", "OK", "passCount", 0L));
        when(consoleService.getBlacklist(0, 10)).thenReturn(blacklistPage);

        assertThat(controller.getRules(null, null, 0, 10).getBody()).isNotNull();
        assertThat(controller.getRules(null, null, 0, 10).getBody().getContent()).hasSize(1);

        IdempotentRuleDto createDto = new IdempotentRuleDto();
        createDto.setScope("http:POST:/api/orders");
        createDto.setEnabled(true);
        assertThat(controller.createRule(createDto).getBody().getId()).isEqualTo(1L);

        IdempotentRuleDto updateDto = new IdempotentRuleDto();
        updateDto.setScope("http:POST:/api/orders");
        updateDto.setEnabled(false);
        assertThat(controller.updateRule(5L, updateDto).getBody().getId()).isEqualTo(1L);

        controller.deleteRule(1L);
        verify(consoleService).deleteRule(1L);

        controller.enableRules(Map.of("ids", List.of(1L, 2L)));
        verify(consoleService).enableRules(List.of(1L, 2L));

        controller.disableRules(Map.of("ids", List.of(1L)));
        verify(consoleService).disableRules(List.of(1L));

        assertThat(controller.getRecords(null, null, 0, 10).getBody()).isNotNull();
        assertThat(controller.getRecords("key1", "FAILED", 1, 10).getBody()).isNotNull();

        controller.retryRecord(Map.of("key", "http:order:123"));
        verify(consoleService).retryRecord("http:order:123");

        assertThat(controller.getMetrics(null, null, null).getBody()).containsEntry("success", true);
        assertThat(controller.getBlacklist(0, 10).getBody()).isNotNull();

        IdempotentBlacklistDto blacklistDto = new IdempotentBlacklistDto();
        blacklistDto.setId(1L);
        blacklistDto.setKeyPattern("http:malicious:*");
        blacklistDto.setReason("恶意 key");
        when(consoleService.addBlacklist(any())).thenReturn(blacklistDto);
        assertThat(controller.addBlacklist(blacklistDto).getBody().getId()).isEqualTo(1L);

        controller.deleteBlacklist(1L);
        verify(consoleService).deleteBlacklist(1L);
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
