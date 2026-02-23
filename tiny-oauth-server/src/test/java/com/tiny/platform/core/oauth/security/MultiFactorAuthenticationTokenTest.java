package com.tiny.platform.core.oauth.security;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

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
}

