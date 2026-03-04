package com.tiny.platform.core.oauth.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.core.oauth.config.CustomLoginFailureHandler;
import com.tiny.platform.core.oauth.config.CustomLoginSuccessHandler;
import com.tiny.platform.core.oauth.config.CustomWebAuthenticationDetailsSource;
import com.tiny.platform.core.oauth.config.DefaultSecurityConfig;
import com.tiny.platform.core.oauth.config.FrontendProperties;
import com.tiny.platform.core.oauth.config.MfaProperties;
import com.tiny.platform.core.oauth.config.LoginProtectionProperties;
import com.tiny.platform.core.oauth.controller.CsrfController;
import com.tiny.platform.core.oauth.controller.SecurityController;
import com.tiny.platform.core.oauth.security.LoginFailurePolicy;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.MultiAuthenticationProvider;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationSessionManager;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken;
import com.tiny.platform.core.oauth.security.TotpService;
import com.tiny.platform.core.oauth.security.TotpVerificationGuard;
import com.tiny.platform.core.oauth.service.AuthenticationAuditService;
import com.tiny.platform.core.oauth.service.SecurityService;
import com.tiny.platform.core.oauth.tenant.TenantContextFilter;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationMethodRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.filter.RequestContextFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PartialMfaFormLoginIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAuthenticationMethodRepository authenticationMethodRepository;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private SecurityService securityService;

    @Mock
    private AuthenticationAuditService authenticationAuditService;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TotpService totpService;

    private MockMvc mockMvc;

    private SecurityContextRepository securityContextRepository;
    private CookieCsrfTokenRepository csrfTokenRepository;

    @BeforeEach
    void setUp() throws Exception {
        PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        securityContextRepository = new HttpSessionSecurityContextRepository();
        csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookiePath("/");
        csrfTokenRepository.setCookieName("XSRF-TOKEN");
        csrfTokenRepository.setHeaderName("X-XSRF-TOKEN");
        csrfTokenRepository.setParameterName("_csrf");

        FrontendProperties frontendProperties = new FrontendProperties();
        frontendProperties.setLoginUrl("redirect:http://localhost:5173/login");
        frontendProperties.setTotpBindUrl("redirect:http://localhost:5173/self/security/totp-bind");
        frontendProperties.setTotpVerifyUrl("redirect:http://localhost:5173/self/security/totp-verify");

        MultiFactorAuthenticationSessionManager sessionManager =
                new MultiFactorAuthenticationSessionManager(userDetailsService, securityContextRepository);

        SecurityController securityController = new SecurityController(
                userRepository,
                securityService,
                authenticationMethodRepository,
                frontendProperties,
                sessionManager,
                authenticationAuditService
        );

        MultiAuthenticationProvider authenticationProvider = new MultiAuthenticationProvider(
                userRepository,
                authenticationMethodRepository,
                passwordEncoder,
                userDetailsService,
                securityService,
                new TotpVerificationGuard(authenticationMethodRepository, new MfaProperties(), totpService),
                new LoginFailurePolicy(new LoginProtectionProperties())
        );

        UsernamePasswordAuthenticationFilter loginFilter = new UsernamePasswordAuthenticationFilter();
        loginFilter.setAuthenticationManager(new ProviderManager(authenticationProvider));
        loginFilter.setAuthenticationSuccessHandler(new CustomLoginSuccessHandler(
                securityService,
                userRepository,
                frontendProperties,
                sessionManager,
                authenticationAuditService
        ));
        loginFilter.setAuthenticationFailureHandler(new CustomLoginFailureHandler(
                userRepository,
                authenticationAuditService,
                new LoginFailurePolicy(new LoginProtectionProperties())
        ));
        loginFilter.setAuthenticationDetailsSource(new CustomWebAuthenticationDetailsSource());
        loginFilter.setSecurityContextRepository(securityContextRepository);
        loginFilter.setFilterProcessesUrl("/login");
        loginFilter.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new CsrfController(), securityController)
                .addFilters(
                        new RequestContextFilter(),
                        new SecurityContextPersistenceFilter(securityContextRepository),
                        new TenantContextFilter(tenantRepository),
                        new CsrfFilter(csrfTokenRepository),
                        loginFilter,
                        partialMfaAuthorizationFilter()
                )
                .build();
    }

    @Test
    void loginShouldRejectPostWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "raw-password")
                        .param("tenantId", "1")
                        .param("authenticationProvider", "LOCAL")
                        .param("authenticationType", "PASSWORD"))
                .andExpect(status().isForbidden());
    }

    @Test
    void formLoginShouldPersistPartialMfaTokenAndAllowSecurityChallengeEndpoints() throws Exception {
        User user = user();
        SecurityUser securityUser = securityUser(user);

        when(userRepository.findUserByUsernameAndTenantId("admin", 1L)).thenReturn(Optional.of(user));
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(securityUser);
        when(authenticationMethodRepository.findEnabledMethodsByUserId(1L, 1L)).thenReturn(List.of(
                passwordMethod(user.getId(), user.getTenantId(), "{noop}raw-password"),
                totpMethod(user.getId(), user.getTenantId(), "BASE32SECRET")
        ));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of(
                "totpBound", true,
                "totpActivated", true,
                "disableMfa", false,
                "skipMfaRemind", false,
                "forceMfa", false,
                "requireTotp", true
        ));
        var csrf = fetchCsrf();

        var loginResult = mockMvc.perform(post("/login")
                        .cookie(csrf.cookie())
                        .param("username", "admin")
                        .param("password", "raw-password")
                        .param("tenantId", "1")
                        .param("authenticationProvider", "LOCAL")
                        .param("authenticationType", "PASSWORD")
                        .param(csrf.parameterName(), csrf.token()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/self/security/totp-verify")))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute("AUTH_TENANT_ID")).isEqualTo(1L);

        SecurityContext securityContext = (SecurityContext) session.getAttribute(SPRING_SECURITY_CONTEXT_KEY);
        assertThat(securityContext).isNotNull();
        assertThat(securityContext.getAuthentication()).isInstanceOf(MultiFactorAuthenticationToken.class);

        MultiFactorAuthenticationToken partial = (MultiFactorAuthenticationToken) securityContext.getAuthentication();
        assertThat(partial.isAuthenticated()).isFalse();
        assertThat(partial.getName()).isEqualTo("admin");
        assertThat(partial.getCompletedFactors()).containsExactly(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD);

        mockMvc.perform(get("/self/security/status").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireTotp").value(true))
                .andExpect(jsonPath("$.totpActivated").value(true));

        mockMvc.perform(post("/self/security/skip-mfa-remind")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("{\"skipMfaRemind\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("当前状态不允许跳过二次验证绑定提醒"));

        mockMvc.perform(post("/self/security/totp/unbind")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("{\"totpCode\":\"123456\"}"))
                .andExpect(status().isForbidden());
    }

    private OncePerRequestFilter partialMfaAuthorizationFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                if (!request.getRequestURI().startsWith("/self/security/")) {
                    filterChain.doFilter(request, response);
                    return;
                }

                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                boolean challengePath = request.getRequestURI().equals("/self/security/status")
                        || request.getRequestURI().equals("/self/security/totp-bind")
                        || request.getRequestURI().equals("/self/security/totp-verify")
                        || request.getRequestURI().equals("/self/security/totp/pre-bind")
                        || request.getRequestURI().equals("/self/security/totp/bind-form")
                        || request.getRequestURI().equals("/self/security/totp/check-form")
                        || request.getRequestURI().equals("/self/security/totp/skip")
                        || request.getRequestURI().equals("/self/security/skip-mfa-remind");
                boolean granted = challengePath
                        ? DefaultSecurityConfig.hasChallengeFlowAccess(authentication)
                        : DefaultSecurityConfig.hasSensitiveSecurityAccess(authentication);
                if (!granted) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
                filterChain.doFilter(request, response);
            }
        };
    }

    private CsrfEnvelope fetchCsrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        JsonNode json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        return new CsrfEnvelope(
                cookie,
                json.get("token").asText(),
                json.get("parameterName").asText(),
                json.get("headerName").asText()
        );
    }

    private record CsrfEnvelope(Cookie cookie, String token, String parameterName, String headerName) {
    }

    private static User user() {
        User user = new User();
        user.setId(1L);
        user.setTenantId(1L);
        user.setUsername("admin");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);
        Role role = new Role();
        role.setName("ROLE_ADMIN");
        user.setRoles(Set.of(role));
        return user;
    }

    private static SecurityUser securityUser(User user) {
        return new SecurityUser(
                user.getId(),
                user.getTenantId(),
                user.getUsername(),
                "",
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")),
                true,
                true,
                true,
                true
        );
    }

    private static UserAuthenticationMethod passwordMethod(Long userId, Long tenantId, String encodedPassword) {
        UserAuthenticationMethod method = new UserAuthenticationMethod();
        method.setId(11L);
        method.setUserId(userId);
        method.setTenantId(tenantId);
        method.setAuthenticationProvider("LOCAL");
        method.setAuthenticationType("PASSWORD");
        method.setAuthenticationConfiguration(Map.of("password", encodedPassword));
        method.setIsMethodEnabled(true);
        return method;
    }

    private static UserAuthenticationMethod totpMethod(Long userId, Long tenantId, String secret) {
        UserAuthenticationMethod method = new UserAuthenticationMethod();
        method.setId(12L);
        method.setUserId(userId);
        method.setTenantId(tenantId);
        method.setAuthenticationProvider("LOCAL");
        method.setAuthenticationType("TOTP");
        method.setAuthenticationConfiguration(Map.of("secret", secret));
        method.setIsMethodEnabled(true);
        return method;
    }
}
