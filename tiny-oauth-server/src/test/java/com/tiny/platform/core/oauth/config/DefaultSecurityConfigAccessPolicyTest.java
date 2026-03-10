package com.tiny.platform.core.oauth.config;

import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultSecurityConfigAccessPolicyTest {

    @Test
    void challengeFlowShouldAllowPartialTokenWithCompletedFactor() {
        MultiFactorAuthenticationToken partial = MultiFactorAuthenticationToken.partiallyAuthenticated(
                "admin",
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                Set.of(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD),
                List.of()
        );

        assertThat(DefaultSecurityConfig.hasChallengeFlowAccess(partial)).isTrue();
        assertThat(DefaultSecurityConfig.hasSensitiveSecurityAccess(partial)).isFalse();
        assertThat(DefaultSecurityConfig.hasTotpSensitiveAccess(partial)).isFalse();
    }

    @Test
    void sensitiveSecurityShouldRequireFullyAuthenticatedToken() {
        MultiFactorAuthenticationToken full = new MultiFactorAuthenticationToken(
                "admin",
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                Set.of(
                        MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD,
                        MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP
                ),
                List.of()
        );

        assertThat(DefaultSecurityConfig.hasChallengeFlowAccess(full)).isTrue();
        assertThat(DefaultSecurityConfig.hasSensitiveSecurityAccess(full)).isTrue();
        assertThat(DefaultSecurityConfig.hasTotpSensitiveAccess(full)).isTrue();
    }

    @Test
    void totpSensitiveSecurityShouldRejectPasswordOnlyFullAuthentication() {
        MultiFactorAuthenticationToken passwordOnly = new MultiFactorAuthenticationToken(
                "admin",
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                Set.of(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD),
                List.of()
        );

        assertThat(DefaultSecurityConfig.hasSensitiveSecurityAccess(passwordOnly)).isTrue();
        assertThat(DefaultSecurityConfig.hasTotpSensitiveAccess(passwordOnly)).isFalse();
    }

    @Test
    void anonymousShouldNotAccessSecurityEndpoints() {
        assertThat(DefaultSecurityConfig.hasChallengeFlowAccess(null)).isFalse();
        assertThat(DefaultSecurityConfig.hasSensitiveSecurityAccess(null)).isFalse();
        assertThat(DefaultSecurityConfig.hasTotpSensitiveAccess(null)).isFalse();
    }

    @Test
    void schedulingAdminShouldRequireFullAuthenticationAndRoleAdmin() {
        MultiFactorAuthenticationToken admin = new MultiFactorAuthenticationToken(
                "admin",
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                Set.of(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        MultiFactorAuthenticationToken user = new MultiFactorAuthenticationToken(
                "user",
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                Set.of(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        MultiFactorAuthenticationToken partialAdmin = MultiFactorAuthenticationToken.partiallyAuthenticated(
                "admin",
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                Set.of(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        assertThat(DefaultSecurityConfig.hasSchedulingAdminAccess(admin)).isTrue();
        assertThat(DefaultSecurityConfig.hasSchedulingAdminAccess(user)).isFalse();
        assertThat(DefaultSecurityConfig.hasSchedulingAdminAccess(partialAdmin)).isFalse();
    }
}
