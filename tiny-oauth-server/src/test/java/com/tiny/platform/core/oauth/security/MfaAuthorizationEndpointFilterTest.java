package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.config.FrontendProperties;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.service.SecurityService;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MfaAuthorizationEndpointFilterTest {

    private final AuthUserResolutionService authUserResolutionService = mock(AuthUserResolutionService.class);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void should_skip_non_authorization_path_and_allow_when_auth_missing() throws Exception {
        MfaAuthorizationEndpointFilter filter = newFilter(mock(SecurityService.class));

        MockHttpServletRequest nonAuthorize = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse nonAuthorizeResponse = new MockHttpServletResponse();
        MockFilterChain nonAuthorizeChain = new MockFilterChain();
        filter.doFilter(nonAuthorize, nonAuthorizeResponse, nonAuthorizeChain);
        assertThat(nonAuthorizeChain.getRequest()).isNotNull();

        MockHttpServletRequest authorize = new MockHttpServletRequest("GET", "/oauth2/authorize");
        MockHttpServletResponse authorizeResponse = new MockHttpServletResponse();
        MockFilterChain authorizeChain = new MockFilterChain();
        SecurityContextHolder.clearContext();
        filter.doFilter(authorize, authorizeResponse, authorizeChain);
        assertThat(authorizeChain.getRequest()).isNotNull();
    }

    @Test
    void should_redirect_to_bind_or_verify_and_allow_when_totp_not_required() throws Exception {
        SecurityService securityService = mock(SecurityService.class);
        MfaAuthorizationEndpointFilter filter = newFilter(securityService);
        User user = user(1L, "alice");
        TenantContext.setActiveTenantId(1L);

        UsernamePasswordAuthenticationToken auth = UsernamePasswordAuthenticationToken.authenticated("alice", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(authUserResolutionService.resolveUserRecordInActiveTenant("alice", 1L)).thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
            "totpBound", false,
            "totpActivated", false,
            "forceMfa", true,
            "requireTotp", true
        ));

        MockHttpServletRequest bindRequest = new MockHttpServletRequest("GET", "/oauth2/authorize");
        bindRequest.setQueryString("client_id=a");
        MockHttpServletResponse bindResponse = new MockHttpServletResponse();
        filter.doFilter(bindRequest, bindResponse, new MockFilterChain());
        assertThat(bindResponse.getRedirectedUrl()).contains("http://localhost:5173/totp-bind");

        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
            "totpBound", true,
            "totpActivated", true,
            "forceMfa", false,
            "requireTotp", false
        ));
        MockHttpServletResponse passResponse = new MockHttpServletResponse();
        MockFilterChain passChain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/oauth2/authorize"), passResponse, passChain);
        assertThat(passChain.getRequest()).isNotNull();

        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
            "totpBound", true,
            "totpActivated", true,
            "forceMfa", false,
            "requireTotp", true
        ));
        MockHttpServletResponse verifyResponse = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/oauth2/authorize"), verifyResponse, new MockFilterChain());
        assertThat(verifyResponse.getRedirectedUrl()).contains("http://localhost:5173/totp-verify");
    }

    @Test
    void should_allow_when_totp_completed_and_handle_user_resolution_fallbacks() throws Exception {
        SecurityService securityService = mock(SecurityService.class);
        MfaAuthorizationEndpointFilter filter = newFilter(securityService);
        User user = user(2L, "bob");

        SecurityUser securityUser = new SecurityUser(
            2L, 9L, "bob", "",
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            true, true, true, true
        );
        MultiFactorAuthenticationToken token = new MultiFactorAuthenticationToken(
            "bob",
            null,
            MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
            Set.of(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD,
                MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP),
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        token.setDetails(securityUser);
        SecurityContextHolder.getContext().setAuthentication(token);

        when(authUserResolutionService.resolveUserRecordInActiveTenant("bob", 9L)).thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
            "totpBound", true,
            "totpActivated", true,
            "forceMfa", false,
            "requireTotp", true
        ));

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/tenant1/oauth2/authorize"), new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();

        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("", null, List.of())
        );
        MockFilterChain blankUserChain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/oauth2/authorize"), new MockHttpServletResponse(), blankUserChain);
        assertThat(blankUserChain.getRequest()).isNotNull();
    }

    @Test
    void should_resolve_active_tenant_from_authenticated_principal() throws Exception {
        SecurityService securityService = mock(SecurityService.class);
        MfaAuthorizationEndpointFilter filter = newFilter(securityService);
        User user = user(4L, "principal-user");

        SecurityUser principal = new SecurityUser(
                4L, 8L, "principal-user", "",
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                true, true, true, true
        );
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(authUserResolutionService.resolveUserRecordInActiveTenant("principal-user", 8L)).thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
                "totpBound", true,
                "totpActivated", true,
                "forceMfa", false,
                "requireTotp", false
        ));

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/oauth2/authorize"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void should_redirect_when_partialMfaTokenStillNeedsTotp() throws Exception {
        SecurityService securityService = mock(SecurityService.class);
        MfaAuthorizationEndpointFilter filter = newFilter(securityService);
        User user = user(3L, "partial");

        SecurityUser securityUser = new SecurityUser(
                3L, 5L, "partial", "",
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                true, true, true, true
        );
        MultiFactorAuthenticationToken partial = MultiFactorAuthenticationToken.partiallyAuthenticated(
                "partial",
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                Set.of(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        partial.setDetails(securityUser);
        SecurityContextHolder.getContext().setAuthentication(partial);

        when(authUserResolutionService.resolveUserRecordInActiveTenant("partial", 5L)).thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
                "totpBound", true,
                "totpActivated", true,
                "forceMfa", false,
                "requireTotp", true
        ));

        MockHttpServletResponse verifyResponse = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/oauth2/authorize"), verifyResponse, new MockFilterChain());

        assertThat(verifyResponse.getRedirectedUrl()).contains("http://localhost:5173/totp-verify");
    }

    private MfaAuthorizationEndpointFilter newFilter(SecurityService securityService) {
        FrontendProperties frontendProperties = new FrontendProperties();
        frontendProperties.setTotpBindUrl("redirect:http://localhost:5173/totp-bind");
        frontendProperties.setTotpVerifyUrl("redirect:http://localhost:5173/totp-verify");
        return new MfaAuthorizationEndpointFilter(securityService, authUserResolutionService, frontendProperties);
    }

    private static User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
