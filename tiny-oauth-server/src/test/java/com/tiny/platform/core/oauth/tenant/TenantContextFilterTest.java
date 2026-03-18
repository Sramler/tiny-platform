package com.tiny.platform.core.oauth.tenant;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken;
import com.tiny.platform.core.oauth.security.PermissionVersionService;
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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
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

        when(permissionVersionService.resolvePermissionsVersion(eq(1L), eq(1L))).thenReturn("fresh-v2");

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

        when(permissionVersionService.resolvePermissionsVersion(eq(1L), eq(1L))).thenReturn("perm-v1");

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

        when(permissionVersionService.resolvePermissionsVersion(eq(1L), eq(1L))).thenReturn("fresh-v2");

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

        when(permissionVersionService.resolvePermissionsVersion(eq(1L), eq(1L))).thenReturn("perm-v1");

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
