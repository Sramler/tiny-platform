package com.tiny.platform.core.oauth.service.impl;

import com.tiny.platform.core.oauth.config.MfaProperties;
import com.tiny.platform.core.oauth.security.TotpService;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationMethodRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityServiceImplMfaDecisionTest {

    @Test
    void modeNoneShouldNeverRequireTotp() {
        SecurityServiceImpl service = createService("NONE", true, true);
        Map<String, Object> status = service.getSecurityStatus(mockUser());

        assertThat(status.get("requireTotp")).isEqualTo(false);
    }

    @Test
    void modeOptionalShouldRequireTotpOnlyWhenBoundAndActivated() {
        SecurityServiceImpl boundActivatedService = createService("OPTIONAL", true, true);
        SecurityServiceImpl boundNotActivatedService = createService("OPTIONAL", true, false);
        SecurityServiceImpl unboundService = createService("OPTIONAL", false, false);

        assertThat(boundActivatedService.getSecurityStatus(mockUser()).get("requireTotp")).isEqualTo(true);
        assertThat(boundNotActivatedService.getSecurityStatus(mockUser()).get("requireTotp")).isEqualTo(false);
        assertThat(unboundService.getSecurityStatus(mockUser()).get("requireTotp")).isEqualTo(false);
    }

    @Test
    void modeRequiredShouldRequireTotpOnlyAfterBindingAndActivation() {
        SecurityServiceImpl boundActivatedService = createService("REQUIRED", true, true);
        SecurityServiceImpl unboundService = createService("REQUIRED", false, false);

        assertThat(boundActivatedService.getSecurityStatus(mockUser()).get("requireTotp")).isEqualTo(true);
        assertThat(unboundService.getSecurityStatus(mockUser()).get("requireTotp")).isEqualTo(false);
    }

    private SecurityServiceImpl createService(String mode, boolean totpBound, boolean totpActivated) {
        UserAuthenticationMethodRepository repository = mock(UserAuthenticationMethodRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TotpService totpService = mock(TotpService.class);
        MfaProperties mfaProperties = new MfaProperties();
        mfaProperties.setMode(mode);

        when(repository.existsByUserIdAndTenantIdAndAuthenticationProviderAndAuthenticationType(
                1L, 1L, "LOCAL", "TOTP"
        )).thenReturn(totpBound);

        if (totpBound) {
            UserAuthenticationMethod method = new UserAuthenticationMethod();
            method.setAuthenticationConfiguration(Map.of("activated", totpActivated));
            when(repository.findByUserIdAndTenantIdAndAuthenticationProviderAndAuthenticationType(
                    1L, 1L, "LOCAL", "TOTP"
            )).thenReturn(Optional.of(method));
        }

        return new SecurityServiceImpl(repository, passwordEncoder, mfaProperties, totpService);
    }

    private User mockUser() {
        User user = new User();
        user.setId(1L);
        user.setTenantId(1L);
        user.setUsername("admin");
        return user;
    }
}

