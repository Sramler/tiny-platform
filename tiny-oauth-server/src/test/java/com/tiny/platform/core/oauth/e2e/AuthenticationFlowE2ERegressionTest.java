package com.tiny.platform.core.oauth.e2e;

import com.tiny.platform.core.oauth.config.CustomLoginSuccessHandler;
import com.tiny.platform.core.oauth.config.FrontendProperties;
import com.tiny.platform.core.oauth.config.JwtTokenCustomizer;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.AuthUserResolutionService;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationSessionManager;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken;
import com.tiny.platform.core.oauth.security.PermissionVersionService;
import com.tiny.platform.core.oauth.service.AuthenticationAuditService;
import com.tiny.platform.core.oauth.service.SecurityService;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.core.oauth.tenant.TenantContextFilter;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 登录/租户/MFA 回归矩阵（E2E 风格）
 *
 * 覆盖维度：
 * - MFA: NONE / OPTIONAL / REQUIRED
 * - TOTP: 已绑定激活 / 未绑定
 * - 租户: 匹配 / 不匹配
 *
 * 断言：
 * - 租户隔离：tenant mismatch 时 403 + tenant_mismatch
 * - 登录跳转：业务页 / totp-verify / totp-bind
 * - Token Claims：activeTenantId（主字段）与 amr
 */
