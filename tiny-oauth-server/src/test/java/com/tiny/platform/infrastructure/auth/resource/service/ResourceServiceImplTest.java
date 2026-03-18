package com.tiny.platform.infrastructure.auth.resource.service;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceRequestDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import com.tiny.platform.infrastructure.auth.resource.repository.ResourceRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.role.service.EffectiveRoleResolutionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResourceServiceImplTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void findByUserId_should_prefer_assignment_backed_role_ids() {
        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        ResourceServiceImpl service = new ResourceServiceImpl(
                resourceRepository,
                roleRepository,
                effectiveRoleResolutionService
        );

        TenantContext.setActiveTenantId(1L);

        Resource resource = new Resource();
        resource.setId(9L);
        resource.setTenantId(1L);
        resource.setSort(1);

        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(5L, 1L)).thenReturn(List.of(100L));
        when(roleRepository.findResourceIdsByRoleId(100L)).thenReturn(List.of(9L));
        when(resourceRepository.findByIdInAndTenantId(List.of(9L), 1L)).thenReturn(List.of(resource));

        List<Resource> result = service.findByUserId(5L);

        assertThat(result).containsExactly(resource);
        verify(effectiveRoleResolutionService).findEffectiveRoleIdsForUserInTenant(5L, 1L);
    }

    @Test
    void resources_should_expose_record_tenant_id_in_response_dto() {
        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        ResourceServiceImpl service = new ResourceServiceImpl(
                resourceRepository,
                roleRepository,
                effectiveRoleResolutionService
        );

        TenantContext.setActiveTenantId(8L);

        Resource resource = new Resource();
        resource.setId(3L);
        resource.setTenantId(8L);
        resource.setName("resource-3");

        PageRequest pageable = PageRequest.of(0, 10);
        when(resourceRepository.findByConditions(null, null, null, null, null, null, null, null, 8L, pageable))
                .thenReturn(new PageImpl<>(List.of(resource), pageable, 1));

        var page = service.resources(new ResourceRequestDto(), pageable);

        assertThat(page.getContent()).hasSize(1);
        ResourceResponseDto dto = page.getContent().getFirst();
        assertThat(dto.getRecordTenantId()).isEqualTo(8L);
    }
}
