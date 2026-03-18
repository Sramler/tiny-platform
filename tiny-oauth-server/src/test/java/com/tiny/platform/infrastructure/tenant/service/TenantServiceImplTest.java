package com.tiny.platform.infrastructure.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.dto.TenantCreateUpdateDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantResponseDto;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TenantServiceImplTest {

    private TenantRepository tenantRepository;
    private TenantBootstrapService tenantBootstrapService;
    private TenantServiceImpl service;

    @BeforeEach
    void setUp() {
        tenantRepository = org.mockito.Mockito.mock(TenantRepository.class);
        tenantBootstrapService = org.mockito.Mockito.mock(TenantBootstrapService.class);
        service = new TenantServiceImpl(tenantRepository, tenantBootstrapService);
    }

    @Test
    void create_shouldNormalizeCodeAndBootstrapFromDefaultTenant() {
        TenantCreateUpdateDto dto = new TenantCreateUpdateDto();
        dto.setCode(" Acme-01 ");
        dto.setName("Acme");
        dto.setDomain("acme.example.com");

        when(tenantRepository.existsByCode("acme-01")).thenReturn(false);
        when(tenantRepository.existsByDomain("acme.example.com")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(42L);
            return tenant;
        });

        TenantResponseDto response = service.create(dto);

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        verify(tenantBootstrapService).bootstrapFromDefaultTenant(tenantCaptor.getValue());
        assertThat(tenantCaptor.getValue().getCode()).isEqualTo("acme-01");
        assertThat(response.getId()).isEqualTo(42L);
        assertThat(response.getCode()).isEqualTo("acme-01");
    }

    @Test
    void create_whenBootstrapFails_shouldPropagateAndNotMaskError() {
        TenantCreateUpdateDto dto = new TenantCreateUpdateDto();
        dto.setCode("tenant-x");
        dto.setName("Tenant X");

        when(tenantRepository.existsByCode("tenant-x")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(99L);
            return tenant;
        });
        org.mockito.Mockito.doThrow(new IllegalStateException("bootstrap failed"))
            .when(tenantBootstrapService)
            .bootstrapFromDefaultTenant(any(Tenant.class));

        assertThatThrownBy(() -> service.create(dto))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("bootstrap failed");

        verify(tenantRepository).save(any(Tenant.class));
    }

    @Test
    void create_whenTenantCodeAlreadyExists_shouldNotBootstrap() {
        TenantCreateUpdateDto dto = new TenantCreateUpdateDto();
        dto.setCode("tenant-x");
        dto.setName("Tenant X");
        when(tenantRepository.existsByCode("tenant-x")).thenReturn(true);

        assertThatThrownBy(() -> service.create(dto))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("租户编码已存在");

        verify(tenantBootstrapService, never()).bootstrapFromDefaultTenant(any(Tenant.class));
    }
}
