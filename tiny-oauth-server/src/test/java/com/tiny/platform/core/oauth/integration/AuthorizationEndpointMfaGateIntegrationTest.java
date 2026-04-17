package com.tiny.platform.core.oauth.integration;

import com.tiny.platform.core.oauth.config.FrontendProperties;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.AuthUserResolutionService;
import com.tiny.platform.core.oauth.security.AuthorizationEndpointMfaAuthorizationManager;
import com.tiny.platform.core.oauth.security.AuthorizationEndpointMfaEntryPoint;
import com.tiny.platform.core.oauth.security.AuthenticationFactorAuthorities;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken;
import com.tiny.platform.core.oauth.service.SecurityService;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.core.oauth.tenant.TenantContextFilter;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.DelegatingMissingAuthorityAccessDeniedHandler;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.RequestContextFilter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthorizationEndpointMfaGateIntegrationTest {

    @Mock
    private SecurityService securityService;

    @Mock
    private AuthUserResolutionService authUserResolutionService;

    @Mock
    private TenantRepository tenantRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FrontendProperties frontendProperties = new FrontendProperties();
        frontendProperties.setTotpBindUrl("redirect:http://localhost:5173/self/security/totp-bind");
        frontendProperties.setTotpVerifyUrl("redirect:http://localhost:5173/self/security/totp-verify");

        AuthorizationEndpointMfaAuthorizationManager authorizationManager =
                new AuthorizationEndpointMfaAuthorizationManager(securityService, authUserResolutionService);
        AuthorizationEndpointMfaEntryPoint entryPoint =
                new AuthorizationEndpointMfaEntryPoint(frontendProperties);

        AuthorizationFilter authorizationFilter = new AuthorizationFilter(
                (authentication, request) -> authorizationManager.authorize(
                        authentication,
                        new org.springframework.security.web.access.intercept.RequestAuthorizationContext(request)
                )
        );

        ExceptionTranslationFilter exceptionTranslationFilter =
                new ExceptionTranslationFilter(new LoginUrlAuthenticationEntryPoint("/login"));
        exceptionTranslationFilter.setAccessDeniedHandler(
                DelegatingMissingAuthorityAccessDeniedHandler.builder()
                        .addEntryPointFor(
                                entryPoint,
                                AuthenticationFactorAuthorities.toAuthority(
                                        MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP
                                )
                        )
                        .build()
        );

        HttpSessionSecurityContextRepository securityContextRepository =
                new HttpSessionSecurityContextRepository();

        mockMvc = MockMvcBuilders.standaloneSetup(new AuthorizationEndpointStubController())
                .addFilters(
                        new RequestContextFilter(),
                        new SecurityContextPersistenceFilter(securityContextRepository),
                        new TenantContextFilter(tenantRepository),
                        exceptionTranslationFilter,
                        authorizationFilter
                )
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void forceMfaWithoutBoundTotpShouldRedirectToBindPage() throws Exception {
        User user = user(1L, "alice");
        when(authUserResolutionService.resolveUserRecordInActiveTenant("alice", 1L))
                .thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
                "totpBound", false,
                "totpActivated", false,
                "forceMfa", true,
                "requireTotp", true
        ));
        when(tenantRepository.findLoginBlockedLifecycleStatus(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/oauth2/authorize")
                        .queryParam("client_id", "vue-client")
                        .queryParam("response_type", "code")
                        .accept(MediaType.TEXT_HTML)
                        .session(sessionWithAuthentication(
                                tenantAuthentication(
                                        1L,
                                        "alice",
                                        Set.of(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD)
                                ),
                                1L
                        )))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/self/security/totp-bind")))
                .andExpect(header().string(
                        "Location",
                        containsString("redirect=%2Foauth2%2Fauthorize%3Fclient_id%3Dvue-client%26response_type%3Dcode")
                ));
    }

    @Test
    void passwordOnlyChallengeTokenShouldRedirectToVerifyPage() throws Exception {
        User user = user(2L, "bob");
        when(authUserResolutionService.resolveUserRecordInActiveTenant("bob", 1L))
                .thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
                "totpBound", true,
                "totpActivated", true,
                "forceMfa", false,
                "requireTotp", true
        ));
        when(tenantRepository.findLoginBlockedLifecycleStatus(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/oauth2/authorize")
                        .queryParam("client_id", "vue-client")
                        .queryParam("response_type", "code")
                        .accept(MediaType.TEXT_HTML)
                        .session(sessionWithAuthentication(
                                tenantAuthentication(
                                        1L,
                                        "bob",
                                        Set.of(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD)
                                ),
                                1L
                        )))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/self/security/totp-verify")))
                .andExpect(header().string(
                        "Location",
                        containsString("redirect=%2Foauth2%2Fauthorize%3Fclient_id%3Dvue-client%26response_type%3Dcode")
                ));
    }

    @Test
    void completedPasswordAndTotpFactorsShouldPassAuthorizationEndpoint() throws Exception {
        User user = user(3L, "carol");
        when(authUserResolutionService.resolveUserRecordInActiveTenant("carol", 1L))
                .thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
                "totpBound", true,
                "totpActivated", true,
                "forceMfa", false,
                "requireTotp", true
        ));
        when(tenantRepository.findLoginBlockedLifecycleStatus(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/oauth2/authorize")
                        .queryParam("client_id", "vue-client")
                        .queryParam("response_type", "code")
                        .accept(MediaType.TEXT_HTML)
                        .session(sessionWithAuthentication(
                                tenantAuthentication(
                                        1L,
                                        "carol",
                                        Set.of(
                                                MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD,
                                                MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP
                                        )
                                ),
                                1L
                        )))
                .andExpect(status().isOk())
                .andExpect(content().string("authorized"));
    }

    private static MockHttpSession sessionWithAuthentication(Authentication authentication, long activeTenantId) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, context);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, activeTenantId);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, TenantContextContract.SCOPE_TYPE_TENANT);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, activeTenantId);
        return session;
    }

    private static Authentication tenantAuthentication(long userId,
                                                       String username,
                                                       Set<MultiFactorAuthenticationToken.AuthenticationFactorType> factors) {
        SecurityUser securityUser = new SecurityUser(
                userId,
                1L,
                username,
                "",
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")),
                true,
                true,
                true,
                true
        );
        MultiFactorAuthenticationToken token = new MultiFactorAuthenticationToken(
                username,
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                factors,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
        );
        token.setDetails(securityUser);
        return token;
    }

    private static User user(long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);
        return user;
    }

    @RestController
    private static final class AuthorizationEndpointStubController {

        @GetMapping("/oauth2/authorize")
        String authorize() {
            return "authorized";
        }
    }
}
