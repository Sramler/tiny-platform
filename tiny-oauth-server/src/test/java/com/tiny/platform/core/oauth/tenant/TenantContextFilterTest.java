package com.tiny.platform.core.oauth.tenant;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken;
import com.tiny.platform.core.oauth.security.PermissionVersionService;
import com.tiny.platform.infrastructure.auth.audit.domain.AuthorizationAuditEventType;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditService;
import com.tiny.platform.infrastructure.auth.org.domain.OrganizationUnit;
import com.tiny.platform.infrastructure.auth.org.repository.OrganizationUnitRepository;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantContextFilterTest {

    private final TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
    private final TenantContextFilter filter = new TenantContextFilter(tenantRepository);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void shouldRejectWhenAuthenticatedTenantMismatchesHeaderTenant() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader(TenantContextContract.ACTIVE_TENANT_ID_HEADER, "2");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityUser principal = new SecurityUser(
                1L,
                1L,
                "admin",
                "",
                List.of(),
                true,
                true,
                true,
                true
        );
        var auth = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        filter.doFilter(request, response, (req, resp) -> {
            throw new AssertionError("filter chain should not be executed");
        });

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("tenant_mismatch");
    }

    @Test
    void shouldFreezeTenantIdInSessionAfterAuthenticatedRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Long> tenantInChain = new AtomicReference<>();
        AtomicReference<String> sourceInChain = new AtomicReference<>();

        SecurityUser principal = new SecurityUser(
                1L,
                9L,
                "admin",
                "",
                List.of(),
                true,
                true,
                true,
                true
        );
        var auth = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        filter.doFilter(request, response, (req, resp) -> {
            tenantInChain.set(TenantContext.getActiveTenantId());
            sourceInChain.set(TenantContext.getTenantSource());
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(tenantInChain.get()).isEqualTo(9L);
        assertThat(sourceInChain.get()).isEqualTo(TenantContext.SOURCE_SESSION);
        assertThat(request.getSession(false)).isNotNull();
        assertThat(request.getSession(false).getAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY)).isEqualTo(9L);
        assertThat(TenantContext.getActiveTenantId()).isNull();
    }

    @Test
    void shouldResolveTenantFromPartialMfaTokenWithFactors() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/self/security/status");
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Long> tenantInChain = new AtomicReference<>();

        SecurityUser details = new SecurityUser(
                1L,
                6L,
                "admin",
                "",
                List.of(),
                true,
                true,
                true,
                true
        );
        MultiFactorAuthenticationToken auth = MultiFactorAuthenticationToken.partiallyAuthenticated(
                "admin",
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                java.util.Set.of(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD),
                List.of()
        );
        auth.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(auth);

        filter.doFilter(request, response, (req, resp) -> tenantInChain.set(TenantContext.getActiveTenantId()));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(tenantInChain.get()).isEqualTo(6L);
        assertThat(request.getSession(false).getAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY)).isEqualTo(6L);
    }

    @Test
    void shouldMarkTenantSourceAsTokenForJwtAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Long> tenantInChain = new AtomicReference<>();
        AtomicReference<String> sourceInChain = new AtomicReference<>();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("activeTenantId", 7L)
                .subject("admin")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList("ROLE_USER"))
        );

        filter.doFilter(request, response, (req, resp) -> {
            tenantInChain.set(TenantContext.getActiveTenantId());
            sourceInChain.set(TenantContext.getTenantSource());
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(tenantInChain.get()).isEqualTo(7L);
        assertThat(sourceInChain.get()).isEqualTo(TenantContext.SOURCE_TOKEN);
    }

    @Test
    void shouldUseActiveTenantIdClaimForJwtAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Long> tenantInChain = new AtomicReference<>();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("activeTenantId", 8L)
                .subject("admin")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList("ROLE_USER"))
        );

        filter.doFilter(request, response, (req, resp) -> tenantInChain.set(TenantContext.getActiveTenantId()));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(tenantInChain.get()).isEqualTo(8L);
    }

    @Test
    void shouldRejectUnauthenticatedNonLoginRequestEvenWithTenantHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader(TenantContextContract.ACTIVE_TENANT_ID_HEADER, "1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, resp) -> {
            throw new AssertionError("filter chain should not be executed");
        });

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("missing_tenant");
    }

    @Test
    void defaultIssuerOauth2TokenPostSkipsTenantContextFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainRan = new AtomicBoolean(false);
        filter.doFilter(request, response, (req, resp) -> chainRan.set(true));
        assertThat(chainRan.get()).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void tenantPrefixedOauth2TokenPostStillRunsTenantContextFilter() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setId(99L);
        tenant.setCode("acme");
        tenant.setName("acme");
        tenant.setEnabled(true);
        when(tenantRepository.findByCode("acme")).thenReturn(Optional.of(tenant));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/acme/oauth2/token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Long> tenantInChain = new AtomicReference<>();
        filter.doFilter(request, response, (req, resp) -> tenantInChain.set(TenantContext.getActiveTenantId()));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(tenantInChain.get()).isEqualTo(99L);
    }

    @Test
    void platformIssuerOauth2TokenPostRunsAsPlatformScopeWithoutTenantLookup() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/platform/oauth2/token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Long> tenantInChain = new AtomicReference<>();
        AtomicReference<String> scopeTypeInChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, resp) -> {
            tenantInChain.set(TenantContext.getActiveTenantId());
            scopeTypeInChain.set(TenantContext.getActiveScopeType());
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(tenantInChain.get()).isNull();
        assertThat(scopeTypeInChain.get()).isEqualTo(TenantContextContract.SCOPE_TYPE_PLATFORM);
        verify(tenantRepository, never()).findByCode("platform");
    }

    @Test
    void issuerTenantSupportShouldTreatPlatformAsIssuerKeyNotTenantCode() {
        assertThat(IssuerTenantSupport.extractIssuerKeyFromRequestPath("/platform/oauth2/token"))
            .isEqualTo(IssuerTenantSupport.PLATFORM_ISSUER_KEY);
        assertThat(IssuerTenantSupport.extractTenantCodeFromRequestPath("/platform/oauth2/token")).isNull();
        assertThat(IssuerTenantSupport.extractIssuerKeyFromIssuer("http://localhost:9000/platform"))
            .isEqualTo(IssuerTenantSupport.PLATFORM_ISSUER_KEY);
        assertThat(IssuerTenantSupport.extractTenantCodeFromIssuer("http://localhost:9000/platform")).isNull();
    }

    @Test
    void issuerTenantSupportDefaultProtocolPostEndpointsMatchLiteralPathsOnly() {
        assertThat(IssuerTenantSupport.isDefaultIssuerOAuth2ProtocolPostEndpoint("/oauth2/token")).isTrue();
        assertThat(IssuerTenantSupport.isDefaultIssuerOAuth2ProtocolPostEndpoint("/oauth2/revoke")).isTrue();
        assertThat(IssuerTenantSupport.isDefaultIssuerOAuth2ProtocolPostEndpoint("/acme/oauth2/token")).isFalse();
        assertThat(IssuerTenantSupport.isDefaultIssuerOAuth2ProtocolPostEndpoint("/oauth2/token/extra")).isFalse();
    }

    @Test
    void shouldRejectRequestsForDecommissionedActiveTenant() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityUser principal = new SecurityUser(
                1L,
                15L,
                "admin",
                "",
                List.of(),
                true,
                true,
                true,
                true
        );
        var auth = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(tenantRepository.findLoginBlockedLifecycleStatus(15L)).thenReturn(Optional.of("DECOMMISSIONED"));

        filter.doFilter(request, response, (req, resp) -> {
            throw new AssertionError("filter chain should not be executed");
        });

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("tenant_decommissioned");
    }

    @Test
    void shouldRejectRequestsForFrozenActiveTenant() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityUser principal = new SecurityUser(
                1L,
                16L,
                "admin",
                "",
                List.of(),
                true,
                true,
                true,
                true
        );
        var auth = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(tenantRepository.findLoginBlockedLifecycleStatus(16L)).thenReturn(Optional.of("FROZEN"));

        filter.doFilter(request, response, (req, resp) -> {
            throw new AssertionError("filter chain should not be executed");
        });

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("tenant_frozen");
    }

    @Test
    void shouldRejectWhitelistedAuditReadWhenPlatformScopeIsNotExplicit() throws Exception {
        AuthorizationAuditService authorizationAuditService = Mockito.mock(AuthorizationAuditService.class);
        TenantContextFilter platformFilter = new TenantContextFilter(
            tenantRepository,
            null,
            authorizationAuditService,
            new TenantLifecycleReadPolicy()
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/audit/authorization");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Long> tenantInChain = new AtomicReference<>();
        AtomicReference<String> scopeTypeInChain = new AtomicReference<>();

        SecurityUser principal = new SecurityUser(
            1L,
            50L,
            "platform-admin",
            "",
            List.of(),
            true,
            true,
            true,
            true
        );
        var auth = UsernamePasswordAuthenticationToken.authenticated(
            principal,
            null,
            AuthorityUtils.createAuthorityList("system:audit:auth:view")
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(tenantRepository.findLoginBlockedLifecycleStatus(50L)).thenReturn(Optional.of("FROZEN"));

        platformFilter.doFilter(request, response, (req, resp) -> {
            tenantInChain.set(TenantContext.getActiveTenantId());
            scopeTypeInChain.set(TenantContext.getActiveScopeType());
        });

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("tenant_frozen");
    }

    @Test
    void shouldRejectNonWhitelistedReadForFrozenPlatformScope() throws Exception {
        AuthorizationAuditService authorizationAuditService = Mockito.mock(AuthorizationAuditService.class);
        TenantContextFilter platformFilter = new TenantContextFilter(
            tenantRepository,
            null,
            authorizationAuditService,
            new TenantLifecycleReadPolicy()
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityUser principal = new SecurityUser(
            1L,
            50L,
            "platform-admin",
            "",
            List.of(),
            true,
            true,
            true,
            true
        );
        var auth = UsernamePasswordAuthenticationToken.authenticated(
            principal,
            null,
            AuthorityUtils.createAuthorityList("system:audit:auth:view")
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(tenantRepository.findLoginBlockedLifecycleStatus(50L)).thenReturn(Optional.of("FROZEN"));

        platformFilter.doFilter(request, response, (req, resp) -> {
            throw new AssertionError("filter chain should not be executed");
        });

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("tenant_frozen");
        verify(authorizationAuditService, never()).log(
            eq(AuthorizationAuditEventType.TENANT_LIFECYCLE_ALLOWLIST_ACCESS),
            eq(50L),
            isNull(),
            isNull(),
            eq(TenantContextContract.SCOPE_TYPE_PLATFORM),
            eq(50L),
            eq("audit"),
            eq("system:audit:auth:view"),
            org.mockito.ArgumentMatchers.anyString(),
            eq("SUCCESS"),
            isNull()
        );
    }

    @Test
    void shouldRejectWhitelistedReadWithoutExplicitPlatformScopeForDecommissionedTenant() throws Exception {
        AuthorizationAuditService authorizationAuditService = Mockito.mock(AuthorizationAuditService.class);
        TenantContextFilter platformFilter = new TenantContextFilter(
            tenantRepository,
            null,
            authorizationAuditService,
            new TenantLifecycleReadPolicy()
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/audit/authentication/export");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Long> tenantInChain = new AtomicReference<>();
        AtomicReference<String> scopeTypeInChain = new AtomicReference<>();

        SecurityUser principal = new SecurityUser(
            1L,
            60L,
            "platform-admin",
            "",
            List.of(),
            true,
            true,
            true,
            true
        );
        var auth = UsernamePasswordAuthenticationToken.authenticated(
            principal,
            null,
            AuthorityUtils.createAuthorityList("system:audit:authentication:view")
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(tenantRepository.findLoginBlockedLifecycleStatus(60L)).thenReturn(Optional.of("DECOMMISSIONED"));

        platformFilter.doFilter(request, response, (req, resp) -> {
            tenantInChain.set(TenantContext.getActiveTenantId());
            scopeTypeInChain.set(TenantContext.getActiveScopeType());
        });

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("tenant_decommissioned");
    }

    @Test
    void shouldUsePlatformScopeForLoginPostWithoutTenantCode() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> scopeInChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, resp) -> scopeInChain.set(TenantContext.getActiveScopeType()));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(scopeInChain.get()).isEqualTo(TenantContextContract.SCOPE_TYPE_PLATFORM);
    }

    @Test
    void shouldIgnoreSessionTenantForPlatformLoginPostWithoutTenantCode() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.getSession(true).setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 99L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Long> tenantInChain = new AtomicReference<>();
        AtomicReference<String> scopeInChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, resp) -> {
            tenantInChain.set(TenantContext.getActiveTenantId());
            scopeInChain.set(TenantContext.getActiveScopeType());
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(tenantInChain.get()).isNull();
        assertThat(scopeInChain.get()).isEqualTo(TenantContextContract.SCOPE_TYPE_PLATFORM);
    }

    @Test
    void shouldAcceptTenantCodeFromLoginFormBeforeAuthentication() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setId(88L);
        tenant.setCode("acme");
        tenant.setName("acme");
        tenant.setEnabled(true);
        when(tenantRepository.findByCode("acme")).thenReturn(Optional.of(tenant));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setParameter("tenantCode", "acme");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Long> tenantInChain = new AtomicReference<>();
        AtomicReference<String> sourceInChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, resp) -> {
            tenantInChain.set(TenantContext.getActiveTenantId());
            sourceInChain.set(TenantContext.getTenantSource());
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(tenantInChain.get()).isEqualTo(88L);
        assertThat(sourceInChain.get()).isEqualTo(TenantContext.SOURCE_LOGIN_PARAM);
    }

    @Test
    void shouldAllowFrozenTenantLoginPostToReachAuthenticationFlow() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setId(89L);
        tenant.setCode("bench");
        tenant.setName("bench");
        tenant.setEnabled(true);
        when(tenantRepository.findByCode("bench")).thenReturn(Optional.of(tenant));
        when(tenantRepository.findLoginBlockedLifecycleStatus(89L)).thenReturn(Optional.of("FROZEN"));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setParameter("tenantCode", "bench");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Long> tenantInChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, resp) -> tenantInChain.set(TenantContext.getActiveTenantId()));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(tenantInChain.get()).isEqualTo(89L);
    }

    @Test
    void shouldAcceptTenantFromBearerTokenForProtectedRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/menus/tree");
        request.addHeader("Authorization", "Bearer " + jwtWithActiveTenantId(1L));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Long> tenantInChain = new AtomicReference<>();
        AtomicReference<String> sourceInChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, resp) -> {
            tenantInChain.set(TenantContext.getActiveTenantId());
            sourceInChain.set(TenantContext.getTenantSource());
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(tenantInChain.get()).isEqualTo(1L);
        assertThat(sourceInChain.get()).isEqualTo(TenantContext.SOURCE_TOKEN);
    }

    @Test
    void shouldRejectBearerTokenWhenPermissionsVersionMismatches() throws Exception {
        PermissionVersionService permissionVersionService = Mockito.mock(PermissionVersionService.class);
        TenantContextFilter permissionsFilter = new TenantContextFilter(tenantRepository, permissionVersionService);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/menus/tree");
        request.addHeader("Authorization", "Bearer " + jwtWithPayload("""
            {"sub":"admin","userId":1,"activeTenantId":1,
             "authorities":["ROLE_ADMIN","system:user:list"],"permissionsVersion":"stale-v1"}
            """));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(permissionVersionService.resolvePermissionsVersion(eq(1L), eq(1L), eq("TENANT"), eq(1L))).thenReturn("fresh-v2");

        permissionsFilter.doFilter(request, response, (req, resp) -> {
            throw new AssertionError("filter chain should not be executed");
        });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer error=\"invalid_token\"");
        assertThat(response.getContentAsString()).contains("stale_permissions");
    }

    @Test
    void shouldAcceptBearerTokenWhenPermissionsVersionMatches() throws Exception {
        PermissionVersionService permissionVersionService = Mockito.mock(PermissionVersionService.class);
        TenantContextFilter permissionsFilter = new TenantContextFilter(tenantRepository, permissionVersionService);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/menus/tree");
        request.addHeader("Authorization", "Bearer " + jwtWithPayload("""
            {"sub":"admin","userId":1,"activeTenantId":1,
             "authorities":["ROLE_ADMIN","system:user:list"],"permissionsVersion":"perm-v1"}
            """));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Long> tenantInChain = new AtomicReference<>();

        when(permissionVersionService.resolvePermissionsVersion(eq(1L), eq(1L), eq("TENANT"), eq(1L))).thenReturn("perm-v1");

        permissionsFilter.doFilter(request, response, (req, resp) -> tenantInChain.set(TenantContext.getActiveTenantId()));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(tenantInChain.get()).isEqualTo(1L);
    }

    @Test
    void shouldAcceptBearerTokenWhenPermissionsVersionMatchesEvenIfAuthorityClaimsAreEmpty() throws Exception {
        PermissionVersionService permissionVersionService = Mockito.mock(PermissionVersionService.class);
        TenantContextFilter permissionsFilter = new TenantContextFilter(tenantRepository, permissionVersionService);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/menus/tree");
        request.addHeader("Authorization", "Bearer " + jwtWithPayload("""
            {"sub":"admin","userId":1,"activeTenantId":1,
             "authorities":[],"permissions":[],"permissionsVersion":"perm-v1"}
            """));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Long> tenantInChain = new AtomicReference<>();

        when(permissionVersionService.resolvePermissionsVersion(eq(1L), eq(1L), eq("TENANT"), eq(1L))).thenReturn("perm-v1");

        permissionsFilter.doFilter(request, response, (req, resp) -> tenantInChain.set(TenantContext.getActiveTenantId()));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(tenantInChain.get()).isEqualTo(1L);
    }

    @Test
    void shouldRejectSessionSecurityUserWhenPermissionsVersionMismatches() throws Exception {
        PermissionVersionService permissionVersionService = Mockito.mock(PermissionVersionService.class);
        TenantContextFilter permissionsFilter = new TenantContextFilter(tenantRepository, permissionVersionService);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/menus/tree");
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityUser principal = new SecurityUser(
                1L,
                1L,
                "admin",
                "",
                List.of(),
                true,
                true,
                true,
                true,
                "stale-v1"
        );
        var auth = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(permissionVersionService.resolvePermissionsVersion(eq(1L), eq(1L), eq("TENANT"), eq(1L))).thenReturn("fresh-v2");

        permissionsFilter.doFilter(request, response, (req, resp) -> {
            throw new AssertionError("filter chain should not be executed");
        });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("stale_permissions");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void shouldAcceptSessionSecurityUserWhenPermissionsVersionMatches() throws Exception {
        PermissionVersionService permissionVersionService = Mockito.mock(PermissionVersionService.class);
        TenantContextFilter permissionsFilter = new TenantContextFilter(tenantRepository, permissionVersionService);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/menus/tree");
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Long> tenantInChain = new AtomicReference<>();

        SecurityUser principal = new SecurityUser(
                1L,
                1L,
                "admin",
                "",
                List.of(),
                true,
                true,
                true,
                true,
                "perm-v1"
        );
        var auth = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(permissionVersionService.resolvePermissionsVersion(eq(1L), eq(1L), eq("TENANT"), eq(1L))).thenReturn("perm-v1");

        permissionsFilter.doFilter(request, response, (req, resp) -> tenantInChain.set(TenantContext.getActiveTenantId()));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(tenantInChain.get()).isEqualTo(1L);
    }

    @Test
    void shouldRedirectAuthorizeNavigationToLoginWhenSessionPermissionsStale() throws Exception {
        PermissionVersionService permissionVersionService = Mockito.mock(PermissionVersionService.class);
        TenantContextFilter permissionsFilter = new TenantContextFilter(tenantRepository, permissionVersionService);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request.setQueryString("client_id=vue-client");
        request.addHeader("Accept", "text/html");
        request.addHeader("Sec-Fetch-Mode", "navigate");
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityUser principal = new SecurityUser(
                1L,
                1L,
                "admin",
                "",
                List.of(),
                true,
                true,
                true,
                true,
                "stale-v1"
        );
        var auth = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(permissionVersionService.resolvePermissionsVersion(eq(1L), eq(1L))).thenReturn("fresh-v2");

        permissionsFilter.doFilter(request, response, (req, resp) -> {
            throw new AssertionError("filter chain should not be executed");
        });

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).contains("/login?redirect=");
        assertThat(response.getRedirectedUrl()).contains("error=stale_permissions");
    }

    @Test
    void shouldRedirectAuthorizeNavigationToLoginWhenTenantMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request.setQueryString("client_id=vue-client");
        request.addHeader("Accept", "text/html");
        request.addHeader("Sec-Fetch-Mode", "navigate");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, resp) -> {
            throw new AssertionError("filter chain should not be executed");
        });

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).contains("/login?redirect=");
        assertThat(response.getRedirectedUrl()).contains("error=missing_tenant");
    }

    @Test
    void shouldAcceptTenantFromIssuerPathForAuthorizationEndpoint() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setCode("default");
        tenant.setEnabled(true);
        when(tenantRepository.findByCode("default")).thenReturn(Optional.of(tenant));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/default/oauth2/authorize");
        request.setQueryString("client_id=vue-client");
        request.addHeader("Accept", "text/html");
        request.addHeader("Sec-Fetch-Mode", "navigate");
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Long> tenantInChain = new AtomicReference<>();
        AtomicReference<String> sourceInChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, resp) -> {
            tenantInChain.set(TenantContext.getActiveTenantId());
            sourceInChain.set(TenantContext.getTenantSource());
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(tenantInChain.get()).isEqualTo(1L);
        assertThat(sourceInChain.get()).isEqualTo(TenantContext.SOURCE_ISSUER);
        assertThat(request.getSession(false).getAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY)).isEqualTo(1L);
    }

    @Test
    void shouldRejectSessionOrgDeptScopeWhenScopeIdMissing_withoutServerError() throws Exception {
        OrganizationUnitRepository orgRepo = Mockito.mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepo = Mockito.mock(UserUnitRepository.class);
        TenantContextFilter scopedFilter = new TenantContextFilter(
            tenantRepository, null, null, new TenantLifecycleReadPolicy(), orgRepo, userUnitRepo);
        when(tenantRepository.findLoginBlockedLifecycleStatus(anyLong())).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/menus/tree");
        var session = request.getSession(true);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, "DEPT");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityUser principal = new SecurityUser(1L, 1L, "admin", "", List.of(), true, true, true, true);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));

        scopedFilter.doFilter(request, response, (req, resp) -> {
            throw new AssertionError("filter chain should not be executed");
        });

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains(TenantContextContract.ERROR_INVALID_ACTIVE_SCOPE);
        assertThat(session.getAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY)).isEqualTo("TENANT");
        assertThat(session.getAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY)).isEqualTo(1L);
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldRejectWhenOrgScopeIdNotInTenant_withoutServerError() throws Exception {
        OrganizationUnitRepository orgRepo = Mockito.mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepo = Mockito.mock(UserUnitRepository.class);
        TenantContextFilter scopedFilter = new TenantContextFilter(
            tenantRepository, null, null, new TenantLifecycleReadPolicy(), orgRepo, userUnitRepo);
        when(tenantRepository.findLoginBlockedLifecycleStatus(anyLong())).thenReturn(Optional.empty());
        when(orgRepo.findByIdAndTenantId(99L, 1L)).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/menus/tree");
        var session = request.getSession(true);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, "ORG");
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, 99L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityUser principal = new SecurityUser(7L, 1L, "u1", "", List.of(), true, true, true, true);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));

        scopedFilter.doFilter(request, response, (req, resp) -> {
            throw new AssertionError("filter chain should not be executed");
        });

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains(TenantContextContract.ERROR_INVALID_ACTIVE_SCOPE);
        assertThat(session.getAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY)).isEqualTo("TENANT");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldRejectWhenScopeTypeMismatchesUnitType_withoutServerError() throws Exception {
        OrganizationUnitRepository orgRepo = Mockito.mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepo = Mockito.mock(UserUnitRepository.class);
        TenantContextFilter scopedFilter = new TenantContextFilter(
            tenantRepository, null, null, new TenantLifecycleReadPolicy(), orgRepo, userUnitRepo);
        when(tenantRepository.findLoginBlockedLifecycleStatus(anyLong())).thenReturn(Optional.empty());

        OrganizationUnit dept = new OrganizationUnit();
        dept.setId(10L);
        dept.setTenantId(1L);
        dept.setUnitType("DEPT");
        when(orgRepo.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(dept));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/menus/tree");
        var session = request.getSession(true);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, "ORG");
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, 10L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityUser principal = new SecurityUser(7L, 1L, "u1", "", List.of(), true, true, true, true);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));

        scopedFilter.doFilter(request, response, (req, resp) -> {
            throw new AssertionError("filter chain should not be executed");
        });

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains(TenantContextContract.ERROR_INVALID_ACTIVE_SCOPE);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldRejectWhenUserNotMemberOfDept_withoutServerError() throws Exception {
        OrganizationUnitRepository orgRepo = Mockito.mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepo = Mockito.mock(UserUnitRepository.class);
        TenantContextFilter scopedFilter = new TenantContextFilter(
            tenantRepository, null, null, new TenantLifecycleReadPolicy(), orgRepo, userUnitRepo);
        when(tenantRepository.findLoginBlockedLifecycleStatus(anyLong())).thenReturn(Optional.empty());

        OrganizationUnit dept = new OrganizationUnit();
        dept.setId(20L);
        dept.setTenantId(1L);
        dept.setUnitType("DEPT");
        when(orgRepo.findByIdAndTenantId(20L, 1L)).thenReturn(Optional.of(dept));
        when(userUnitRepo.existsByTenantIdAndUserIdAndUnitId(1L, 7L, 20L)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/menus/tree");
        var session = request.getSession(true);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, "DEPT");
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, 20L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityUser principal = new SecurityUser(7L, 1L, "u1", "", List.of(), true, true, true, true);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));

        scopedFilter.doFilter(request, response, (req, resp) -> {
            throw new AssertionError("filter chain should not be executed");
        });

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains(TenantContextContract.ERROR_INVALID_ACTIVE_SCOPE);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldRejectOrgDeptScopeWhenReposMissing_withoutServerError() throws Exception {
        TenantContextFilter scopedFilter = new TenantContextFilter(
            tenantRepository, null, null, new TenantLifecycleReadPolicy(), null, null);
        when(tenantRepository.findLoginBlockedLifecycleStatus(anyLong())).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/menus/tree");
        var session = request.getSession(true);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, "ORG");
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, 5L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityUser principal = new SecurityUser(7L, 1L, "u1", "", List.of(), true, true, true, true);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));

        scopedFilter.doFilter(request, response, (req, resp) -> {
            throw new AssertionError("filter chain should not be executed");
        });

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains(TenantContextContract.ERROR_INVALID_ACTIVE_SCOPE);
    }

    @Test
    void shouldRejectBearerOrgScopeWhenUnitInvalid_with401Not500() throws Exception {
        OrganizationUnitRepository orgRepo = Mockito.mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepo = Mockito.mock(UserUnitRepository.class);
        TenantContextFilter scopedFilter = new TenantContextFilter(
            tenantRepository, null, null, new TenantLifecycleReadPolicy(), orgRepo, userUnitRepo);
        when(tenantRepository.findLoginBlockedLifecycleStatus(anyLong())).thenReturn(Optional.empty());
        when(orgRepo.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/menus/tree");
        request.addHeader("Authorization", "Bearer " + jwtWithPayload("""
            {"sub":"admin","userId":3,"activeTenantId":1,"activeScopeType":"ORG","activeScopeId":5,"authorities":["ROLE_X"]}
            """));
        MockHttpServletResponse response = new MockHttpServletResponse();

        scopedFilter.doFilter(request, response, (req, resp) -> {
            throw new AssertionError("filter chain should not be executed");
        });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).contains("invalid_token");
        assertThat(response.getContentAsString()).contains(TenantContextContract.ERROR_INVALID_ACTIVE_SCOPE);
    }

    @Test
    void shouldAcceptBearerAndSessionWhenPairedActiveScopeMatches() throws Exception {
        OrganizationUnitRepository orgRepo = Mockito.mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepo = Mockito.mock(UserUnitRepository.class);
        TenantContextFilter scopedFilter = new TenantContextFilter(
            tenantRepository, null, null, new TenantLifecycleReadPolicy(), orgRepo, userUnitRepo);
        when(tenantRepository.findLoginBlockedLifecycleStatus(anyLong())).thenReturn(Optional.empty());

        OrganizationUnit org = new OrganizationUnit();
        org.setId(5L);
        org.setTenantId(1L);
        org.setUnitType("ORG");
        when(orgRepo.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(org));
        when(userUnitRepo.findUnitIdsByTenantIdAndUserIdAndStatus(eq(1L), eq(3L), eq("ACTIVE")))
            .thenReturn(List.of(5L));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/menus/tree");
        request.addHeader("Authorization", "Bearer " + jwtWithPayload("""
            {"sub":"admin","userId":3,"activeTenantId":1,"activeScopeType":"ORG","activeScopeId":5}
            """));
        var session = request.getSession(true);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, "ORG");
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, 5L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> scopeTypeInChain = new AtomicReference<>();
        AtomicReference<Long> scopeIdInChain = new AtomicReference<>();

        scopedFilter.doFilter(request, response, (req, resp) -> {
            scopeTypeInChain.set(TenantContext.getActiveScopeType());
            scopeIdInChain.set(TenantContext.getActiveScopeId());
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(scopeTypeInChain.get()).isEqualTo("ORG");
        assertThat(scopeIdInChain.get()).isEqualTo(5L);
    }

    @Test
    void shouldReject401WhenBearerAndSessionActiveScopeConflict() throws Exception {
        OrganizationUnitRepository orgRepo = Mockito.mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepo = Mockito.mock(UserUnitRepository.class);
        TenantContextFilter scopedFilter = new TenantContextFilter(
            tenantRepository, null, null, new TenantLifecycleReadPolicy(), orgRepo, userUnitRepo);
        when(tenantRepository.findLoginBlockedLifecycleStatus(anyLong())).thenReturn(Optional.empty());

        OrganizationUnit org5 = new OrganizationUnit();
        org5.setId(5L);
        org5.setTenantId(1L);
        org5.setUnitType("ORG");
        OrganizationUnit org7 = new OrganizationUnit();
        org7.setId(7L);
        org7.setTenantId(1L);
        org7.setUnitType("ORG");
        when(orgRepo.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(org5));
        when(orgRepo.findByIdAndTenantId(7L, 1L)).thenReturn(Optional.of(org7));
        when(userUnitRepo.findUnitIdsByTenantIdAndUserIdAndStatus(eq(1L), eq(3L), eq("ACTIVE")))
            .thenReturn(List.of(5L, 7L));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/menus/tree");
        request.addHeader("Authorization", "Bearer " + jwtWithPayload("""
            {"sub":"admin","userId":3,"activeTenantId":1,"activeScopeType":"ORG","activeScopeId":5}
            """));
        var session = request.getSession(true);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, "ORG");
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, 7L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        scopedFilter.doFilter(request, response, (req, resp) -> {
            throw new AssertionError("filter chain should not be executed");
        });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains(TenantContextContract.ERROR_INVALID_ACTIVE_SCOPE);
        assertThat(session.getAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY)).isEqualTo("TENANT");
        assertThat(session.getAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY)).isEqualTo(1L);
    }

    @Test
    void shouldReject401WhenBearerOrgPairConflictsWithSessionOrgId_notMixBearerTypeWithSessionId() throws Exception {
        OrganizationUnitRepository orgRepo = Mockito.mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepo = Mockito.mock(UserUnitRepository.class);
        TenantContextFilter scopedFilter = new TenantContextFilter(
            tenantRepository, null, null, new TenantLifecycleReadPolicy(), orgRepo, userUnitRepo);
        when(tenantRepository.findLoginBlockedLifecycleStatus(anyLong())).thenReturn(Optional.empty());

        OrganizationUnit org5 = new OrganizationUnit();
        org5.setId(5L);
        org5.setTenantId(1L);
        org5.setUnitType("ORG");
        when(orgRepo.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(org5));
        when(userUnitRepo.findUnitIdsByTenantIdAndUserIdAndStatus(eq(1L), eq(3L), eq("ACTIVE")))
            .thenReturn(List.of(5L));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/menus/tree");
        request.addHeader("Authorization", "Bearer " + jwtWithPayload("""
            {"sub":"admin","userId":3,"activeTenantId":1,"activeScopeType":"ORG","activeScopeId":5}
            """));
        var session = request.getSession(true);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, "ORG");
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, 99L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        scopedFilter.doFilter(request, response, (req, resp) -> {
            throw new AssertionError("filter chain should not be executed");
        });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains(TenantContextContract.ERROR_INVALID_ACTIVE_SCOPE);
        assertThat(session.getAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY)).isEqualTo("TENANT");
        assertThat(session.getAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY)).isEqualTo(1L);
    }

    @Test
    void shouldReject401WhenBearerOrgConflictsWithSessionScopeIdOnlyWithoutType() throws Exception {
        OrganizationUnitRepository orgRepo = Mockito.mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepo = Mockito.mock(UserUnitRepository.class);
        TenantContextFilter scopedFilter = new TenantContextFilter(
            tenantRepository, null, null, new TenantLifecycleReadPolicy(), orgRepo, userUnitRepo);
        when(tenantRepository.findLoginBlockedLifecycleStatus(anyLong())).thenReturn(Optional.empty());

        OrganizationUnit org5 = new OrganizationUnit();
        org5.setId(5L);
        org5.setTenantId(1L);
        org5.setUnitType("ORG");
        when(orgRepo.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(org5));
        when(userUnitRepo.findUnitIdsByTenantIdAndUserIdAndStatus(eq(1L), eq(3L), eq("ACTIVE")))
            .thenReturn(List.of(5L));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/menus/tree");
        request.addHeader("Authorization", "Bearer " + jwtWithPayload("""
            {"sub":"admin","userId":3,"activeTenantId":1,"activeScopeType":"ORG","activeScopeId":5}
            """));
        var session = request.getSession(true);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, 99L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        scopedFilter.doFilter(request, response, (req, resp) -> {
            throw new AssertionError("filter chain should not be executed");
        });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains(TenantContextContract.ERROR_INVALID_ACTIVE_SCOPE);
        assertThat(session.getAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY)).isEqualTo("TENANT");
        assertThat(session.getAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY)).isEqualTo(1L);
    }

    @Test
    void shouldAcceptWhenBearerOrgMatchesSessionScopeIdOnlyWithoutType() throws Exception {
        OrganizationUnitRepository orgRepo = Mockito.mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepo = Mockito.mock(UserUnitRepository.class);
        TenantContextFilter scopedFilter = new TenantContextFilter(
            tenantRepository, null, null, new TenantLifecycleReadPolicy(), orgRepo, userUnitRepo);
        when(tenantRepository.findLoginBlockedLifecycleStatus(anyLong())).thenReturn(Optional.empty());

        OrganizationUnit org5 = new OrganizationUnit();
        org5.setId(5L);
        org5.setTenantId(1L);
        org5.setUnitType("ORG");
        when(orgRepo.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(org5));
        when(userUnitRepo.findUnitIdsByTenantIdAndUserIdAndStatus(eq(1L), eq(3L), eq("ACTIVE")))
            .thenReturn(List.of(5L));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/menus/tree");
        request.addHeader("Authorization", "Bearer " + jwtWithPayload("""
            {"sub":"admin","userId":3,"activeTenantId":1,"activeScopeType":"ORG","activeScopeId":5}
            """));
        var session = request.getSession(true);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, 5L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> scopeTypeInChain = new AtomicReference<>();
        AtomicReference<Long> scopeIdInChain = new AtomicReference<>();

        scopedFilter.doFilter(request, response, (req, resp) -> {
            scopeTypeInChain.set(TenantContext.getActiveScopeType());
            scopeIdInChain.set(TenantContext.getActiveScopeId());
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(scopeTypeInChain.get()).isEqualTo("ORG");
        assertThat(scopeIdInChain.get()).isEqualTo(5L);
    }

    @Test
    void shouldClearSessionSecurityContextOnBearerInvalidActiveScope() throws Exception {
        OrganizationUnitRepository orgRepo = Mockito.mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepo = Mockito.mock(UserUnitRepository.class);
        TenantContextFilter scopedFilter = new TenantContextFilter(
            tenantRepository, null, null, new TenantLifecycleReadPolicy(), orgRepo, userUnitRepo);
        when(tenantRepository.findLoginBlockedLifecycleStatus(anyLong())).thenReturn(Optional.empty());
        when(orgRepo.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/menus/tree");
        request.addHeader("Authorization", "Bearer " + jwtWithPayload("""
            {"sub":"admin","userId":3,"activeTenantId":1,"activeScopeType":"ORG","activeScopeId":5,"authorities":["ROLE_X"]}
            """));
        var session = request.getSession(true);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, "opaque-context-handle");
        MockHttpServletResponse response = new MockHttpServletResponse();

        scopedFilter.doFilter(request, response, (req, resp) -> {
            throw new AssertionError("filter chain should not be executed");
        });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains(TenantContextContract.ERROR_INVALID_ACTIVE_SCOPE);
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
    }

    @Test
    void shouldRejectOrphanBearerActiveScopeIdWithoutType_with401() throws Exception {
        OrganizationUnitRepository orgRepo = Mockito.mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepo = Mockito.mock(UserUnitRepository.class);
        TenantContextFilter scopedFilter = new TenantContextFilter(
            tenantRepository, null, null, new TenantLifecycleReadPolicy(), orgRepo, userUnitRepo);
        when(tenantRepository.findLoginBlockedLifecycleStatus(anyLong())).thenReturn(Optional.empty());

        OrganizationUnit dept = new OrganizationUnit();
        dept.setId(30L);
        dept.setTenantId(1L);
        dept.setUnitType("DEPT");
        when(orgRepo.findByIdAndTenantId(30L, 1L)).thenReturn(Optional.of(dept));
        when(userUnitRepo.existsByTenantIdAndUserIdAndUnitId(1L, 7L, 30L)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/menus/tree");
        request.addHeader("Authorization", "Bearer " + jwtWithPayload("""
            {"sub":"admin","userId":7,"activeTenantId":1,"activeScopeId":30}
            """));
        var session = request.getSession(true);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, "DEPT");
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, 30L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        scopedFilter.doFilter(request, response, (req, resp) -> {
            throw new AssertionError("filter chain should not be executed");
        });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains(TenantContextContract.ERROR_INVALID_ACTIVE_SCOPE);
    }

    @Test
    void shouldUseSessionPairedScopeWhenBearerOmitsActiveScopeType() throws Exception {
        OrganizationUnitRepository orgRepo = Mockito.mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepo = Mockito.mock(UserUnitRepository.class);
        TenantContextFilter scopedFilter = new TenantContextFilter(
            tenantRepository, null, null, new TenantLifecycleReadPolicy(), orgRepo, userUnitRepo);
        when(tenantRepository.findLoginBlockedLifecycleStatus(anyLong())).thenReturn(Optional.empty());

        OrganizationUnit dept = new OrganizationUnit();
        dept.setId(30L);
        dept.setTenantId(1L);
        dept.setUnitType("DEPT");
        when(orgRepo.findByIdAndTenantId(30L, 1L)).thenReturn(Optional.of(dept));
        when(userUnitRepo.existsByTenantIdAndUserIdAndUnitId(1L, 7L, 30L)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/menus/tree");
        request.addHeader("Authorization", "Bearer " + jwtWithPayload("""
            {"sub":"admin","userId":7,"activeTenantId":1}
            """));
        var session = request.getSession(true);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, "DEPT");
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, 30L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> scopeTypeInChain = new AtomicReference<>();
        AtomicReference<Long> scopeIdInChain = new AtomicReference<>();

        scopedFilter.doFilter(request, response, (req, resp) -> {
            scopeTypeInChain.set(TenantContext.getActiveScopeType());
            scopeIdInChain.set(TenantContext.getActiveScopeId());
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(scopeTypeInChain.get()).isEqualTo("DEPT");
        assertThat(scopeIdInChain.get()).isEqualTo(30L);
    }

    private String jwtWithActiveTenantId(long tenantId) {
        return jwtWithPayload("{\"sub\":\"admin\",\"activeTenantId\":" + tenantId + "}");
    }

    private String jwtWithPayload(String payloadJson) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".signature";
    }
}
