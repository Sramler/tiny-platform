package com.tiny.platform.application.controller.tenant;

import com.tiny.platform.core.oauth.tenant.TenantLifecycleAccessGuard;
import com.tiny.platform.infrastructure.core.dto.PageResponse;
import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.dto.TenantCreateUpdateDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantInitializationSummaryDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantPrecheckResponseDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantPermissionSummaryDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantRequestDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantResponseDto;
import com.tiny.platform.infrastructure.tenant.service.PlatformTemplateDiffResult;
import com.tiny.platform.infrastructure.tenant.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TenantControllerTest {

    private TenantService tenantService;
    private TenantLifecycleAccessGuard tenantLifecycleAccessGuard;
    private TenantController controller;

    @BeforeEach
    void setUp() {
        tenantService = mock(TenantService.class);
        tenantLifecycleAccessGuard = mock(TenantLifecycleAccessGuard.class);
        controller = new TenantController(tenantService, tenantLifecycleAccessGuard);
    }

    @Test
    void list_shouldDelegateAndWrapPageResponse() {
        TenantRequestDto query = new TenantRequestDto();
        TenantResponseDto dto = new TenantResponseDto();
        dto.setId(1L);
        dto.setCode("t1");

        when(tenantService.list(eq(query), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(dto)));

        ResponseEntity<PageResponse<TenantResponseDto>> resp = controller.list(query, Pageable.unpaged());

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(1, resp.getBody().getContent().size());
        assertEquals(1L, resp.getBody().getContent().get(0).getId());
        verify(tenantService).list(eq(query), any(Pageable.class));
    }

    @Test
    void get_whenNotFound_shouldReturn404() {
        when(tenantService.findById(99L)).thenReturn(Optional.empty());

        ResponseEntity<TenantResponseDto> resp = controller.get(99L);

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        verify(tenantService).findById(99L);
    }

    @Test
    void get_whenFound_shouldMapFields() {
        Tenant tenant = mock(Tenant.class);
        when(tenant.getId()).thenReturn(7L);
        when(tenant.getCode()).thenReturn("code");
        when(tenant.getName()).thenReturn("name");
        when(tenant.getDomain()).thenReturn("example.com");
        when(tenant.isEnabled()).thenReturn(true);
        when(tenant.getLifecycleStatus()).thenReturn("FROZEN");
        when(tenant.getPlanCode()).thenReturn("FREE");
        when(tenant.getExpiresAt()).thenReturn(LocalDateTime.parse("2030-01-01T00:00:00"));
        when(tenant.getMaxUsers()).thenReturn(10);
        when(tenant.getMaxStorageGb()).thenReturn(20);
        when(tenant.getContactName()).thenReturn("c");
        when(tenant.getContactEmail()).thenReturn("c@example.com");
        when(tenant.getContactPhone()).thenReturn("1");
        when(tenant.getRemark()).thenReturn("r");

        when(tenantService.findById(7L)).thenReturn(Optional.of(tenant));

        ResponseEntity<TenantResponseDto> resp = controller.get(7L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(7L, resp.getBody().getId());
        assertEquals("code", resp.getBody().getCode());
        assertEquals("name", resp.getBody().getName());
        assertEquals("example.com", resp.getBody().getDomain());
        assertTrue(resp.getBody().isEnabled());
        assertEquals("FROZEN", resp.getBody().getLifecycleStatus());
        assertEquals("FREE", resp.getBody().getPlanCode());
        assertEquals("2030-01-01T00:00", resp.getBody().getExpiresAt());
        verify(tenantLifecycleAccessGuard).assertPlatformTargetTenantReadable(7L, "system:tenant:view");
        verify(tenantService).findById(7L);
    }

    @Test
    void create_shouldDelegate() {
        TenantCreateUpdateDto dto = new TenantCreateUpdateDto();
        TenantResponseDto created = new TenantResponseDto();
        created.setId(1L);
        when(tenantService.create(dto)).thenReturn(created);

        ResponseEntity<TenantResponseDto> resp = controller.create(dto);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1L, resp.getBody().getId());
        verify(tenantService).create(dto);
    }

    @Test
    void precheck_shouldDelegate() {
        TenantCreateUpdateDto dto = new TenantCreateUpdateDto();
        TenantInitializationSummaryDto summary = new TenantInitializationSummaryDto();
        summary.setTenantCode("acme");
        TenantPrecheckResponseDto precheck = new TenantPrecheckResponseDto();
        precheck.setOk(true);
        precheck.setInitializationSummary(summary);
        when(tenantService.precheckCreate(dto)).thenReturn(precheck);

        ResponseEntity<TenantPrecheckResponseDto> resp = controller.precheck(dto);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isOk());
        assertEquals("acme", resp.getBody().getInitializationSummary().getTenantCode());
        verify(tenantService).precheckCreate(dto);
    }

    @Test
    void update_shouldDelegate() {
        TenantCreateUpdateDto dto = new TenantCreateUpdateDto();
        TenantResponseDto updated = new TenantResponseDto();
        updated.setId(2L);
        when(tenantService.update(2L, dto)).thenReturn(updated);

        ResponseEntity<TenantResponseDto> resp = controller.update(2L, dto);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(2L, resp.getBody().getId());
        verify(tenantService).update(2L, dto);
    }

    @Test
    void initializePlatformTemplate_shouldDelegate() {
        when(tenantService.initializePlatformTemplates()).thenReturn(true);

        ResponseEntity<java.util.Map<String, Object>> resp = controller.initializePlatformTemplate();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(Boolean.TRUE, resp.getBody().get("initialized"));
        verify(tenantService).initializePlatformTemplates();
    }

    @Test
    void diffPlatformTemplate_shouldDelegate() {
        PlatformTemplateDiffResult diff = new PlatformTemplateDiffResult(
            7L,
            new PlatformTemplateDiffResult.Summary(1, 1, 0, 0, 0),
            List.of()
        );
        when(tenantService.diffPlatformTemplate(7L)).thenReturn(diff);

        ResponseEntity<PlatformTemplateDiffResult> resp = controller.diffPlatformTemplate(7L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(7L, resp.getBody().tenantId());
        verify(tenantLifecycleAccessGuard).assertPlatformTargetTenantReadable(7L, "system:tenant:view");
        verify(tenantService).diffPlatformTemplate(7L);
    }

    @Test
    void permissionSummary_shouldDelegate() {
        TenantPermissionSummaryDto summary = new TenantPermissionSummaryDto(7L, 5L, 4L, 30L, 28L, 24L, 20L, 10L, 8L, 6L);
        when(tenantService.summarizeTenantPermissions(7L)).thenReturn(summary);

        ResponseEntity<TenantPermissionSummaryDto> resp = controller.permissionSummary(7L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(30L, resp.getBody().totalPermissions());
        verify(tenantLifecycleAccessGuard).assertPlatformTargetTenantReadable(7L, "system:tenant:view");
        verify(tenantService).summarizeTenantPermissions(7L);
    }

    @Test
    void freeze_shouldDelegate() {
        TenantResponseDto updated = new TenantResponseDto();
        updated.setId(5L);
        updated.setLifecycleStatus("FROZEN");
        when(tenantService.freeze(5L)).thenReturn(updated);

        ResponseEntity<TenantResponseDto> resp = controller.freeze(5L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("FROZEN", resp.getBody().getLifecycleStatus());
        verify(tenantService).freeze(5L);
    }

    @Test
    void unfreeze_shouldDelegate() {
        TenantResponseDto updated = new TenantResponseDto();
        updated.setId(6L);
        updated.setLifecycleStatus("ACTIVE");
        when(tenantService.unfreeze(6L)).thenReturn(updated);

        ResponseEntity<TenantResponseDto> resp = controller.unfreeze(6L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("ACTIVE", resp.getBody().getLifecycleStatus());
        verify(tenantService).unfreeze(6L);
    }

    @Test
    void decommission_shouldDelegate() {
        TenantResponseDto updated = new TenantResponseDto();
        updated.setId(7L);
        updated.setLifecycleStatus("DECOMMISSIONED");
        when(tenantService.decommission(7L)).thenReturn(updated);

        ResponseEntity<TenantResponseDto> resp = controller.decommission(7L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("DECOMMISSIONED", resp.getBody().getLifecycleStatus());
        verify(tenantService).decommission(7L);
    }

    @Test
    void delete_shouldDelegateAndReturn204() {
        ResponseEntity<Void> resp = controller.delete(3L);

        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
        verify(tenantService).delete(3L);
    }
}
