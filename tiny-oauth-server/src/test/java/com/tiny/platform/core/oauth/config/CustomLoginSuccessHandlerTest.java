package com.tiny.platform.core.oauth.config;

import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationSessionManager;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken;
import com.tiny.platform.core.oauth.service.AuthenticationAuditService;
import com.tiny.platform.core.oauth.service.SecurityService;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CustomLoginSuccessHandlerTest {

    private final SecurityService securityService = mock(SecurityService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
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
                auditService
        );

        User user = user();
        when(userRepository.findUserByUsernameAndTenantId("admin", 1L)).thenReturn(Optional.of(user));
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

        TenantContext.setTenantId(1L);
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
                auditService
        );

        User user = user();
        when(userRepository.findUserByUsernameAndTenantId("admin", 1L)).thenReturn(Optional.of(user));
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
                MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD,
                List.of()
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        TenantContext.setTenantId(1L);
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
                auditService
        );

        User user = user();
        when(userRepository.findUserByUsernameAndTenantId("admin", 1L)).thenReturn(Optional.of(user));
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

        TenantContext.setTenantId(1L);
        handler.onAuthenticationSuccess(request, response, authentication);

        verify(sessionManager, times(1)).promoteToFullyAuthenticated(eq(user), eq(request), eq(response));
        verify(userRepository).save(user);
        verify(auditService).recordLoginSuccess(eq("admin"), eq(1L), eq("LOCAL"), eq("MFA"), eq(request));
        assertThat(user.getLastLoginAt()).isNotNull();
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
                auditService
        );

        User user = user();
        when(userRepository.findUserByUsernameAndTenantId("admin", 1L)).thenReturn(Optional.of(user));
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
                MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD,
                List.of()
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setParameter("redirect", "/default/oauth2/authorize?client_id=vue-client");
        MockHttpServletResponse response = new MockHttpServletResponse();

        TenantContext.setTenantId(1L);
        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).startsWith("http://localhost:5173/self/security/totp-verify");
        assertThat(response.getRedirectedUrl())
                .contains("redirect=%2Fdefault%2Foauth2%2Fauthorize%3Fclient_id%3Dvue-client");
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
