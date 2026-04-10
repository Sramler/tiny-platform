package com.tiny.platform.core.oauth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tiny.platform.application.controller.user.UserController;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.infrastructure.auth.org.domain.OrganizationUnit;
import com.tiny.platform.infrastructure.auth.org.repository.OrganizationUnitRepository;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.core.oauth.security.LoginFailurePolicy;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationSessionManager;
import com.tiny.platform.core.oauth.security.AuthUserResolutionService;
import com.tiny.platform.core.oauth.session.UserSessionActivityFilter;
import com.tiny.platform.core.oauth.session.UserSessionService;
import com.tiny.platform.core.oauth.service.SecurityService;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.auth.resource.service.ResourceService;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationAuditRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.auth.user.service.AvatarService;
import com.tiny.platform.infrastructure.auth.user.service.UserService;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@SpringBootTest(
    webEnvironment = WebEnvironment.MOCK,
    classes = DefaultSecurityConfigUserEndpointIntegrationTest.SecurityTestApp.class
)
@AutoConfigureMockMvc
class DefaultSecurityConfigUserEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private OrganizationUnitRepository organizationUnitRepository;

    @Autowired
    private UserUnitRepository userUnitRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private AuthUserResolutionService authUserResolutionService;

    @Test
    @DisplayName("anonymous 访问 /sys/users/** 应被认证层拦截")
    void sysUsersProbeShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/sys/users/probe").sessionAttr(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    @DisplayName("authenticated 用户可以访问 /sys/users/**")
    void sysUsersProbeShouldAllowAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/sys/users/probe")
                .sessionAttr(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L)
                .with(user("alice").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(content().string("ok"));
    }

    @Test
    @DisplayName("真实 /sys/users/current 在仅 Session 且 active scope 合法时应正常通过")
    void currentUserShouldAllowSessionOnlyRequestWithActiveScope() throws Exception {
        Mockito.when(tenantRepository.findLoginBlockedLifecycleStatus(1L)).thenReturn(Optional.empty());
        Mockito.when(userUnitRepository.findUnitIdsByTenantIdAndUserIdAndStatus(1L, 3L, "ACTIVE"))
            .thenReturn(List.of(5L));
        Mockito.when(userService.findByUsername("alice")).thenReturn(Optional.of(userEntity(3L, "alice")));

        OrganizationUnit orgUnit = new OrganizationUnit();
        orgUnit.setId(5L);
        orgUnit.setTenantId(1L);
        orgUnit.setUnitType("ORG");
        Mockito.when(organizationUnitRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(orgUnit));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, "ORG");
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, 5L);

        SecurityUser principal = new SecurityUser(3L, 1L, "alice", "", List.of(), true, true, true, true, "pv-session");

        mockMvc.perform(get("/sys/users/current")
                .session(session)
                .with(user(principal)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.activeTenantId").value(1))
            .andExpect(jsonPath("$.activeScopeType").value("ORG"))
            .andExpect(jsonPath("$.activeScopeId").value(5))
            .andExpect(jsonPath("$.permissionsVersion").value("pv-session"));
    }

    @Test
    @DisplayName("真实 /sys/users/current 在 Bearer + Session active scope 一致时应正常通过")
    void currentUserShouldAllowWhenBearerAndSessionScopeMatch() throws Exception {
        Mockito.when(tenantRepository.findLoginBlockedLifecycleStatus(1L)).thenReturn(Optional.empty());
        Mockito.when(userUnitRepository.findUnitIdsByTenantIdAndUserIdAndStatus(1L, 3L, "ACTIVE"))
            .thenReturn(List.of(5L));
        Mockito.when(userService.findByUsername("alice")).thenReturn(Optional.of(userEntity(3L, "alice")));

        OrganizationUnit orgUnit = new OrganizationUnit();
        orgUnit.setId(5L);
        orgUnit.setTenantId(1L);
        orgUnit.setUnitType("ORG");
        Mockito.when(organizationUnitRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(orgUnit));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, "ORG");
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, 5L);

        SecurityUser principal = new SecurityUser(3L, 1L, "alice", "", List.of(), true, true, true, true, "pv-consistent");

        mockMvc.perform(get("/sys/users/current")
                .session(session)
                .with(user(principal))
                .header("Authorization", "Bearer " + jwtWithPayload("""
                    {"sub":"alice","userId":3,"activeTenantId":1,"activeScopeType":"ORG","activeScopeId":5,"permissions":"perm:1","permissionsVersion":"pv-from-jwt"}
                    """)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.activeTenantId").value(1))
            .andExpect(jsonPath("$.activeScopeType").value("ORG"))
            .andExpect(jsonPath("$.activeScopeId").value(5))
            .andExpect(jsonPath("$.permissionsVersion").value("pv-from-jwt"));
    }

    @Test
    @DisplayName("Bearer + Session active scope 冲突时，应 fail-closed 返回 401 并清理 session 安全上下文")
    void sysUsersProbeShouldFailClosedWhenBearerAndSessionScopeConflict() throws Exception {
        Mockito.when(tenantRepository.findLoginBlockedLifecycleStatus(1L)).thenReturn(Optional.empty());

        OrganizationUnit orgUnit = new OrganizationUnit();
        orgUnit.setId(5L);
        orgUnit.setTenantId(1L);
        orgUnit.setUnitType("ORG");
        Mockito.when(organizationUnitRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(orgUnit));
        Mockito.when(userUnitRepository.findUnitIdsByTenantIdAndUserIdAndStatus(1L, 3L, "ACTIVE"))
            .thenReturn(List.of(5L));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, "ORG");
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, 7L);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, "opaque-context-handle");

        MvcResult result = mockMvc.perform(get("/sys/users/probe")
                .session(session)
                .header("Authorization", "Bearer " + jwtWithPayload("""
                    {"sub":"admin","userId":3,"activeTenantId":1,"activeScopeType":"ORG","activeScopeId":5}
                    """)))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("invalid_token")))
            .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains(TenantContextContract.ERROR_INVALID_ACTIVE_SCOPE);
        assertThat(session.getAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY))
            .isEqualTo(TenantContextContract.SCOPE_TYPE_TENANT);
        assertThat(session.getAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY)).isEqualTo(1L);
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
    }

    @Test
    @DisplayName("真实 /sys/users/current 端点在 Bearer + Session active scope 冲突时也应 fail-closed")
    void currentUserShouldFailClosedWhenBearerAndSessionScopeConflict() throws Exception {
        Mockito.when(tenantRepository.findLoginBlockedLifecycleStatus(1L)).thenReturn(Optional.empty());

        OrganizationUnit orgUnit = new OrganizationUnit();
        orgUnit.setId(5L);
        orgUnit.setTenantId(1L);
        orgUnit.setUnitType("ORG");
        Mockito.when(organizationUnitRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(orgUnit));
        Mockito.when(userUnitRepository.findUnitIdsByTenantIdAndUserIdAndStatus(1L, 3L, "ACTIVE"))
            .thenReturn(List.of(5L));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, "ORG");
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, 7L);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, "opaque-context-handle");

        MvcResult result = mockMvc.perform(get("/sys/users/current")
                .session(session)
                .header("Authorization", "Bearer " + jwtWithPayload("""
                    {"sub":"admin","userId":3,"activeTenantId":1,"activeScopeType":"ORG","activeScopeId":5}
                    """)))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("invalid_token")))
            .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains(TenantContextContract.ERROR_INVALID_ACTIVE_SCOPE);
        assertThat(session.getAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY))
            .isEqualTo(TenantContextContract.SCOPE_TYPE_TENANT);
        assertThat(session.getAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY)).isEqualTo(1L);
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
    }

    @Test
    @DisplayName("真实 /sys/users/current/active-scope 在 Bearer + Session active scope 冲突时也应 fail-closed")
    void currentActiveScopeSwitchShouldFailClosedWhenBearerAndSessionScopeConflict() throws Exception {
        Mockito.when(tenantRepository.findLoginBlockedLifecycleStatus(1L)).thenReturn(Optional.empty());

        OrganizationUnit orgUnit = new OrganizationUnit();
        orgUnit.setId(5L);
        orgUnit.setTenantId(1L);
        orgUnit.setUnitType("ORG");
        Mockito.when(organizationUnitRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(orgUnit));
        Mockito.when(userUnitRepository.findUnitIdsByTenantIdAndUserIdAndStatus(1L, 3L, "ACTIVE"))
            .thenReturn(List.of(5L));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, "ORG");
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, 7L);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, "opaque-context-handle");

        MvcResult result = mockMvc.perform(post("/sys/users/current/active-scope")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"scopeType":"ORG","scopeId":5}
                    """)
                .header("Authorization", "Bearer " + jwtWithPayload("""
                    {"sub":"admin","userId":3,"activeTenantId":1,"activeScopeType":"ORG","activeScopeId":5}
                    """)))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("invalid_token")))
            .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains(TenantContextContract.ERROR_INVALID_ACTIVE_SCOPE);
        assertThat(session.getAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY))
            .isEqualTo(TenantContextContract.SCOPE_TYPE_TENANT);
        assertThat(session.getAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY)).isEqualTo(1L);
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
    }

    @Test
    @DisplayName("POST /sys/users/current/active-scope：M4（Bearer + Session 成对一致）写成功并返回 tokenRefreshRequired")
    void currentActiveScopeSwitchShouldSucceedWithM4BearerAndRequireTokenRefresh() throws Exception {
        Mockito.when(tenantRepository.findLoginBlockedLifecycleStatus(1L)).thenReturn(Optional.empty());
        Mockito.when(userUnitRepository.findUnitIdsByTenantIdAndUserIdAndStatus(1L, 3L, "ACTIVE"))
            .thenReturn(List.of(5L));
        Mockito.when(userService.findByUsername("alice")).thenReturn(Optional.of(userEntity(3L, "alice")));
        SecurityUser reloaded = new SecurityUser(3L, 1L, "alice", "", List.of(), true, true, true, true, "pv-m4-reload");
        Mockito.when(userDetailsService.loadUserByUsername("alice")).thenReturn(reloaded);

        OrganizationUnit orgUnit = new OrganizationUnit();
        orgUnit.setId(5L);
        orgUnit.setTenantId(1L);
        orgUnit.setUnitType("ORG");
        Mockito.when(organizationUnitRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(orgUnit));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, "ORG");
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, 5L);

        SecurityUser principal = new SecurityUser(3L, 1L, "alice", "", List.of(), true, true, true, true, "pv-m4");

        mockMvc.perform(post("/sys/users/current/active-scope")
                .session(session)
                .with(user(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"scopeType":"ORG","scopeId":5}
                    """)
                .header("Authorization", "Bearer " + jwtWithPayload("""
                    {"sub":"alice","userId":3,"activeTenantId":1,"activeScopeType":"ORG","activeScopeId":5,"permissions":"p1"}
                    """)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.tokenRefreshRequired").value(true))
            .andExpect(jsonPath("$.newActiveScopeType").value("ORG"))
            .andExpect(jsonPath("$.newActiveScopeId").value(5));
    }

    @Test
    @DisplayName("M4 写 scope 后沿用旧 Bearer（显式 scope claims）下一请求应 fail-closed，刷新 JWT 后恢复一致")
    void currentActiveScopeM4WriteThenStaleBearerJwtFailsUntilRefreshed() throws Exception {
        Mockito.when(tenantRepository.findLoginBlockedLifecycleStatus(1L)).thenReturn(Optional.empty());
        Mockito.when(userUnitRepository.findUnitIdsByTenantIdAndUserIdAndStatus(1L, 3L, "ACTIVE"))
            .thenReturn(List.of(5L, 60L));
        Mockito.when(userService.findByUsername("alice")).thenReturn(Optional.of(userEntity(3L, "alice")));

        OrganizationUnit orgUnit = new OrganizationUnit();
        orgUnit.setId(5L);
        orgUnit.setTenantId(1L);
        orgUnit.setUnitType("ORG");
        Mockito.when(organizationUnitRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(orgUnit));

        OrganizationUnit deptUnit = new OrganizationUnit();
        deptUnit.setId(60L);
        deptUnit.setTenantId(1L);
        deptUnit.setUnitType("DEPT");
        Mockito.when(organizationUnitRepository.findByIdAndTenantId(60L, 1L)).thenReturn(Optional.of(deptUnit));
        Mockito.when(userUnitRepository.existsByTenantIdAndUserIdAndUnitId(1L, 3L, 60L)).thenReturn(true);

        SecurityUser reloaded = new SecurityUser(3L, 1L, "alice", "", List.of(), true, true, true, true, "pv-after");
        Mockito.when(userDetailsService.loadUserByUsername("alice")).thenReturn(reloaded);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, "ORG");
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, 5L);

        SecurityUser principal = new SecurityUser(3L, 1L, "alice", "", List.of(), true, true, true, true, "pv-m4");

        String staleScopeJwt = jwtWithPayload("""
            {"sub":"alice","userId":3,"activeTenantId":1,"activeScopeType":"ORG","activeScopeId":5,"permissions":"p1","permissionsVersion":"pv1"}
            """);

        mockMvc.perform(post("/sys/users/current/active-scope")
                .session(session)
                .with(user(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"scopeType":"DEPT","scopeId":60}
                    """)
                .header("Authorization", "Bearer " + staleScopeJwt))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenRefreshRequired").value(true))
            .andExpect(jsonPath("$.newActiveScopeType").value("DEPT"))
            .andExpect(jsonPath("$.newActiveScopeId").value(60));

        MvcResult staleGet = mockMvc.perform(get("/sys/users/current")
                .session(session)
                .header("Authorization", "Bearer " + staleScopeJwt))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("invalid_token")))
            .andReturn();
        assertThat(staleGet.getResponse().getContentAsString()).contains(TenantContextContract.ERROR_INVALID_ACTIVE_SCOPE);

        String refreshedJwt = jwtWithPayload("""
            {"sub":"alice","userId":3,"activeTenantId":1,"activeScopeType":"DEPT","activeScopeId":60,"permissions":"p1","permissionsVersion":"pv2"}
            """);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, "DEPT");
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, 60L);
        mockMvc.perform(get("/sys/users/current")
                .session(session)
                .header("Authorization", "Bearer " + refreshedJwt))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.activeScopeType").value("DEPT"))
            .andExpect(jsonPath("$.activeScopeId").value(60));
    }

    @Test
    @DisplayName("真实 /sys/users/current/active-scope 在仅 Session 时应允许切换活动 scope")
    void currentActiveScopeSwitchShouldSucceedWithSessionOnlyAuthentication() throws Exception {
        Mockito.when(tenantRepository.findLoginBlockedLifecycleStatus(1L)).thenReturn(Optional.empty());

        OrganizationUnit orgUnit = new OrganizationUnit();
        orgUnit.setId(5L);
        orgUnit.setTenantId(1L);
        orgUnit.setUnitType("ORG");
        Mockito.when(organizationUnitRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(orgUnit));
        Mockito.when(userUnitRepository.findUnitIdsByTenantIdAndUserIdAndStatus(1L, 3L, "ACTIVE"))
            .thenReturn(List.of(5L));

        SecurityUser reloaded = new SecurityUser(3L, 1L, "alice", "", List.of(), true, true, true, true, "pv-switched");
        Mockito.when(userDetailsService.loadUserByUsername("alice")).thenReturn(reloaded);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, TenantContextContract.SCOPE_TYPE_TENANT);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, 1L);

        SecurityUser principal = new SecurityUser(3L, 1L, "alice", "", List.of(), true, true, true, true, "pv-before");

        mockMvc.perform(post("/sys/users/current/active-scope")
                .session(session)
                .with(user(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"scopeType":"ORG","scopeId":5}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.activeTenantId").value(1))
            .andExpect(jsonPath("$.activeScopeType").value("ORG"))
            .andExpect(jsonPath("$.activeScopeId").value(5))
            .andExpect(jsonPath("$.tokenRefreshRequired").value(false));

        assertThat(session.getAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY)).isEqualTo("ORG");
        assertThat(session.getAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY)).isEqualTo(5L);
    }

    @Test
    @DisplayName("真实 /sys/users/current/active-scope 在平台态可通过显式 TENANT scopeId 切回租户态")
    void currentActiveScopeSwitchShouldAllowTenantReentryFromPlatformWhenExplicitTenantIdProvided() throws Exception {
        Mockito.when(tenantRepository.findLoginBlockedLifecycleStatus(9L)).thenReturn(Optional.empty());
        Mockito.when(userService.findByUsername("alice")).thenReturn(Optional.of(userEntity(3L, "alice")));
        Mockito.when(authUserResolutionService.resolveUserRecordInActiveTenant("alice", 9L))
            .thenReturn(Optional.of(userEntity(3L, "alice")));

        SecurityUser reloaded = new SecurityUser(3L, 9L, "alice", "", List.of(), true, true, true, true, "pv-tenant-9");
        Mockito.when(userDetailsService.loadUserByUsername("alice")).thenReturn(reloaded);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, TenantContextContract.SCOPE_TYPE_PLATFORM);

        SecurityUser principal = new SecurityUser(3L, null, "alice", "", List.of(), true, true, true, true, "pv-platform");

        mockMvc.perform(post("/sys/users/current/active-scope")
                .session(session)
                .with(user(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"scopeType":"TENANT","scopeId":9}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.activeTenantId").value(9))
            .andExpect(jsonPath("$.activeScopeType").value("TENANT"))
            .andExpect(jsonPath("$.activeScopeId").value(9))
            .andExpect(jsonPath("$.tokenRefreshRequired").value(false));

        assertThat(session.getAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY)).isEqualTo(9L);
        assertThat(session.getAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY))
            .isEqualTo(TenantContextContract.SCOPE_TYPE_TENANT);
        assertThat(session.getAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY)).isEqualTo(9L);
    }

    @SpringBootConfiguration
    @Import({
        DefaultSecurityConfig.class,
        DefaultSecurityConfigUserEndpointIntegrationTest.ProbeController.class,
        UserController.class,
        org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration.class,
        org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class
    })
    static class SecurityTestApp {

        @Bean("corsConfigurationSource")
        CorsConfigurationSource corsConfigurationSource() {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.addAllowedOriginPattern("*");
            configuration.addAllowedHeader("*");
            configuration.addAllowedMethod("*");
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", configuration);
            return source;
        }

        @Bean
        AuthenticationProvider authenticationProvider() {
            return Mockito.mock(AuthenticationProvider.class);
        }

        @Bean
        SecurityService securityService() {
            return Mockito.mock(SecurityService.class);
        }

        @Bean
        UserRepository userRepository() {
            return Mockito.mock(UserRepository.class);
        }

        @Bean
        UserService userService() {
            return Mockito.mock(UserService.class);
        }

        @Bean
        UserAuthenticationAuditRepository userAuthenticationAuditRepository() {
            return Mockito.mock(UserAuthenticationAuditRepository.class);
        }

        @Bean
        AvatarService avatarService() {
            return Mockito.mock(AvatarService.class);
        }

        @Bean
        UserDetailsService userDetailsService() {
            return Mockito.mock(UserDetailsService.class);
        }

        @Bean
        AuthUserResolutionService authUserResolutionService() {
            return Mockito.mock(AuthUserResolutionService.class);
        }

        @Bean
        TenantRepository tenantRepository() {
            return Mockito.mock(TenantRepository.class);
        }

        @Bean
        ResourceService resourceService() {
            return Mockito.mock(ResourceService.class);
        }

        @Bean
        OrganizationUnitRepository organizationUnitRepository() {
            return Mockito.mock(OrganizationUnitRepository.class);
        }

        @Bean
        UserUnitRepository userUnitRepository() {
            return Mockito.mock(UserUnitRepository.class);
        }

        @Bean
        FrontendProperties frontendProperties() {
            return new FrontendProperties();
        }

        @Bean
        MultiFactorAuthenticationSessionManager multiFactorAuthenticationSessionManager() {
            return Mockito.mock(MultiFactorAuthenticationSessionManager.class);
        }

        @Bean
        com.tiny.platform.core.oauth.service.AuthenticationAuditService authenticationAuditService() {
            return Mockito.mock(com.tiny.platform.core.oauth.service.AuthenticationAuditService.class);
        }

        @Bean
        UserSessionService userSessionService() {
            return Mockito.mock(UserSessionService.class);
        }

        @Bean
        UserSessionActivityFilter userSessionActivityFilter() {
            return new UserSessionActivityFilter(Mockito.mock(UserSessionService.class)) {
                @Override
                protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                                jakarta.servlet.http.HttpServletResponse response,
                                                jakarta.servlet.FilterChain filterChain)
                    throws jakarta.servlet.ServletException, java.io.IOException {
                    filterChain.doFilter(request, response);
                }
            };
        }

        @Bean
        LoginFailurePolicy loginFailurePolicy() {
            return Mockito.mock(LoginFailurePolicy.class);
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                String[] segments = token.split("\\.");
                if (segments.length < 2) {
                    throw new org.springframework.security.oauth2.jwt.JwtException("invalid test jwt");
                }
                try {
                    String payloadJson = new String(
                        Base64.getUrlDecoder().decode(segments[1]),
                        StandardCharsets.UTF_8
                    );
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> claims = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(payloadJson, java.util.Map.class);
                    java.time.Instant issuedAt = java.time.Instant.now();
                    return org.springframework.security.oauth2.jwt.Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .issuedAt(issuedAt)
                        .expiresAt(issuedAt.plusSeconds(300))
                        .claims(entries -> entries.putAll(claims))
                        .build();
                } catch (java.io.IOException e) {
                    throw new org.springframework.security.oauth2.jwt.JwtException("failed to decode test jwt", e);
                }
            };
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/sys/users/probe")
        public String probe() {
            return "ok";
        }
    }

    private static com.tiny.platform.infrastructure.auth.user.domain.User userEntity(Long id, String username) {
        com.tiny.platform.infrastructure.auth.user.domain.User user =
            new com.tiny.platform.infrastructure.auth.user.domain.User();
        user.setId(id);
        user.setUsername(username);
        user.setNickname("Alice");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);
        return user;
    }

    private static String jwtWithPayload(String payloadJson) {
        String header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".signature";
    }
}
