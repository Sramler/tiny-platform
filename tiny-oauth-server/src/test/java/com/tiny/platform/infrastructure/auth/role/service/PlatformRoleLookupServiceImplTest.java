package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformRoleLookupServiceImplTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void findOptions_shouldReturnPlatformRoleOptionsInPlatformScope() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        PlatformRoleLookupServiceImpl service = new PlatformRoleLookupServiceImpl(roleRepository);
        Role role = new Role();
        role.setId(11L);
        role.setCode("ROLE_PLATFORM_ADMIN");
        role.setName("平台管理员");
        role.setDescription("平台角色候选");
        role.setEnabled(true);
        role.setBuiltin(true);
        role.setTenantId(null);
        role.setRoleLevel("PLATFORM");

        TenantContext.setActiveTenantId(1L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        when(roleRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(role)));

        var result = service.findOptions("platform", 50);

        assertThat(result).containsExactly(new com.tiny.platform.infrastructure.auth.role.dto.PlatformRoleOptionDto(
            11L,
            "ROLE_PLATFORM_ADMIN",
            "平台管理员",
            "平台角色候选",
            true,
            true
        ));
        verify(roleRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void findOptions_shouldFailClosedOutsidePlatformScope() {
        PlatformRoleLookupServiceImpl service = new PlatformRoleLookupServiceImpl(mock(RoleRepository.class));

        TenantContext.setActiveTenantId(7L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);

        assertThatThrownBy(() -> service.findOptions(null, 50))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("PLATFORM");
    }
}
