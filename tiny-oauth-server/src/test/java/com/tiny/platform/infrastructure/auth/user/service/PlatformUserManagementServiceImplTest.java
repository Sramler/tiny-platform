package com.tiny.platform.infrastructure.auth.user.service;

import com.tiny.platform.infrastructure.auth.user.domain.PlatformUserProfile;
import com.tiny.platform.infrastructure.auth.user.dto.PlatformUserManagementDtos.PlatformUserCreateDto;
import com.tiny.platform.infrastructure.auth.role.domain.PlatformRoleGovernanceCodes;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.role.service.RoleAssignmentSyncService;
import com.tiny.platform.infrastructure.auth.user.repository.PlatformUserDetailProjection;
import com.tiny.platform.infrastructure.auth.user.repository.PlatformUserListProjection;
import com.tiny.platform.infrastructure.auth.user.repository.PlatformUserProfileRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformUserManagementServiceImplTest {

    private PlatformUserProfileRepository platformUserProfileRepository;
    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private RoleAssignmentSyncService roleAssignmentSyncService;
    private PlatformUserManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        platformUserProfileRepository = mock(PlatformUserProfileRepository.class);
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        service = new PlatformUserManagementServiceImpl(
            platformUserProfileRepository,
            userRepository,
            roleRepository,
            roleAssignmentSyncService
        );
    }

    @Test
    void list_shouldMapRepositoryPage() {
        when(platformUserProfileRepository.findPage("platform", true, "ACTIVE", PageRequest.of(0, 10)))
            .thenReturn(new PageImpl<>(
                List.of(listProjection()),
                PageRequest.of(0, 10),
                1
            ));

        var page = service.list("platform", true, "ACTIVE", PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).username()).isEqualTo("platform_admin");
        assertThat(page.getContent().get(0).hasPlatformRoleAssignment()).isTrue();
    }

    @Test
    void getRoles_shouldReturnBoundPlatformRoles() {
        when(platformUserProfileRepository.existsByUserId(9L)).thenReturn(true);
        when(roleAssignmentSyncService.findActiveRoleIdsForUserInPlatform(9L)).thenReturn(List.of(2L, 1L));
        Role admin = new Role();
        admin.setId(1L);
        admin.setTenantId(null);
        admin.setRoleLevel("PLATFORM");
        admin.setCode("ROLE_PLATFORM_ADMIN");
        admin.setName("平台管理员");
        admin.setEnabled(true);
        Role auditor = new Role();
        auditor.setId(2L);
        auditor.setTenantId(null);
        auditor.setRoleLevel("PLATFORM");
        auditor.setCode("ROLE_PLATFORM_AUDITOR");
        auditor.setName("平台审计员");
        auditor.setEnabled(true);
        when(roleRepository.findByIdInAndTenantIdIsNullOrderByIdAsc(List.of(2L, 1L))).thenReturn(List.of(admin, auditor));

        var roles = service.getRoles(9L);

        assertThat(roles).hasSize(2);
        assertThat(roles.get(0).code()).isEqualTo("ROLE_PLATFORM_ADMIN");
    }

    @Test
    void replaceRoles_shouldRejectTenantRoleBinding() {
        when(platformUserProfileRepository.existsByUserId(9L)).thenReturn(true);
        Role tenantRole = new Role();
        tenantRole.setId(100L);
        tenantRole.setTenantId(1L);
        tenantRole.setRoleLevel("TENANT");
        when(roleRepository.findByIdInAndTenantIdIsNullOrderByIdAsc(List.of(100L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.replaceRoles(9L, List.of(100L)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("仅允许绑定");
    }

    @Test
    void replaceRoles_shouldRejectDirectWrite_whenGrantingOneStepApprovalRole() {
        when(platformUserProfileRepository.existsByUserId(9L)).thenReturn(true);
        Role sensitive = oneStepPlatformRole();
        when(roleRepository.findByIdInAndTenantIdIsNullOrderByIdAsc(anyList())).thenAnswer(invocation -> {
            java.util.List<Long> ids = invocation.getArgument(0);
            if (ids.equals(List.of(5L))) {
                return List.of(sensitive);
            }
            return List.of();
        });
        when(roleAssignmentSyncService.findActiveRoleIdsForUserInPlatform(9L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.replaceRoles(9L, List.of(5L)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("审批");
    }

    @Test
    void replaceRoles_shouldRejectDirectWrite_whenRevokingOneStepApprovalRole() {
        when(platformUserProfileRepository.existsByUserId(9L)).thenReturn(true);
        Role sensitive = oneStepPlatformRole();
        when(roleRepository.findByIdInAndTenantIdIsNullOrderByIdAsc(anyList())).thenAnswer(invocation -> {
            java.util.List<Long> ids = invocation.getArgument(0);
            if (ids.equals(List.of(5L))) {
                return List.of(sensitive);
            }
            return List.of();
        });
        when(roleAssignmentSyncService.findActiveRoleIdsForUserInPlatform(9L)).thenReturn(List.of(5L));

        assertThatThrownBy(() -> service.replaceRoles(9L, List.of()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("审批");
    }

    private static Role oneStepPlatformRole() {
        Role sensitive = new Role();
        sensitive.setId(5L);
        sensitive.setTenantId(null);
        sensitive.setRoleLevel("PLATFORM");
        sensitive.setApprovalMode(PlatformRoleGovernanceCodes.APPROVAL_MODE_ONE_STEP);
        sensitive.setCode("ROLE_SENSITIVE");
        sensitive.setName("敏感");
        sensitive.setEnabled(true);
        return sensitive;
    }

    @Test
    void replaceRoles_shouldWritePlatformAssignments() {
        when(platformUserProfileRepository.existsByUserId(9L)).thenReturn(true);
        Role admin = new Role();
        admin.setId(1L);
        admin.setTenantId(null);
        admin.setRoleLevel("PLATFORM");
        admin.setCode("ROLE_PLATFORM_ADMIN");
        admin.setName("平台管理员");
        admin.setEnabled(true);
        when(roleRepository.findByIdInAndTenantIdIsNullOrderByIdAsc(List.of(1L))).thenReturn(List.of(admin));
        when(roleAssignmentSyncService.findActiveRoleIdsForUserInPlatform(9L)).thenReturn(List.of(1L));

        var roles = service.replaceRoles(9L, List.of(1L, 1L));

        verify(roleAssignmentSyncService).replaceUserPlatformRoleAssignments(9L, List.of(1L));
        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).roleId()).isEqualTo(1L);
    }

    @Test
    void replaceRoles_shouldFailClosedWhenRoleIdsContainsInvalidValue() {
        when(platformUserProfileRepository.existsByUserId(9L)).thenReturn(true);

        assertThatThrownBy(() -> service.replaceRoles(9L, List.of(1L, 0L, -2L)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("roleIds");
    }

    @Test
    void create_shouldRejectUnsupportedStatus() {
        assertThatThrownBy(() -> service.create(new PlatformUserCreateDto(9L, "平台管理员", "PAUSED")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("ACTIVE");
    }

    @Test
    void create_shouldPersistProfileForExistingUser() {
        User user = new User();
        user.setId(9L);
        user.setUsername("platform_admin");
        user.setNickname("平台管理员");
        when(platformUserProfileRepository.existsByUserId(9L)).thenReturn(false);
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(platformUserProfileRepository.findDetailByUserId(9L)).thenReturn(Optional.of(detailProjection()));

        var detail = service.create(new PlatformUserCreateDto(9L, null, "ACTIVE"));

        assertThat(detail.userId()).isEqualTo(9L);
        verify(platformUserProfileRepository).saveAndFlush(any(PlatformUserProfile.class));
    }

    @Test
    void updateStatus_shouldReturnFalseWhenMissing() {
        when(platformUserProfileRepository.findByUserId(88L)).thenReturn(Optional.empty());

        assertThat(service.updateStatus(88L, "DISABLED")).isFalse();
    }

    @Test
    void isPlatformUserActive_shouldDelegateToRepository() {
        when(platformUserProfileRepository.existsByUserIdAndStatus(9L, PlatformUserProfile.STATUS_ACTIVE)).thenReturn(true);

        assertThat(service.isPlatformUserActive(9L)).isTrue();
    }

    @Test
    void get_shouldCoerceNumericBooleanColumnsFromNativeProjection() {
        when(platformUserProfileRepository.findDetailByUserId(9L)).thenReturn(Optional.of(new PlatformUserDetailProjection() {
            @Override
            public Long getUserId() {
                return 9L;
            }

            @Override
            public String getUsername() {
                return "platform_admin";
            }

            @Override
            public String getNickname() {
                return "平台管理员";
            }

            @Override
            public String getDisplayName() {
                return "平台管理员";
            }

            @Override
            public String getEmail() {
                return "platform@example.com";
            }

            @Override
            public String getPhone() {
                return "13800000000";
            }

            @Override
            public Object getUserEnabled() {
                return 1L;
            }

            @Override
            public Object getAccountNonExpired() {
                return 1L;
            }

            @Override
            public Object getAccountNonLocked() {
                return 0L;
            }

            @Override
            public Object getCredentialsNonExpired() {
                return 1L;
            }

            @Override
            public String getPlatformStatus() {
                return "ACTIVE";
            }

            @Override
            public Object getHasPlatformRoleAssignment() {
                return 1L;
            }

            @Override
            public LocalDateTime getLastLoginAt() {
                return LocalDateTime.now();
            }

            @Override
            public LocalDateTime getCreatedAt() {
                return LocalDateTime.now().minusDays(1);
            }

            @Override
            public LocalDateTime getUpdatedAt() {
                return LocalDateTime.now();
            }
        }));

        var detail = service.get(9L).orElseThrow();

        assertThat(detail.userEnabled()).isTrue();
        assertThat(detail.accountNonExpired()).isTrue();
        assertThat(detail.accountNonLocked()).isFalse();
        assertThat(detail.credentialsNonExpired()).isTrue();
        assertThat(detail.hasPlatformRoleAssignment()).isTrue();
    }

    @Test
    void list_shouldCoerceBooleanColumnsFromNativeProjection() {
        when(platformUserProfileRepository.findPage(null, null, null, PageRequest.of(0, 10)))
            .thenReturn(new PageImpl<>(
                List.of(new PlatformUserListProjection() {
                    @Override
                    public Long getUserId() {
                        return 11L;
                    }

                    @Override
                    public String getUsername() {
                        return "boolean_projection_user";
                    }

                    @Override
                    public String getNickname() {
                        return "布尔投影用户";
                    }

                    @Override
                    public String getDisplayName() {
                        return "布尔投影用户";
                    }

                    @Override
                    public Object getUserEnabled() {
                        return Boolean.TRUE;
                    }

                    @Override
                    public String getPlatformStatus() {
                        return "ACTIVE";
                    }

                    @Override
                    public Object getHasPlatformRoleAssignment() {
                        return Boolean.FALSE;
                    }

                    @Override
                    public LocalDateTime getUpdatedAt() {
                        return LocalDateTime.now();
                    }
                }),
                PageRequest.of(0, 10),
                1
            ));

        var page = service.list(null, null, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).userEnabled()).isTrue();
        assertThat(page.getContent().get(0).hasPlatformRoleAssignment()).isFalse();
    }

    private PlatformUserListProjection listProjection() {
        return new PlatformUserListProjection() {
            @Override
            public Long getUserId() {
                return 9L;
            }

            @Override
            public String getUsername() {
                return "platform_admin";
            }

            @Override
            public String getNickname() {
                return "平台管理员";
            }

            @Override
            public String getDisplayName() {
                return "平台管理员";
            }

            @Override
            public Object getUserEnabled() {
                return 1;
            }

            @Override
            public String getPlatformStatus() {
                return "ACTIVE";
            }

            @Override
            public Object getHasPlatformRoleAssignment() {
                return 1;
            }

            @Override
            public LocalDateTime getUpdatedAt() {
                return LocalDateTime.now();
            }
        };
    }

    private PlatformUserDetailProjection detailProjection() {
        return new PlatformUserDetailProjection() {
            @Override
            public Long getUserId() {
                return 9L;
            }

            @Override
            public String getUsername() {
                return "platform_admin";
            }

            @Override
            public String getNickname() {
                return "平台管理员";
            }

            @Override
            public String getDisplayName() {
                return "平台管理员";
            }

            @Override
            public String getEmail() {
                return "platform@example.com";
            }

            @Override
            public String getPhone() {
                return "13800000000";
            }

            @Override
            public Object getUserEnabled() {
                return 1;
            }

            @Override
            public Object getAccountNonExpired() {
                return 1;
            }

            @Override
            public Object getAccountNonLocked() {
                return 1;
            }

            @Override
            public Object getCredentialsNonExpired() {
                return 1;
            }

            @Override
            public String getPlatformStatus() {
                return "ACTIVE";
            }

            @Override
            public Object getHasPlatformRoleAssignment() {
                return 1;
            }

            @Override
            public LocalDateTime getLastLoginAt() {
                return LocalDateTime.now();
            }

            @Override
            public LocalDateTime getCreatedAt() {
                return LocalDateTime.now().minusDays(1);
            }

            @Override
            public LocalDateTime getUpdatedAt() {
                return LocalDateTime.now();
            }
        };
    }
}