class AuthenticationFlowE2ERegressionTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private static void assertActiveTenantClaims(Map<String, Object> claims, long expectedTenantId) {
        assertThat(claims).containsEntry("activeTenantId", expectedTenantId);
    }

    @DisplayName("登录与租户认证矩阵回归")
    @ParameterizedTest(name = "{0}")
    @MethodSource("matrix")
    void shouldCoverLoginTenantMfaMatrix(String caseName,
                                         String mode,
                                         boolean totpBoundAndActivated,
                                         boolean tenantMatched,
                                         String expectedRedirectFragment,
                                         boolean expectTokenClaims,
                                         List<String> expectedAmr) throws Exception {
        // ---------- Step 1: 租户隔离闸门（已认证请求） ----------
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantContextFilter tenantFilter = new TenantContextFilter(tenantRepository);

        MultiFactorAuthenticationToken baseAuth = createPasswordOnlyAuthentication();
        SecurityContextHolder.getContext().setAuthentication(baseAuth);

        MockHttpServletRequest protectedRequest = new MockHttpServletRequest("GET", "/api/protected");
        protectedRequest.getSession(true).setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L);
        protectedRequest.addHeader(TenantContextContract.ACTIVE_TENANT_ID_HEADER, tenantMatched ? "1" : "2");
        MockHttpServletResponse protectedResponse = new MockHttpServletResponse();

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        AtomicReference<Long> tenantInChain = new AtomicReference<>();

        tenantFilter.doFilter(protectedRequest, protectedResponse, (req, resp) -> {
            chainCalled.set(true);
            tenantInChain.set(TenantContext.getActiveTenantId());
        });

        if (!tenantMatched) {
            assertThat(protectedResponse.getStatus()).isEqualTo(403);
            assertThat(protectedResponse.getContentAsString()).contains("tenant_mismatch");
            assertThat(chainCalled).isFalse();
            return;
        }

        assertThat(protectedResponse.getStatus()).isEqualTo(200);
        assertThat(chainCalled).isTrue();
        assertThat(tenantInChain.get()).isEqualTo(1L);

        // ---------- Step 2: 登录成功跳转 ----------
        SecurityService securityService = mock(SecurityService.class);
        UserRepository userRepository = mock(UserRepository.class);
        AuthUserResolutionService authUserResolutionService = mock(AuthUserResolutionService.class);
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(
                org.springframework.security.core.userdetails.User.withUsername("admin")
                        .password("{noop}n/a")
                        .authorities("ROLE_ADMIN")
                        .build());

        MultiFactorAuthenticationSessionManager sessionManager =
                new MultiFactorAuthenticationSessionManager(userDetailsService, new HttpSessionSecurityContextRepository());
        CustomLoginSuccessHandler successHandler = new CustomLoginSuccessHandler(
                securityService,
                userRepository,
                frontendProperties(),
                sessionManager,
                authUserResolutionService,
                auditService);

        User user = loginUser();
        when(authUserResolutionService.resolveUserRecordInActiveTenant("admin", 1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(securityService.getSecurityStatus(user)).thenReturn(securityStatus(mode, totpBoundAndActivated));

        MockHttpServletRequest loginRequest = new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse loginResponse = new MockHttpServletResponse();

        TenantContext.setActiveTenantId(1L);
        successHandler.onAuthenticationSuccess(loginRequest, loginResponse, baseAuth);

        assertThat(loginResponse.getRedirectedUrl()).contains(expectedRedirectFragment);

        // ---------- Step 3: token claims（amr + activeTenantId） ----------
        if (!expectTokenClaims) {
            return;
        }

        Authentication tokenPrincipal = baseAuth;
        if (expectedAmr.contains("totp")) {
            MockHttpServletResponse mfaResponse = new MockHttpServletResponse();
            sessionManager.promoteToFullyAuthenticated(
                    user,
                    baseAuth,
                    loginRequest,
                    mfaResponse,
                    MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP
            );
            tokenPrincipal = SecurityContextHolder.getContext().getAuthentication();
        }

        Map<String, Object> claims = accessTokenClaims(tokenPrincipal, userRepository);
        assertActiveTenantClaims(claims, 1L);
        assertThat(claims).containsKey("amr");
        assertThat(asStringList(claims.get("amr"))).containsExactlyInAnyOrderElementsOf(expectedAmr);
    }

    private static Stream<Arguments> matrix() {
        return Stream.of(
                // NONE
                Arguments.of("NONE + BOUND + TENANT_MATCH", "NONE", true, true,
                        "http://localhost:5173/", true, List.of("password")),
                Arguments.of("NONE + UNBOUND + TENANT_MATCH", "NONE", false, true,
                        "http://localhost:5173/", true, List.of("password")),
                Arguments.of("NONE + BOUND + TENANT_MISMATCH", "NONE", true, false,
                        "", false, List.of()),
                Arguments.of("NONE + UNBOUND + TENANT_MISMATCH", "NONE", false, false,
                        "", false, List.of()),

                // OPTIONAL
                Arguments.of("OPTIONAL + BOUND + TENANT_MATCH", "OPTIONAL", true, true,
                        "/self/security/totp-verify?redirect=%2F", true, List.of("password", "totp")),
                Arguments.of("OPTIONAL + UNBOUND + TENANT_MATCH", "OPTIONAL", false, true,
                        "/self/security/totp-bind?redirect=%2F", false, List.of()),
                Arguments.of("OPTIONAL + BOUND + TENANT_MISMATCH", "OPTIONAL", true, false,
                        "", false, List.of()),
                Arguments.of("OPTIONAL + UNBOUND + TENANT_MISMATCH", "OPTIONAL", false, false,
                        "", false, List.of()),

                // REQUIRED
                Arguments.of("REQUIRED + BOUND + TENANT_MATCH", "REQUIRED", true, true,
                        "/self/security/totp-verify?redirect=%2F", true, List.of("password", "totp")),
                Arguments.of("REQUIRED + UNBOUND + TENANT_MATCH", "REQUIRED", false, true,
                        "/self/security/totp-bind?redirect=%2F", false, List.of()),
                Arguments.of("REQUIRED + BOUND + TENANT_MISMATCH", "REQUIRED", true, false,
                        "", false, List.of()),
                Arguments.of("REQUIRED + UNBOUND + TENANT_MISMATCH", "REQUIRED", false, false,
                        "", false, List.of())
        );
    }

    private MultiFactorAuthenticationToken createPasswordOnlyAuthentication() {
        MultiFactorAuthenticationToken authentication = new MultiFactorAuthenticationToken(
                "admin",
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                Set.of(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        authentication.setDetails(new SecurityUser(
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
        return authentication;
    }

    private FrontendProperties frontendProperties() {
        FrontendProperties properties = new FrontendProperties();
        properties.setLoginUrl("redirect:http://localhost:5173/login");
        properties.setTotpBindUrl("redirect:http://localhost:5173/self/security/totp-bind");
        properties.setTotpVerifyUrl("redirect:http://localhost:5173/self/security/totp-verify");
        return properties;
    }

    private User loginUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);
        return user;
    }

    private Map<String, Object> securityStatus(String mode, boolean boundAndActivated) {
        boolean disableMfa = "NONE".equalsIgnoreCase(mode);
        boolean forceMfa = "REQUIRED".equalsIgnoreCase(mode);
        boolean requireTotp = !disableMfa && boundAndActivated;
        return Map.of(
                "totpBound", boundAndActivated,
                "totpActivated", boundAndActivated,
                "disableMfa", disableMfa,
                "skipMfaRemind", false,
                "forceMfa", forceMfa,
                "requireTotp", requireTotp
        );
    }

    private Map<String, Object> accessTokenClaims(Authentication principal, UserRepository userRepository) {
        RegisteredClient client = RegisteredClient.withId("rc-id")
                .clientId("vue-client")
                .clientSecret("{noop}secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:5173/callback")
                .scope("openid")
                .scope("profile")
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder().build())
                .build();

        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .id("auth-id")
                .principalName("admin")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of("openid", "profile"))
                .attribute("auth_time", Instant.ofEpochSecond(1_770_000_000L))
                .build();

        JwtEncodingContext context = JwtEncodingContext.with(
                        JwsHeader.with(SignatureAlgorithm.RS256),
                        JwtClaimsSet.builder()
                )
                .registeredClient(client)
                .principal(principal)
                .authorization(authorization)
                .authorizedScopes(Set.of("openid", "profile"))
                .tokenType(OAuth2TokenType.ACCESS_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .build();

        PermissionVersionService permissionVersionService = mock(PermissionVersionService.class);
        when(permissionVersionService.resolvePermissionsVersion(1L, 1L)).thenReturn("perm-v1");
        new JwtTokenCustomizer(userRepository, permissionVersionService).customize(context);
        return context.getClaims().build().getClaims();
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
