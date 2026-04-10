package com.tiny.platform.core.oauth.service.impl;

import com.tiny.platform.core.oauth.config.MfaProperties;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.TotpVerificationGuard;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import com.tiny.platform.infrastructure.auth.user.service.UserAuthenticationBridgeWriter;
import com.tiny.platform.infrastructure.auth.user.service.UserAuthenticationMethodProfileService;
import com.tiny.platform.infrastructure.auth.user.support.UserAuthenticationCredential;
import com.tiny.platform.infrastructure.auth.user.support.UserAuthenticationMethodProfile;
import com.tiny.platform.infrastructure.auth.user.support.UserAuthenticationScopePolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityServiceImplBridgeSyncTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unbindTotpShouldDeleteLegacyAndBridgeRowsForActiveScope() {
        UserAuthenticationMethodProfileService profileService = mock(UserAuthenticationMethodProfileService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        MfaProperties mfaProperties = new MfaProperties();
        TotpVerificationGuard totpVerificationGuard = mock(TotpVerificationGuard.class);
        UserAuthenticationBridgeWriter bridgeWriter = mock(UserAuthenticationBridgeWriter.class);
        SecurityServiceImpl service =
                new SecurityServiceImpl(
                        profileService,
                        passwordEncoder,
                        mfaProperties,
                        totpVerificationGuard,
                        bridgeWriter);

        User user = user();
        UserAuthenticationMethod totpMethod = Objects.requireNonNull(totpMethod());
        SecurityUser securityUser = new SecurityUser(user, "", 9L, Set.of());
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                securityUser, null, securityUser.getAuthorities()));

        when(profileService.resolveStorageTenantIdForWrite(eq("TENANT"), eq(9L))).thenReturn(9L);
        when(profileService.findEffectiveMethodProfile(eq(1L), eq("TENANT"), eq(9L), eq("LOCAL"), eq("TOTP")))
                .thenReturn(Optional.of(profile(totpMethod)));

        Map<String, Object> result = service.unbindTotp(user, null, "123456");

        assertThat(result).containsEntry("success", true);
        verify(bridgeWriter).deleteRuntime(argThat(m ->
                m != null
                        && m.getUserId().equals(1L)
                        && "LOCAL".equals(m.getAuthenticationProvider())
                        && "TOTP".equals(m.getAuthenticationType())));
    }

    private static User user() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        return user;
    }

    private static UserAuthenticationMethod totpMethod() {
        UserAuthenticationMethod method = new UserAuthenticationMethod();
        method.setId(11L);
        method.setUserId(1L);
        method.setTenantId(9L);
        method.setAuthenticationProvider("LOCAL");
        method.setAuthenticationType("TOTP");
        method.setAuthenticationConfiguration(Map.of("secretKey", "BASE32SECRET", "activated", true));
        return method;
    }

    private static UserAuthenticationMethodProfile profile(UserAuthenticationMethod storageRecord) {
        return new UserAuthenticationMethodProfile(
                new UserAuthenticationCredential(
                        1L,
                        "LOCAL",
                        "TOTP",
                        storageRecord.getAuthenticationConfiguration(),
                        null,
                        null,
                        null),
                new UserAuthenticationScopePolicy(1L, 9L, "LOCAL", "TOTP", false, true, 1),
                storageRecord);
    }
}
