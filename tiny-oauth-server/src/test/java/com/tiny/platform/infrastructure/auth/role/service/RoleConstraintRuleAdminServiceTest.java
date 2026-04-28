package com.tiny.platform.infrastructure.auth.role.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.repository.RoleCardinalityRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleHierarchyRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleMutexRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RolePrerequisiteRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleConstraintRuleAdminServiceTest {

    @Mock
    private RoleHierarchyRepository roleHierarchyRepository;

    @Mock
    private RoleMutexRepository roleMutexRepository;

    @Mock
    private RoleCardinalityRepository roleCardinalityRepository;

    @Mock
    private RolePrerequisiteRepository rolePrerequisiteRepository;

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private RoleConstraintRuleAdminService service;

    @Test
    void upsertPlatformRoleHierarchyEdge_shouldRejectNonPlatformRoles() {
        Role platformRole = new Role();
        platformRole.setId(11L);
        platformRole.setTenantId(null);
        platformRole.setRoleLevel("PLATFORM");

        when(roleRepository.findByIdInAndTenantIdIsNullOrderByIdAsc(List.of(10L, 11L)))
            .thenReturn(List.of(platformRole));

        assertThatThrownBy(() -> service.upsertPlatformRoleHierarchyEdge(10L, 11L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("平台角色");

        verify(roleHierarchyRepository, never()).save(any());
    }

    @Test
    void upsertPlatformRoleMutex_shouldPersistWhenAllRolesArePlatformRoles() {
        Role roleA = new Role();
        roleA.setId(10L);
        roleA.setTenantId(null);
        roleA.setRoleLevel("PLATFORM");

        Role roleB = new Role();
        roleB.setId(11L);
        roleB.setTenantId(null);
        roleB.setRoleLevel("PLATFORM");

        when(roleRepository.findByIdInAndTenantIdIsNullOrderByIdAsc(any()))
            .thenReturn(List.of(roleA, roleB));

        service.upsertPlatformRoleMutex(11L, 10L);

        verify(roleMutexRepository).deleteByTenantIdIsNullAndLeftRoleIdAndRightRoleId(10L, 11L);
        verify(roleMutexRepository).save(any());
    }
}
