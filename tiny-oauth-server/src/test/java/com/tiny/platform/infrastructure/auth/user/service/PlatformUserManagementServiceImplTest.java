package com.tiny.platform.infrastructure.auth.user.service;

import com.tiny.platform.infrastructure.auth.user.domain.PlatformUserProfile;
import com.tiny.platform.infrastructure.auth.user.dto.PlatformUserManagementDtos.PlatformUserCreateDto;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformUserManagementServiceImplTest {

    private PlatformUserProfileRepository platformUserProfileRepository;
    private UserRepository userRepository;
    private PlatformUserManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        platformUserProfileRepository = mock(PlatformUserProfileRepository.class);
        userRepository = mock(UserRepository.class);
        service = new PlatformUserManagementServiceImpl(platformUserProfileRepository, userRepository);
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
