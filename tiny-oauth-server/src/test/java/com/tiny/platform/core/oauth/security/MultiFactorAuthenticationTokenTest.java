package com.tiny.platform.core.oauth.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MultiFactorAuthenticationTokenTest {

    @Test
    void promoteWithoutAdditionalFactorShouldKeepPasswordOnly() {
        MultiFactorAuthenticationToken token = new MultiFactorAuthenticationToken(
                "admin",
                "secret",
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD
        );

        MultiFactorAuthenticationToken promoted = token.promoteToFullyAuthenticated(List.of());

        assertThat(promoted.isAuthenticated()).isTrue();
        assertThat(promoted.getCompletedFactors()).isEqualTo(
                EnumSet.of(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD)
        );
    }

    @Test
    void promoteWithAdditionalTotpFactorShouldIncludeTotp() {
        MultiFactorAuthenticationToken token = new MultiFactorAuthenticationToken(
                "admin",
                "secret",
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD
        );

        MultiFactorAuthenticationToken promoted = token.promoteToFullyAuthenticatedWithFactor(
                MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP,
                List.of()
        );

        assertThat(promoted.isAuthenticated()).isTrue();
        assertThat(promoted.getCompletedFactors()).containsExactlyInAnyOrder(
                MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD,
                MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP
        );
    }

    @Test
    void factorAuthoritiesShouldDriveAuthenticationTypeWhenCompletedFactorsAreEmpty() {
        MultiFactorAuthenticationToken token = new MultiFactorAuthenticationToken(
                "admin",
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                Set.of(),
                List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority(AuthenticationFactorAuthorities.FACTOR_AUTHORITY_PREFIX + "TOTP")
                )
        );

        assertThat(token.getCompletedFactors()).isEmpty();
        assertThat(token.getAuthenticationType()).isEqualTo("TOTP");
        assertThat(token.hasCompletedFactor(MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP)).isTrue();
    }
}
