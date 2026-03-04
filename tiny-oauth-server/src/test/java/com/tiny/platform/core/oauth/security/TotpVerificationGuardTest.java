package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.config.MfaProperties;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationMethodRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

class TotpVerificationGuardTest {

    @Test
    void should_increment_failed_attempts_and_lock_after_threshold() {
        UserAuthenticationMethodRepository repository = mock(UserAuthenticationMethodRepository.class);
        TotpService totpService = mock(TotpService.class);
        when(totpService.verify("BASE32SECRET", "000000")).thenReturn(false);

        TotpVerificationGuard guard = new TotpVerificationGuard(repository, mfaProperties(), totpService);
        UserAuthenticationMethod method = totpMethod();

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> guard.verifyOrThrow("alice", method, "BASE32SECRET", "000000", "验证码错误"))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("验证码错误");
        }

        assertThatThrownBy(() -> guard.verifyOrThrow("alice", method, "BASE32SECRET", "000000", "验证码错误"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("TOTP 验证尝试过多");

        assertThat(method.getAuthenticationConfiguration())
                .containsKey(TotpVerificationGuard.LOCKED_UNTIL_KEY)
                .containsEntry(TotpVerificationGuard.FAILED_ATTEMPTS_KEY, 5);
        verify(repository, times(5)).save(same(method));
    }

    @Test
    void should_reject_when_totp_is_already_locked_without_verifying_code() {
        UserAuthenticationMethodRepository repository = mock(UserAuthenticationMethodRepository.class);
        TotpService totpService = mock(TotpService.class);
        TotpVerificationGuard guard = new TotpVerificationGuard(repository, mfaProperties(), totpService);
        UserAuthenticationMethod method = totpMethod();
        method.setAuthenticationConfiguration(new HashMap<>(Map.of(
                "secret", "BASE32SECRET",
                TotpVerificationGuard.LOCKED_UNTIL_KEY, LocalDateTime.now().plusMinutes(5).toString()
        )));

        assertThatThrownBy(() -> guard.verifyOrThrow("alice", method, "BASE32SECRET", "123456", "验证码错误"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("TOTP 验证尝试过多");

        verify(totpService, never()).verify(anyString(), anyString());
        verify(repository, never()).save(any());
    }

    @Test
    void should_clear_failure_state_after_success() {
        UserAuthenticationMethodRepository repository = mock(UserAuthenticationMethodRepository.class);
        TotpService totpService = mock(TotpService.class);
        when(totpService.verify("BASE32SECRET", "123456")).thenReturn(true);

        TotpVerificationGuard guard = new TotpVerificationGuard(repository, mfaProperties(), totpService);
        UserAuthenticationMethod method = totpMethod();
        method.setAuthenticationConfiguration(new HashMap<>(Map.of(
                "secret", "BASE32SECRET",
                TotpVerificationGuard.FAILED_ATTEMPTS_KEY, 3,
                TotpVerificationGuard.LAST_FAILED_AT_KEY, LocalDateTime.now().toString()
        )));

        guard.verifyOrThrow("alice", method, "BASE32SECRET", "123456", "验证码错误");

        assertThat(method.getAuthenticationConfiguration())
                .doesNotContainKeys(
                        TotpVerificationGuard.FAILED_ATTEMPTS_KEY,
                        TotpVerificationGuard.LAST_FAILED_AT_KEY,
                        TotpVerificationGuard.LOCKED_UNTIL_KEY
                );
        verify(repository, never()).save(any());
    }

    private static MfaProperties mfaProperties() {
        MfaProperties properties = new MfaProperties();
        properties.setTotpMaxFailedAttempts(5);
        properties.setTotpFailureWindowMinutes(10);
        properties.setTotpLockMinutes(10);
        return properties;
    }

    private static UserAuthenticationMethod totpMethod() {
        UserAuthenticationMethod method = new UserAuthenticationMethod();
        method.setId(1L);
        method.setUserId(1L);
        method.setTenantId(1L);
        method.setAuthenticationProvider("LOCAL");
        method.setAuthenticationType("TOTP");
        method.setAuthenticationConfiguration(new HashMap<>(Map.of("secret", "BASE32SECRET")));
        return method;
    }
}
