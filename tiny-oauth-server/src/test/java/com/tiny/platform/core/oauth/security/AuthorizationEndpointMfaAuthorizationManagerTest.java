package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.service.SecurityService;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.FactorAuthorizationDecision;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthorizationEndpointMfaAuthorizationManagerTest {

    private final AuthUserResolutionService authUserResolutionService = mock(AuthUserResolutionService.class);

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldAbstainWhenAuthenticationMissing() {
        AuthorizationEndpointMfaAuthorizationManager manager =
                newManager(mock(SecurityService.class));

        AuthorizationResult result = manager.authorize(
                () -> null,
                new RequestAuthorizationContext(new MockHttpServletRequest("GET", "/oauth2/authorize"))
        );

        assertThat(result).isNull();
    }

    @Test
    void shouldAbstainWhenAuthenticationIsAnonymous() {
        AuthorizationEndpointMfaAuthorizationManager manager =
                newManager(mock(SecurityService.class));

        AuthorizationResult result = manager.authorize(
                () -> new AnonymousAuthenticationToken(
                        "test-key",
                        "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
                ),
                new RequestAuthorizationContext(new MockHttpServletRequest("GET", "/oauth2/authorize"))
        );

        assertThat(result).isNull();
    }

    @Test
    void shouldAbstainWhenTotpNotRequired() {
        SecurityService securityService = mock(SecurityService.class);
        AuthorizationEndpointMfaAuthorizationManager manager = newManager(securityService);
        User user = user(1L, "alice");
        TenantContext.setActiveTenantId(1L);

        when(authUserResolutionService.resolveUserRecordInActiveTenant("alice", 1L)).thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
                "totpBound", true,
                "totpActivated", true,
                "forceMfa", false,
                "requireTotp", false
        ));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        AuthorizationResult result = manager.authorize(
                () -> org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                        "alice",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                ),
                new RequestAuthorizationContext(request)
        );

        assertThat(result).isNull();
        assertThat(request.getAttribute(AuthorizationEndpointMfaAuthorizationManager.BIND_REQUIRED_ATTRIBUTE))
                .isEqualTo(false);
    }

    @Test
    void shouldDenyWithTotpFactorWhenBindRequired() {
        SecurityService securityService = mock(SecurityService.class);
        AuthorizationEndpointMfaAuthorizationManager manager = newManager(securityService);
        User user = user(2L, "bob");
        TenantContext.setActiveTenantId(1L);

        when(authUserResolutionService.resolveUserRecordInActiveTenant("bob", 1L)).thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
                "totpBound", false,
                "totpActivated", false,
                "forceMfa", true,
                "requireTotp", true
        ));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        AuthorizationResult result = manager.authorize(
                () -> org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                        "bob",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                ),
                new RequestAuthorizationContext(request)
        );

        assertThat(result).isInstanceOf(FactorAuthorizationDecision.class);
        assertThat(result.isGranted()).isFalse();
        assertThat(request.getAttribute(AuthorizationEndpointMfaAuthorizationManager.BIND_REQUIRED_ATTRIBUTE))
                .isEqualTo(true);
        assertThat(((FactorAuthorizationDecision) result).getFactorErrors())
                .extracting(error -> error.getRequiredFactor().getAuthority())
                .containsExactly(AuthenticationFactorAuthorities.toAuthority(
                        MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP
                ));
    }

    @Test
    void shouldDenyWhenPasswordOnlyAuthenticationStillNeedsTotp() {
        SecurityService securityService = mock(SecurityService.class);
        AuthorizationEndpointMfaAuthorizationManager manager = newManager(securityService);
        User user = user(3L, "partial");

        SecurityUser securityUser = new SecurityUser(
                3L, 5L, "partial", "",
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                true, true, true, true
        );
        MultiFactorAuthenticationToken challengeToken = new MultiFactorAuthenticationToken(
                "partial",
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                Set.of(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        challengeToken.setDetails(securityUser);

        when(authUserResolutionService.resolveUserRecordInActiveTenant("partial", 5L)).thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
                "totpBound", true,
                "totpActivated", true,
                "forceMfa", false,
                "requireTotp", true
        ));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        AuthorizationResult result = manager.authorize(() -> challengeToken, new RequestAuthorizationContext(request));

        assertThat(result).isInstanceOf(FactorAuthorizationDecision.class);
        assertThat(result.isGranted()).isFalse();
        assertThat(request.getAttribute(AuthorizationEndpointMfaAuthorizationManager.BIND_REQUIRED_ATTRIBUTE))
                .isEqualTo(false);
    }

    @Test
    void shouldGrantWhenTotpAlreadyCompleted() {
        SecurityService securityService = mock(SecurityService.class);
        AuthorizationEndpointMfaAuthorizationManager manager = newManager(securityService);
        User user = user(4L, "carol");

        SecurityUser securityUser = new SecurityUser(
                4L, 9L, "carol", "",
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                true, true, true, true
        );
        MultiFactorAuthenticationToken token = new MultiFactorAuthenticationToken(
                "carol",
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                Set.of(
                        MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD,
                        MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP
                ),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        token.setDetails(securityUser);

        when(authUserResolutionService.resolveUserRecordInActiveTenant("carol", 9L)).thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
                "totpBound", true,
                "totpActivated", true,
                "forceMfa", false,
                "requireTotp", true
        ));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/tenant1/oauth2/authorize");
        AuthorizationResult result = manager.authorize(() -> token, new RequestAuthorizationContext(request));

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isTrue();
        assertThat(request.getAttribute(AuthorizationEndpointMfaAuthorizationManager.BIND_REQUIRED_ATTRIBUTE))
                .isEqualTo(false);
    }

    private AuthorizationEndpointMfaAuthorizationManager newManager(SecurityService securityService) {
        return new AuthorizationEndpointMfaAuthorizationManager(securityService, authUserResolutionService);
    }

    private static User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
