package com.tiny.platform.core.oauth.service.impl;

import com.tiny.platform.core.oauth.config.MfaProperties;
import com.tiny.platform.core.oauth.security.TotpService;
import com.tiny.platform.core.oauth.security.TotpVerificationGuard;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationMethodRepository;
import com.tiny.platform.infrastructure.tenant.config.PlatformTenantResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

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
        UserAuthenticationMethodRepository repository = mock(UserAuthenticationMethodRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TotpService totpService = mock(TotpService.class);
        MfaProperties mfaProperties = new MfaProperties();
        TotpVerificationGuard guard = new TotpVerificationGuard(repository, mfaProperties, totpService);
        PlatformTenantResolver platformTenantResolver = mock(PlatformTenantResolver.class);
        SecurityServiceImpl service = new SecurityServiceImpl(repository, passwordEncoder, mfaProperties, guard, platformTenantResolver);

        User user = user();
        UserAuthenticationMethod method = totpMethod();
        method.setAuthenticationConfiguration(new HashMap<>(Map.of(
                "secretKey", "BASE32SECRET",
                "activated", true,
                TotpVerificationGuard.LOCKED_UNTIL_KEY, LocalDateTime.now().plusMinutes(5).toString()
        )));

        when(repository.findEffectiveAuthenticationMethod(1L, 1L, "LOCAL", "TOTP")).thenReturn(Optional.of(method));

        Map<String, Object> result = service.checkTotp(user, "123456");

        assertThat(result).containsEntry("success", false);
        assertThat(String.valueOf(result.get("error"))).contains("TOTP 验证尝试过多");
        verify(totpService, never()).verify(anyString(), anyString());
    }

    @Test
    void checkTotpShouldLockAfterRepeatedFailures() {
        UserAuthenticationMethodRepository repository = mock(UserAuthenticationMethodRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TotpService totpService = mock(TotpService.class);
        when(totpService.verify("BASE32SECRET", "000000")).thenReturn(false);

        MfaProperties mfaProperties = new MfaProperties();
        TotpVerificationGuard guard = new TotpVerificationGuard(repository, mfaProperties, totpService);
        PlatformTenantResolver platformTenantResolver = mock(PlatformTenantResolver.class);
        SecurityServiceImpl service = new SecurityServiceImpl(repository, passwordEncoder, mfaProperties, guard, platformTenantResolver);

        User user = user();
        UserAuthenticationMethod method = totpMethod();
        when(repository.findEffectiveAuthenticationMethod(1L, 1L, "LOCAL", "TOTP")).thenReturn(Optional.of(method));

        for (int i = 0; i < 4; i++) {
            Map<String, Object> result = service.checkTotp(user, "000000");
            assertThat(result).containsEntry("success", false).containsEntry("error", "验证码错误");
        }

        Map<String, Object> lockedResult = service.checkTotp(user, "000000");
        assertThat(lockedResult).containsEntry("success", false);
        assertThat(String.valueOf(lockedResult.get("error"))).contains("TOTP 验证尝试过多");
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
}
