package com.tiny.platform.core.oauth.controller;

import com.tiny.platform.core.oauth.config.FrontendProperties;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.AuthUserResolutionService;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationSessionManager;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken;
import com.tiny.platform.core.oauth.service.AuthenticationAuditService;
import com.tiny.platform.core.oauth.service.SecurityService;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationMethodRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityControllerRedirectTest {

    private final AuthUserResolutionService authUserResolutionService = mock(AuthUserResolutionService.class);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void shouldFallbackToRootWhenSkipTotpReceivesExternalRedirect() {
        UserRepository userRepository = mock(UserRepository.class);
        SecurityService securityService = mock(SecurityService.class);
        SecurityController controller = new SecurityController(
                userRepository,
                securityService,
                mock(UserAuthenticationMethodRepository.class),
                frontendProperties(),
                mock(MultiFactorAuthenticationSessionManager.class),
                authUserResolutionService,
                mock(AuthenticationAuditService.class)
        );

        User user = user();
        when(authUserResolutionService.resolveUserRecordInActiveTenant("admin", 1L)).thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
                "disableMfa", false,
                "forceMfa", false,
                "totpActivated", false
        ));

        TenantContext.setActiveTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(partialAuthentication());

        MockHttpServletRequest request = request();
        String view = controller.skipTotp("https://evil.com/callback", request);

        verify(securityService).skipMfaRemind(user, true);
        assertThat(view).isEqualTo("redirect:http://localhost:5173/");
    }

    @Test
    void shouldRejectSkipTotpWhenUserAlreadyActivatedTotp() {
        UserRepository userRepository = mock(UserRepository.class);
        SecurityService securityService = mock(SecurityService.class);
        SecurityController controller = new SecurityController(
                userRepository,
                securityService,
                mock(UserAuthenticationMethodRepository.class),
                frontendProperties(),
                mock(MultiFactorAuthenticationSessionManager.class),
                authUserResolutionService,
                mock(AuthenticationAuditService.class)
        );

        User user = user();
        when(authUserResolutionService.resolveUserRecordInActiveTenant("admin", 1L)).thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
                "disableMfa", false,
                "forceMfa", false,
                "totpActivated", true
        ));

        TenantContext.setActiveTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(partialAuthentication());

        MockHttpServletRequest request = request();
        String view = controller.skipTotp("/oauth2/authorize?client_id=vue-client", request);

        verify(securityService, never()).skipMfaRemind(user, true);
        assertThat(view).isEqualTo("redirect:/self/security/totp-bind?redirect=%2Foauth2%2Fauthorize%3Fclient_id%3Dvue-client&error=%E5%BD%93%E5%89%8D%E7%8A%B6%E6%80%81%E4%B8%8D%E5%85%81%E8%AE%B8%E8%B7%B3%E8%BF%87%E4%BA%8C%E6%AC%A1%E9%AA%8C%E8%AF%81%E7%BB%91%E5%AE%9A%E6%8F%90%E9%86%92");
    }

    @Test
    void shouldPreserveInternalAuthorizePathWhenTotpCheckSucceeds() {
        UserRepository userRepository = mock(UserRepository.class);
        SecurityService securityService = mock(SecurityService.class);
        MultiFactorAuthenticationSessionManager sessionManager = mock(MultiFactorAuthenticationSessionManager.class);
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        SecurityController controller = new SecurityController(
                userRepository,
                securityService,
                mock(UserAuthenticationMethodRepository.class),
                frontendProperties(),
                sessionManager,
                authUserResolutionService,
                auditService
        );

        User user = user();
        when(authUserResolutionService.resolveUserRecordInActiveTenant("admin", 1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(securityService.checkTotp(user, "123456")).thenReturn(Map.of("success", true));
        when(sessionManager.tryPromoteToFullyAuthenticated(
                eq(user),
                any(MockHttpServletRequest.class),
                any(MockHttpServletResponse.class),
                eq(MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP)
        )).thenReturn(true);

        TenantContext.setActiveTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(partialAuthentication());

        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String redirect = "/oauth2/authorize?client_id=vue-client&redirect_uri=https://client.example.com/callback";

        String view = controller.checkTotpForm("123456", redirect, request, response);

        assertThat(view).isEqualTo("redirect:/oauth2/authorize?client_id=vue-client&redirect_uri=https://client.example.com/callback");
        verify(sessionManager).tryPromoteToFullyAuthenticated(
                eq(user),
                eq(request),
                eq(response),
                eq(MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP)
        );
        verify(auditService).recordLoginSuccess(eq("admin"), eq(1L), eq("LOCAL"), eq("MFA"), eq(request));
    }

    @Test
    void shouldRejectSkipMfaRemindJsonWhenUserAlreadyActivatedTotp() {
        UserRepository userRepository = mock(UserRepository.class);
        SecurityService securityService = mock(SecurityService.class);
        SecurityController controller = new SecurityController(
                userRepository,
                securityService,
                mock(UserAuthenticationMethodRepository.class),
                frontendProperties(),
                mock(MultiFactorAuthenticationSessionManager.class),
                authUserResolutionService,
                mock(AuthenticationAuditService.class)
        );

        User user = user();
        when(authUserResolutionService.resolveUserRecordInActiveTenant("admin", 1L)).thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
                "disableMfa", false,
                "forceMfa", false,
                "totpActivated", true
        ));

        TenantContext.setActiveTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(partialAuthentication());

        ResponseEntity<Map<String, Object>> response = controller.skipMfaRemind(Map.of("skipMfaRemind", true));

        verify(securityService, never()).skipMfaRemind(user, true);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("success", false);
        assertThat(response.getBody()).containsEntry("error", "当前状态不允许跳过二次验证绑定提醒");
    }

    @Test
    void shouldSanitizeRedirectWhenTotpCheckFails() {
        UserRepository userRepository = mock(UserRepository.class);
        SecurityService securityService = mock(SecurityService.class);
        MultiFactorAuthenticationSessionManager sessionManager = mock(MultiFactorAuthenticationSessionManager.class);
        SecurityController controller = new SecurityController(
                userRepository,
                securityService,
                mock(UserAuthenticationMethodRepository.class),
                frontendProperties(),
                sessionManager,
                authUserResolutionService,
                mock(AuthenticationAuditService.class)
        );

        User user = user();
        when(authUserResolutionService.resolveUserRecordInActiveTenant("admin", 1L)).thenReturn(Optional.of(user));
        when(securityService.checkTotp(user, "000000")).thenReturn(Map.of("success", false, "error", "验证码错误"));

        TenantContext.setActiveTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(partialAuthentication());

        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String view = controller.checkTotpForm("000000", "https://evil.com/callback", request, response);

        assertThat(view).isEqualTo("redirect:/self/security/totp-verify?redirect=%2F&error=%E9%AA%8C%E8%AF%81%E7%A0%81%E9%94%99%E8%AF%AF");
        verify(sessionManager, never()).tryPromoteToFullyAuthenticated(eq(user), eq(request), eq(response), eq(MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP));
    }

    @Test
    void shouldRedirectToLoginWhenSessionPromotionFailsAfterTotpCheck() {
        UserRepository userRepository = mock(UserRepository.class);
        SecurityService securityService = mock(SecurityService.class);
        MultiFactorAuthenticationSessionManager sessionManager = mock(MultiFactorAuthenticationSessionManager.class);
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        SecurityController controller = new SecurityController(
                userRepository,
                securityService,
                mock(UserAuthenticationMethodRepository.class),
                frontendProperties(),
                sessionManager,
                authUserResolutionService,
                auditService
        );

        User user = user();
        when(authUserResolutionService.resolveUserRecordInActiveTenant("admin", 1L)).thenReturn(Optional.of(user));
        when(securityService.checkTotp(user, "123456")).thenReturn(Map.of("success", true));
        when(sessionManager.tryPromoteToFullyAuthenticated(
                eq(user),
                any(MockHttpServletRequest.class),
                any(MockHttpServletResponse.class),
                eq(MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP)
        )).thenReturn(false);

        TenantContext.setActiveTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(partialAuthentication());

        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String view = controller.checkTotpForm("123456", "/oauth2/authorize?client_id=vue-client", request, response);

        assertThat(view).isEqualTo("redirect:/login?redirect=%2Foauth2%2Fauthorize%3Fclient_id%3Dvue-client&error=%E7%99%BB%E5%BD%95%E7%8A%B6%E6%80%81%E6%9B%B4%E6%96%B0%E5%A4%B1%E8%B4%A5%EF%BC%8C%E8%AF%B7%E9%87%8D%E6%96%B0%E7%99%BB%E5%BD%95");
        verify(auditService, never()).recordLoginSuccess(any(), any(), any(), any(), any());
        verify(userRepository, never()).save(any(User.class));
    }

    private static FrontendProperties frontendProperties() {
        FrontendProperties properties = new FrontendProperties();
        properties.setLoginUrl("redirect:http://localhost:5173/login");
        properties.setTotpBindUrl("redirect:http://localhost:5173/self/security/totp-bind");
        properties.setTotpVerifyUrl("redirect:http://localhost:5173/self/security/totp-verify");
        return properties;
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(80);
        return request;
    }

    private static MultiFactorAuthenticationToken partialAuthentication() {
        MultiFactorAuthenticationToken token = MultiFactorAuthenticationToken.partiallyAuthenticated(
                "admin",
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                Set.of(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        token.setDetails(new SecurityUser(
                1L,
                1L,
                "admin",
                "",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
                true,
                true,
                true,
                true
        ));
        return token;
    }

    private static User user() {
        User user = new User();
        user.setId(1L);
        user.setTenantId(1L);
        user.setUsername("admin");
        return user;
    }
}
