package com.tiny.platform.core.oauth.security;

import com.tiny.platform.infrastructure.auth.role.service.EffectiveRoleResolutionService;
import com.tiny.platform.infrastructure.auth.user.domain.PlatformUserProfile;
import com.tiny.platform.infrastructure.auth.user.repository.PlatformUserProfileRepository;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthUserResolutionServiceTest {

    @Test
    void shouldRejectPlatformUserWithoutPlatformAssignments() {
        UserRepository userRepository = mock(UserRepository.class);
        PlatformUserProfileRepository platformUserProfileRepository = mock(PlatformUserProfileRepository.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        AuthUserResolutionService service = new AuthUserResolutionService(
            userRepository,
            platformUserProfileRepository,
            tenantUserRepository,
            effectiveRoleResolutionService,
            tenantRepository
        );

        User user = new User();
        user.setId(100L);
        user.setUsername("platform_admin");
        when(userRepository.findAllByUsername("platform_admin")).thenReturn(List.of(user));
        when(platformUserProfileRepository.existsByUserIdAndStatus(100L, PlatformUserProfile.STATUS_ACTIVE)).thenReturn(true);
        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInPlatform(100L)).thenReturn(List.of());

        assertThat(service.resolveUserRecordInPlatform("platform_admin")).isEmpty();
    }

    @Test
    void shouldRejectPlatformUserWhenPlatformProfileMissing() {
        UserRepository userRepository = mock(UserRepository.class);
        PlatformUserProfileRepository platformUserProfileRepository = mock(PlatformUserProfileRepository.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        AuthUserResolutionService service = new AuthUserResolutionService(
            userRepository,
            platformUserProfileRepository,
            tenantUserRepository,
            effectiveRoleResolutionService,
            tenantRepository
        );

        User user = new User();
        user.setId(101L);
        user.setUsername("platform_admin");
        when(userRepository.findAllByUsername("platform_admin")).thenReturn(List.of(user));
        when(platformUserProfileRepository.existsByUserIdAndStatus(101L, PlatformUserProfile.STATUS_ACTIVE)).thenReturn(false);
        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInPlatform(101L)).thenReturn(List.of(1L));

        assertThat(service.resolveUserRecordInPlatform("platform_admin")).isEmpty();
    }

    @Test
    void shouldResolvePlatformUserWhenPlatformProfileAndAssignmentsPresent() {
        UserRepository userRepository = mock(UserRepository.class);
        PlatformUserProfileRepository platformUserProfileRepository = mock(PlatformUserProfileRepository.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        AuthUserResolutionService service = new AuthUserResolutionService(
            userRepository,
            platformUserProfileRepository,
            tenantUserRepository,
            effectiveRoleResolutionService,
            tenantRepository
        );

        User user = new User();
        user.setId(102L);
        user.setUsername("platform_admin");
        when(userRepository.findAllByUsername("platform_admin")).thenReturn(List.of(user));
        when(platformUserProfileRepository.existsByUserIdAndStatus(102L, PlatformUserProfile.STATUS_ACTIVE)).thenReturn(true);
        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInPlatform(102L)).thenReturn(List.of(1L));

        assertThat(service.resolveUserRecordInPlatform("platform_admin")).contains(user);
    }
}
