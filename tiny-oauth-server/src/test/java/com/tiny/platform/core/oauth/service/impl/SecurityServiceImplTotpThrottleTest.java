package com.tiny.platform.core.oauth.service.impl;

import com.tiny.platform.core.oauth.config.MfaProperties;
import com.tiny.platform.core.oauth.security.TotpService;
import com.tiny.platform.core.oauth.security.TotpVerificationGuard;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthCredential;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthScopePolicy;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthScopePolicyRepository;
import com.tiny.platform.infrastructure.auth.user.service.UserAuthenticationBridgeWriter;
import com.tiny.platform.infrastructure.auth.user.service.UserAuthenticationMethodProfileService;
import com.tiny.platform.infrastructure.auth.user.support.UserAuthenticationCredential;
import com.tiny.platform.infrastructure.auth.user.support.UserAuthenticationMethodProfile;
import com.tiny.platform.infrastructure.auth.user.support.UserAuthenticationScopePolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityServiceImplTotpThrottleTest {

    @BeforeEach
    void setUp() {
        TenantContext.setActiveTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void checkTotpShouldReturnLockMessageWhenMethodIsLocked() {
        UserAuthScopePolicyRepository scopeRepo = mock(UserAuthScopePolicyRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TotpService totpService = mock(TotpService.class);
        MfaProperties mfaProperties = new MfaProperties();
        TotpVerificationGuard guard = new TotpVerificationGuard(mock(UserAuthenticationBridgeWriter.class), mfaProperties, totpService);
        UserAuthenticationMethodProfileService profileService = profileService(scopeRepo);
        SecurityServiceImpl service = new SecurityServiceImpl(profileService, passwordEncoder, mfaProperties, guard,
                mock(UserAuthenticationBridgeWriter.class));

        User user = user();
        UserAuthenticationMethod method = totpMethod();
        method.setAuthenticationConfiguration(new HashMap<>(Map.of(
                "secretKey", "BASE32SECRET",
                "activated", true,
                TotpVerificationGuard.LOCKED_UNTIL_KEY, LocalDateTime.now().plusMinutes(5).toString()
        )));

        when(scopeRepo.findByUserIdAndAuthenticationProviderAndAuthenticationTypeAndScopeKey(
                1L, "LOCAL", "TOTP", "TENANT:1"
        )).thenReturn(Optional.of(wrapTotp(method)));

        Map<String, Object> result = service.checkTotp(user, "123456");

        assertThat(result).containsEntry("success", false);
        assertThat(String.valueOf(result.get("error"))).contains("TOTP 验证尝试过多");
        verify(totpService, never()).verify(anyString(), anyString());
    }

    @Test
    void checkTotpShouldLockAfterRepeatedFailures() {
        UserAuthenticationMethodProfileService profileService = mock(UserAuthenticationMethodProfileService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TotpService totpService = mock(TotpService.class);
        when(totpService.verify("BASE32SECRET", "000000")).thenReturn(false);

        MfaProperties mfaProperties = new MfaProperties();
        UserAuthenticationBridgeWriter bridgeWriter = mock(UserAuthenticationBridgeWriter.class);
        TotpVerificationGuard guard = new TotpVerificationGuard(bridgeWriter, mfaProperties, totpService);
        SecurityServiceImpl service = new SecurityServiceImpl(profileService, passwordEncoder, mfaProperties, guard, bridgeWriter);

        User user = user();
        UserAuthenticationMethod method = totpMethod();
        when(profileService.findEffectiveMethodProfile(eq(1L), any(), any(), eq("LOCAL"), eq("TOTP")))
                .thenAnswer(invocation -> Optional.of(totpProfile(method)));

        for (int i = 0; i < 4; i++) {
            Map<String, Object> result = service.checkTotp(user, "000000");
            assertThat(result).containsEntry("success", false).containsEntry("error", "验证码错误");
        }

        Map<String, Object> lockedResult = service.checkTotp(user, "000000");
        assertThat(lockedResult).containsEntry("success", false);
        assertThat(String.valueOf(lockedResult.get("error"))).contains("TOTP 验证尝试过多");
    }

    private static UserAuthenticationMethodProfile totpProfile(UserAuthenticationMethod storageRecord) {
        return new UserAuthenticationMethodProfile(
                new UserAuthenticationCredential(
                        storageRecord.getUserId(),
                        "LOCAL",
                        "TOTP",
                        storageRecord.getAuthenticationConfiguration(),
                        null,
                        null,
                        null),
                new UserAuthenticationScopePolicy(
                        storageRecord.getUserId(),
                        storageRecord.getTenantId(),
                        "LOCAL",
                        "TOTP",
                        false,
                        true,
                        1),
                storageRecord);
    }

    private static User user() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        return user;
    }

    private static UserAuthenticationMethod totpMethod() {
        UserAuthenticationMethod method = new UserAuthenticationMethod();
        method.setId(1L);
        method.setUserId(1L);
        method.setTenantId(1L);
        method.setAuthenticationProvider("LOCAL");
        method.setAuthenticationType("TOTP");
        method.setAuthenticationConfiguration(new HashMap<>(Map.of(
                "secretKey", "BASE32SECRET",
                "activated", true
        )));
        return method;
    }

    private static UserAuthScopePolicy wrapTotp(UserAuthenticationMethod method) {
        UserAuthCredential credential = new UserAuthCredential();
        credential.setUserId(method.getUserId());
        credential.setAuthenticationProvider("LOCAL");
        credential.setAuthenticationType("TOTP");
        credential.setAuthenticationConfiguration(method.getAuthenticationConfiguration());

        UserAuthScopePolicy scopePolicy = new UserAuthScopePolicy();
        scopePolicy.setCredential(credential);
        scopePolicy.setScopeType("TENANT");
        scopePolicy.setScopeId(1L);
        scopePolicy.setScopeKey("TENANT:1");
        scopePolicy.setIsMethodEnabled(true);
        scopePolicy.setIsPrimaryMethod(false);
        scopePolicy.setAuthenticationPriority(1);
        return scopePolicy;
    }

    private static UserAuthenticationMethodProfileService profileService(UserAuthScopePolicyRepository scopeRepo) {
        lenient().when(scopeRepo.findByUserIdAndAuthenticationProviderAndAuthenticationTypeAndScopeKey(
                anyLong(), anyString(), anyString(), anyString()
        )).thenReturn(Optional.empty());
        lenient().when(scopeRepo.findByUserIdAndScopeKey(anyLong(), anyString())).thenReturn(java.util.List.of());
        return new UserAuthenticationMethodProfileService(scopeRepo);
    }
}
