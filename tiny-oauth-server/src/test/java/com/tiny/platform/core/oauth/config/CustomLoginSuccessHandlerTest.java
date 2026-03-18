package com.tiny.platform.core.oauth.config;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.AuthUserResolutionService;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationSessionManager;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken;
import com.tiny.platform.core.oauth.service.AuthenticationAuditService;
import com.tiny.platform.core.oauth.service.SecurityService;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CustomLoginSuccessHandlerTest {

    private final SecurityService securityService = mock(SecurityService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuthUserResolutionService authUserResolutionService = mock(AuthUserResolutionService.class);
    private final MultiFactorAuthenticationSessionManager sessionManager = mock(MultiFactorAuthenticationSessionManager.class);
    private final AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void modeNoneShouldNotPromoteTotpFactor() throws Exception {
        FrontendProperties frontendProperties = frontendProperties();
        CustomLoginSuccessHandler handler = new CustomLoginSuccessHandler(
                securityService,
                userRepository,
                frontendProperties,
                sessionManager,
                authUserResolutionService,
                auditService
        );

        User user = user();
        when(authUserResolutionService.resolveUserRecordInActiveTenant("admin", 1L)).thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
                "totpBound", true,
                "totpActivated", true,
                "disableMfa", true,
                "skipMfaRemind", false,
                "forceMfa", false,
                "requireTotp", false
        ));

        MultiFactorAuthenticationToken authentication = new MultiFactorAuthenticationToken(
                "admin",
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD,
                List.of()
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        TenantContext.setActiveTenantId(1L);
        handler.onAuthenticationSuccess(request, response, authentication);

        verify(sessionManager, never()).promoteToFullyAuthenticated(any(User.class), any(), any());
        verify(userRepository).save(user);
        verify(auditService).recordLoginSuccess(eq("admin"), eq(1L), eq("LOCAL"), eq("PASSWORD"), eq(request));
        assertThat(user.getLastLoginAt()).isNotNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/");
    }

    @Test
    void modeOptionalShouldRedirectToTotpVerifyWhenPasswordOnly() throws Exception {
        FrontendProperties frontendProperties = frontendProperties();
        CustomLoginSuccessHandler handler = new CustomLoginSuccessHandler(
                securityService,
                userRepository,
                frontendProperties,
                sessionManager,
                authUserResolutionService,
                auditService
        );

        User user = user();
        when(authUserResolutionService.resolveUserRecordInActiveTenant("admin", 1L)).thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
                "totpBound", true,
                "totpActivated", true,
                "disableMfa", false,
                "skipMfaRemind", false,
                "forceMfa", false,
                "requireTotp", true
        ));

        MultiFactorAuthenticationToken authentication = MultiFactorAuthenticationToken.partiallyAuthenticated(
                "admin",
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                java.util.Set.of(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD),
                List.of()
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        TenantContext.setActiveTenantId(1L);
        handler.onAuthenticationSuccess(request, response, authentication);

        verify(sessionManager, never()).promoteToFullyAuthenticated(any(User.class), any(), any());
        verify(userRepository, never()).save(any(User.class));
        verify(auditService, never()).recordLoginSuccess(any(), any(), any(), any(), any());
        assertThat(response.getRedirectedUrl()).startsWith("http://localhost:5173/self/security/totp-verify");
    }

    @Test
    void modeOptionalShouldKeepTotpInCompletedFactorsOnlyAfterRealTotp() throws Exception {
        FrontendProperties frontendProperties = frontendProperties();
        CustomLoginSuccessHandler handler = new CustomLoginSuccessHandler(
                securityService,
                userRepository,
                frontendProperties,
                sessionManager,
                authUserResolutionService,
                auditService
        );

        User user = user();
        when(authUserResolutionService.resolveUserRecordInActiveTenant("admin", 1L)).thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
                "totpBound", true,
                "totpActivated", true,
                "disableMfa", false,
                "skipMfaRemind", false,
                "forceMfa", false,
                "requireTotp", true
        ));

        MultiFactorAuthenticationToken authentication = new MultiFactorAuthenticationToken(
                "admin",
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                java.util.Set.of(
                        MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD,
                        MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP
                ),
                List.of()
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        TenantContext.setActiveTenantId(1L);
        handler.onAuthenticationSuccess(request, response, authentication);

        verify(sessionManager, times(1)).promoteToFullyAuthenticated(eq(user), eq(request), eq(response));
        verify(userRepository).save(user);
        verify(auditService).recordLoginSuccess(eq("admin"), eq(1L), eq("LOCAL"), eq("MFA"), eq(request));
        assertThat(user.getLastLoginAt()).isNotNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/");
    }

    @Test
    void shouldResolveActiveTenantIdFromSecurityUserDetailsWithoutTenantContext() throws Exception {
        FrontendProperties frontendProperties = frontendProperties();
        CustomLoginSuccessHandler handler = new CustomLoginSuccessHandler(
                securityService,
                userRepository,
                frontendProperties,
                sessionManager,
                authUserResolutionService,
                auditService
        );

        User user = user();
        when(authUserResolutionService.resolveUserRecordInActiveTenant("admin", 9L)).thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
                "totpBound", true,
                "totpActivated", true,
                "disableMfa", true,
                "skipMfaRemind", false,
                "forceMfa", false,
                "requireTotp", false
        ));

        MultiFactorAuthenticationToken authentication = new MultiFactorAuthenticationToken(
                "admin",
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD,
                List.of()
        );
        authentication.setDetails(new SecurityUser(user, "", 9L, Set.of()));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        TenantContext.clear();
        handler.onAuthenticationSuccess(request, response, authentication);

        verify(authUserResolutionService).resolveUserRecordInActiveTenant("admin", 9L);
        verify(auditService).recordLoginSuccess(eq("admin"), eq(1L), eq("LOCAL"), eq("PASSWORD"), eq(request));
        assertThat(request.getSession(false)).isNotNull();
        assertThat(request.getSession(false).getAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY)).isEqualTo(9L);
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/");
    }

    @Test
    void shouldPreserveIssuerAuthorizeRedirectWhenTotpRequired() throws Exception {
        FrontendProperties frontendProperties = frontendProperties();
        CustomLoginSuccessHandler handler = new CustomLoginSuccessHandler(
                securityService,
                userRepository,
                frontendProperties,
                sessionManager,
                authUserResolutionService,
                auditService
        );

        User user = user();
        when(authUserResolutionService.resolveUserRecordInActiveTenant("admin", 1L)).thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
                "totpBound", true,
                "totpActivated", true,
                "disableMfa", false,
                "skipMfaRemind", false,
                "forceMfa", false,
                "requireTotp", true
        ));

        MultiFactorAuthenticationToken authentication = MultiFactorAuthenticationToken.partiallyAuthenticated(
                "admin",
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                java.util.Set.of(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD),
                List.of()
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setParameter("redirect", "/default/oauth2/authorize?client_id=vue-client");
        MockHttpServletResponse response = new MockHttpServletResponse();

        TenantContext.setActiveTenantId(1L);
        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).startsWith("http://localhost:5173/self/security/totp-verify");
        assertThat(response.getRedirectedUrl())
                .contains("redirect=%2Fdefault%2Foauth2%2Fauthorize%3Fclient_id%3Dvue-client");
    }

    @Test
    void shouldFallbackToRootWhenRedirectParameterTargetsExternalSite() throws Exception {
        FrontendProperties frontendProperties = frontendProperties();
        CustomLoginSuccessHandler handler = new CustomLoginSuccessHandler(
                securityService,
                userRepository,
                frontendProperties,
                sessionManager,
                authUserResolutionService,
                auditService
        );

        User user = user();
        when(authUserResolutionService.resolveUserRecordInActiveTenant("admin", 1L)).thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
                "totpBound", true,
                "totpActivated", true,
                "disableMfa", true,
                "skipMfaRemind", false,
                "forceMfa", false,
                "requireTotp", false
        ));

        MultiFactorAuthenticationToken authentication = new MultiFactorAuthenticationToken(
                "admin",
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD,
                List.of()
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(80);
        request.setParameter("redirect", "https://evil.com/callback");
        MockHttpServletResponse response = new MockHttpServletResponse();

        TenantContext.setActiveTenantId(1L);
        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/");
    }

    @Test
    void shouldPreserveSavedRequestAuthorizePathAndNotDirectlyRedirectToClientCallback() throws Exception {
        FrontendProperties frontendProperties = frontendProperties();
        CustomLoginSuccessHandler handler = new CustomLoginSuccessHandler(
                securityService,
                userRepository,
                frontendProperties,
                sessionManager,
                authUserResolutionService,
                auditService
        );

        User user = user();
        when(authUserResolutionService.resolveUserRecordInActiveTenant("admin", 1L)).thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
                "totpBound", true,
                "totpActivated", true,
                "disableMfa", false,
                "skipMfaRemind", false,
                "forceMfa", false,
                "requireTotp", true
        ));

        MultiFactorAuthenticationToken authentication = MultiFactorAuthenticationToken.partiallyAuthenticated(
                "admin",
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                java.util.Set.of(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD),
                List.of()
        );

        MockHttpServletRequest authorizeRequest = new MockHttpServletRequest("GET", "/oauth2/authorize");
        authorizeRequest.setScheme("http");
        authorizeRequest.setServerName("localhost");
        authorizeRequest.setServerPort(80);
        authorizeRequest.setQueryString("client_id=vue-client&redirect_uri=https://client.example.com/callback");
        authorizeRequest.setParameter("client_id", "vue-client");
        authorizeRequest.setParameter("redirect_uri", "https://client.example.com/callback");
        MockHttpServletResponse authorizeResponse = new MockHttpServletResponse();
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.saveRequest(authorizeRequest, authorizeResponse);

        MockHttpSession session = (MockHttpSession) authorizeRequest.getSession(false);
        MockHttpServletRequest loginRequest = new MockHttpServletRequest("POST", "/login");
        loginRequest.setScheme("http");
        loginRequest.setServerName("localhost");
        loginRequest.setServerPort(80);
        loginRequest.setSession(session);
        MockHttpServletResponse loginResponse = new MockHttpServletResponse();

        TenantContext.setActiveTenantId(1L);
        handler.onAuthenticationSuccess(loginRequest, loginResponse, authentication);

        assertThat(loginResponse.getRedirectedUrl()).startsWith("http://localhost:5173/self/security/totp-verify");
        assertThat(loginResponse.getRedirectedUrl())
                .contains("redirect=%2Foauth2%2Fauthorize%3Fclient_id%3Dvue-client%26redirect_uri%3Dhttps%3A%2F%2Fclient.example.com%2Fcallback");
        assertThat(loginResponse.getRedirectedUrl()).doesNotContain("redirect=https://client.example.com/callback");
    }

    @Test
    void should_resolve_membership_user_and_freeze_active_tenant() throws Exception {
        FrontendProperties frontendProperties = frontendProperties();
        CustomLoginSuccessHandler handler = new CustomLoginSuccessHandler(
                securityService,
                userRepository,
                frontendProperties,
                sessionManager,
                authUserResolutionService,
                auditService
        );

        User user = user();
        user.setTenantId(88L);
        when(authUserResolutionService.resolveUserRecordInActiveTenant("admin", 1L)).thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
                "totpBound", true,
                "totpActivated", true,
                "disableMfa", true,
                "skipMfaRemind", false,
                "forceMfa", false,
                "requireTotp", false
        ));

        MultiFactorAuthenticationToken authentication = new MultiFactorAuthenticationToken(
                "admin",
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD,
                List.of()
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        TenantContext.setActiveTenantId(1L);
        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(request.getSession(false)).isNotNull();
        assertThat(request.getSession(false).getAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY)).isEqualTo(1L);
        verify(authUserResolutionService).resolveUserRecordInActiveTenant("admin", 1L);
    }

    @Test
    void should_resolve_user_from_authentication_active_tenant_when_context_missing() throws Exception {
        FrontendProperties frontendProperties = frontendProperties();
        CustomLoginSuccessHandler handler = new CustomLoginSuccessHandler(
                securityService,
                userRepository,
                frontendProperties,
                sessionManager,
                authUserResolutionService,
                auditService
        );

        User user = user();
        user.setTenantId(88L);
        when(authUserResolutionService.resolveUserRecordInActiveTenant("admin", 7L)).thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
                "totpBound", true,
                "totpActivated", true,
                "disableMfa", true,
                "skipMfaRemind", false,
                "forceMfa", false,
                "requireTotp", false
        ));

        MultiFactorAuthenticationToken authentication = new MultiFactorAuthenticationToken(
                "admin",
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD,
                List.of()
        );
        authentication.setDetails(new SecurityUser(1L, 7L, "admin", "", List.of(), true, true, true, true));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        TenantContext.clear();
        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(request.getSession(false)).isNotNull();
        assertThat(request.getSession(false).getAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY)).isEqualTo(7L);
        verify(authUserResolutionService).resolveUserRecordInActiveTenant("admin", 7L);
    }

    private FrontendProperties frontendProperties() {
        FrontendProperties frontendProperties = new FrontendProperties();
        frontendProperties.setLoginUrl("redirect:http://localhost:5173/login");
        frontendProperties.setTotpBindUrl("redirect:http://localhost:5173/self/security/totp-bind");
        frontendProperties.setTotpVerifyUrl("redirect:http://localhost:5173/self/security/totp-verify");
        return frontendProperties;
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        user.setTenantId(1L);
        user.setUsername("admin");
        return user;
    }
}
