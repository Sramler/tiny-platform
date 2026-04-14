package com.tiny.platform.infrastructure.auth.user.service;

import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthCredential;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthScopePolicy;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthScopePolicyRepository;
import com.tiny.platform.infrastructure.auth.user.support.UserAuthenticationMethodProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserAuthenticationMethodProfileServiceTest {

    @Test
    void tenantScopeShouldExcludeDisabledMethodWithoutCrossScopeMerge() {
        UserAuthScopePolicyRepository scopePolicyRepository = mock(UserAuthScopePolicyRepository.class);
        UserAuthenticationMethodProfileService service = new UserAuthenticationMethodProfileService(scopePolicyRepository);

        when(scopePolicyRepository.findByUserIdAndScopeKey(1L, "TENANT:9")).thenReturn(List.of(
                newScopePolicy(1L, UserAuthenticationBridgeWriter.SCOPE_TYPE_TENANT, 9L, "LOCAL", "PASSWORD", false, Map.of("password", "{noop}tenant"))
        ));

        List<UserAuthenticationMethodProfile> profiles = service.loadEnabledMethodProfiles(
                1L,
                TenantContextContract.SCOPE_TYPE_TENANT,
                9L
        );

        assertThat(profiles).isEmpty();
    }

    @Test
    void platformScopeShouldLoadOnlyPlatformScopeRows() {
        UserAuthScopePolicyRepository scopePolicyRepository = mock(UserAuthScopePolicyRepository.class);
        UserAuthenticationMethodProfileService service = new UserAuthenticationMethodProfileService(scopePolicyRepository);

        when(scopePolicyRepository.findByUserIdAndScopeKey(1L, "PLATFORM")).thenReturn(List.of(
                newScopePolicy(1L, UserAuthenticationBridgeWriter.SCOPE_TYPE_PLATFORM, null, "LOCAL", "PASSWORD", true, Map.of("password", "{noop}platform")),
                newScopePolicy(1L, UserAuthenticationBridgeWriter.SCOPE_TYPE_PLATFORM, null, "LOCAL", "TOTP", true, Map.of("secretKey", "platform-totp"))
        ));

        List<UserAuthenticationMethodProfile> profiles = service.loadEnabledMethodProfiles(
                1L,
                TenantContextContract.SCOPE_TYPE_PLATFORM,
                null
        );

        assertThat(profiles)
                .extracting(UserAuthenticationMethodProfile::authenticationType)
                .containsExactlyInAnyOrder("PASSWORD", "TOTP");
    }

    @Test
    void tenantScopeShouldReturnEmptyWhenNewModelHasNoMatchingRows() {
        UserAuthScopePolicyRepository scopePolicyRepository = mock(UserAuthScopePolicyRepository.class);
        UserAuthenticationMethodProfileService service =
                new UserAuthenticationMethodProfileService(scopePolicyRepository);

        when(scopePolicyRepository.findByUserIdAndAuthenticationProviderAndAuthenticationTypeAndScopeKey(
                1L, "LOCAL", "PASSWORD", "TENANT:9"
        )).thenReturn(Optional.empty());

        Optional<UserAuthenticationMethodProfile> profile = service.findEffectiveMethodProfile(
                1L,
                TenantContextContract.SCOPE_TYPE_TENANT,
                9L,
                "LOCAL",
                "PASSWORD"
        );

        assertThat(profile).isEmpty();
    }

    @Test
    void runtimeProjectionShouldRetainScopeMetadataForPlatformRows() {
        UserAuthScopePolicyRepository scopePolicyRepository = mock(UserAuthScopePolicyRepository.class);
        UserAuthenticationMethodProfileService service = new UserAuthenticationMethodProfileService(scopePolicyRepository);

        when(scopePolicyRepository.findByUserIdAndAuthenticationProviderAndAuthenticationTypeAndScopeKey(
                1L, "LOCAL", "PASSWORD", "PLATFORM"
        )).thenReturn(Optional.of(
                newScopePolicy(1L, UserAuthenticationBridgeWriter.SCOPE_TYPE_PLATFORM, null, "LOCAL", "PASSWORD", true, Map.of("password", "{noop}platform"))
        ));

        UserAuthenticationMethod storageRecord = service.findEffectiveMethodProfile(
                        1L,
                        TenantContextContract.SCOPE_TYPE_PLATFORM,
                        null,
                        "LOCAL",
                        "PASSWORD"
                )
                .map(UserAuthenticationMethodProfile::storageRecord)
                .orElseThrow();

        assertThat(storageRecord.getRuntimeScopeType()).isEqualTo(UserAuthenticationBridgeWriter.SCOPE_TYPE_PLATFORM);
        assertThat(storageRecord.getRuntimeScopeKey()).isEqualTo("PLATFORM");
        assertThat(storageRecord.getTenantId()).isNull();
    }

    @Test
    void resolveStorageTenantIdForWriteShouldWritePlatformRowsToGlobalCarrier() {
        UserAuthScopePolicyRepository scopePolicyRepository = mock(UserAuthScopePolicyRepository.class);
        UserAuthenticationMethodProfileService service = new UserAuthenticationMethodProfileService(scopePolicyRepository);

        assertThat(service.resolveStorageTenantIdForWrite(TenantContextContract.SCOPE_TYPE_PLATFORM, null)).isNull();
        assertThat(service.resolveStorageTenantIdForWrite(TenantContextContract.SCOPE_TYPE_TENANT, 9L)).isEqualTo(9L);
    }

    private static UserAuthScopePolicy newScopePolicy(
            Long userId,
            String scopeType,
            Long scopeId,
            String provider,
            String type,
            boolean enabled,
            Map<String, Object> configuration) {
        UserAuthCredential credential = new UserAuthCredential();
        credential.setUserId(userId);
        credential.setAuthenticationProvider(provider);
        credential.setAuthenticationType(type);
        credential.setAuthenticationConfiguration(configuration);

        UserAuthScopePolicy scopePolicy = new UserAuthScopePolicy();
        scopePolicy.setCredential(credential);
        scopePolicy.setScopeType(scopeType);
        scopePolicy.setScopeId(scopeId);
        scopePolicy.setScopeKey(UserAuthenticationBridgeWriter.buildScopeKey(scopeType, scopeId));
        scopePolicy.setIsMethodEnabled(enabled);
        scopePolicy.setIsPrimaryMethod(false);
        scopePolicy.setAuthenticationPriority(1);
        return scopePolicy;
    }
}
