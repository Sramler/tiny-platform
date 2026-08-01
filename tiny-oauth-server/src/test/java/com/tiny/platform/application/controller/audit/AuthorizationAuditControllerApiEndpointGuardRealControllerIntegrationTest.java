package com.tiny.platform.application.controller.audit;

import com.tiny.platform.application.controller.audit.security.AuthorizationAuditAccessGuard;
import com.tiny.platform.core.oauth.security.ApiEndpointRequirementFilter;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.core.oauth.tenant.TenantLifecycleAccessGuard;
import com.tiny.platform.infrastructure.auth.audit.domain.AuthorizationAuditLog;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditQuery;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditService;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditSummary;
import com.tiny.platform.infrastructure.auth.resource.domain.ApiEndpointEntry;
import com.tiny.platform.infrastructure.auth.resource.repository.ApiEndpointEntryRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.ApiEndpointPermissionRequirementRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.CarrierPermissionRequirementRow;
import com.tiny.platform.infrastructure.auth.resource.repository.UiActionEntryRepository;
import com.tiny.platform.infrastructure.auth.resource.service.CarrierPermissionReferenceSafetyService;
import com.tiny.platform.infrastructure.auth.resource.service.CarrierPermissionRequirementEvaluator;
import com.tiny.platform.infrastructure.auth.resource.service.ResourcePermissionBindingService;
import com.tiny.platform.infrastructure.auth.resource.service.ResourceService;
import com.tiny.platform.infrastructure.auth.resource.service.ResourceServiceImpl;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.role.service.EffectiveRoleResolutionService;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.menu.repository.MenuEntryRepository;
import com.tiny.platform.infrastructure.menu.repository.MenuPermissionRequirementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = AuthorizationAuditControllerApiEndpointGuardRealControllerIntegrationTest.TestApp.class
)
@AutoConfigureMockMvc
@ActiveProfiles("rbac-test")
class AuthorizationAuditControllerApiEndpointGuardRealControllerIntegrationTest {

    private static final long TENANT_ID = 9L;
    private static final long LIST_ENDPOINT_ID = 61101L;
    private static final long SUMMARY_ENDPOINT_ID = 61102L;
    private static final long EXPORT_ENDPOINT_ID = 61103L;
    private static final long BY_EVENT_TYPE_ENDPOINT_ID = 61104L;
    private static final long BY_USER_ENDPOINT_ID = 61105L;
    private static final long PURGE_ENDPOINT_ID = 61106L;
    private static final long VIEW_PERMISSION_ID = 62101L;
    private static final long EXPORT_PERMISSION_ID = 62102L;
    private static final long PURGE_PERMISSION_ID = 62103L;
    private static final String VIEW_AUTHORITY = "system:audit:auth:view";
    private static final String EXPORT_AUTHORITY = "system:audit:auth:export";
    private static final String PURGE_AUTHORITY = "system:audit:auth:purge";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiEndpointEntryRepository apiEndpointEntryRepository;

    @Autowired
    private ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository;

    @Autowired
    private AuthorizationAuditService auditService;

    @BeforeEach
    void setUp() {
        TenantContext.setActiveTenantId(TENANT_ID);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);
        TenantContext.setTenantSource(TenantContext.SOURCE_UNKNOWN);

        when(apiEndpointEntryRepository.findAll(
            Mockito.<Specification<ApiEndpointEntry>>any(),
            Mockito.<Sort>any()
        )).thenReturn(List.of(
            endpoint(LIST_ENDPOINT_ID, "audit-authorization-list", "/sys/audit/authorization", "GET",
                VIEW_AUTHORITY, VIEW_PERMISSION_ID),
            endpoint(SUMMARY_ENDPOINT_ID, "audit-authorization-summary", "/sys/audit/authorization/summary", "GET",
                VIEW_AUTHORITY, VIEW_PERMISSION_ID),
            endpoint(EXPORT_ENDPOINT_ID, "audit-authorization-export", "/sys/audit/authorization/export", "GET",
                EXPORT_AUTHORITY, EXPORT_PERMISSION_ID),
            endpoint(BY_EVENT_TYPE_ENDPOINT_ID, "audit-authorization-by-event-type",
                "/sys/audit/authorization/by-event-type", "GET", VIEW_AUTHORITY, VIEW_PERMISSION_ID),
            endpoint(BY_USER_ENDPOINT_ID, "audit-authorization-by-user",
                "/sys/audit/authorization/by-user/{userId}", "GET", VIEW_AUTHORITY, VIEW_PERMISSION_ID),
            endpoint(PURGE_ENDPOINT_ID, "audit-authorization-purge", "/sys/audit/authorization/purge", "DELETE",
                PURGE_AUTHORITY, PURGE_PERMISSION_ID)
        ));

        Page<AuthorizationAuditLog> empty = new PageImpl<>(List.of());
        when(auditService.search(any(AuthorizationAuditQuery.class), any(Pageable.class))).thenReturn(empty);
        when(auditService.summarize(any(AuthorizationAuditQuery.class)))
            .thenReturn(new AuthorizationAuditSummary(0, 0, 0, List.of()));
        when(auditService.listByTenantAndEventType(Mockito.anyLong(), Mockito.anyString(), any(Pageable.class)))
            .thenReturn(empty);
        when(auditService.listByTargetUser(Mockito.anyLong(), Mockito.anyLong())).thenReturn(List.of());
        when(auditService.purge(Mockito.anyInt())).thenReturn(0);
    }

    private ApiEndpointEntry endpoint(long id,
                                      String name,
                                      String uri,
                                      String method,
                                      String permission,
                                      long permissionId) {
        ApiEndpointEntry entry = new ApiEndpointEntry();
        entry.setId(id);
        entry.setTenantId(TENANT_ID);
        entry.setResourceLevel("TENANT");
        entry.setName(name);
        entry.setTitle(name);
        entry.setUri(uri);
        entry.setMethod(method);
        entry.setPermission(permission);
        entry.setRequiredPermissionId(permissionId);
        entry.setEnabled(true);
        return entry;
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void audit_realAuthorizationAuditController_sysAuditAuthorization_allow_shouldReturn200_whenRequirementSatisfied_staticUri()
        throws Exception {
        stubRequirement(LIST_ENDPOINT_ID, VIEW_AUTHORITY, true);

        mockMvc.perform(get("/sys/audit/authorization")
                .accept(MediaType.APPLICATION_JSON)
                .with(user("audit-viewer").authorities(new SimpleGrantedAuthority(VIEW_AUTHORITY))))
            .andExpect(status().isOk());
    }

    @Test
    void audit_realAuthorizationAuditController_sysAuditAuthorization_deny_shouldReturn403_whenPermissionDisabled_staticUri()
        throws Exception {
        stubRequirement(LIST_ENDPOINT_ID, VIEW_AUTHORITY, false);

        mockMvc.perform(get("/sys/audit/authorization")
                .accept(MediaType.APPLICATION_JSON)
                .with(user("audit-viewer").authorities(new SimpleGrantedAuthority(VIEW_AUTHORITY))))
            .andExpect(status().isForbidden());
    }

    @Test
    void audit_realAuthorizationAuditController_summary_allow_shouldReturn200_whenRequirementSatisfied()
        throws Exception {
        stubRequirement(SUMMARY_ENDPOINT_ID, VIEW_AUTHORITY, true);

        mockMvc.perform(get("/sys/audit/authorization/summary")
                .accept(MediaType.APPLICATION_JSON)
                .with(user("audit-viewer").authorities(new SimpleGrantedAuthority(VIEW_AUTHORITY))))
            .andExpect(status().isOk());
    }

    @Test
    void audit_realAuthorizationAuditController_summary_deny_shouldReturn403_whenPermissionDisabled()
        throws Exception {
        stubRequirement(SUMMARY_ENDPOINT_ID, VIEW_AUTHORITY, false);

        mockMvc.perform(get("/sys/audit/authorization/summary")
                .accept(MediaType.APPLICATION_JSON)
                .with(user("audit-viewer").authorities(new SimpleGrantedAuthority(VIEW_AUTHORITY))))
            .andExpect(status().isForbidden());
    }

    @Test
    void audit_realAuthorizationAuditController_export_allow_shouldReturn200_whenRequirementSatisfied()
        throws Exception {
        stubRequirement(EXPORT_ENDPOINT_ID, EXPORT_AUTHORITY, true);

        mockMvc.perform(get("/sys/audit/authorization/export")
                .accept("text/csv")
                .with(user("audit-exporter").authorities(new SimpleGrantedAuthority(EXPORT_AUTHORITY))))
            .andExpect(status().isOk());
    }

    @Test
    void audit_realAuthorizationAuditController_export_deny_shouldReturn403_whenPermissionDisabled()
        throws Exception {
        stubRequirement(EXPORT_ENDPOINT_ID, EXPORT_AUTHORITY, false);

        mockMvc.perform(get("/sys/audit/authorization/export")
                .accept("text/csv")
                .with(user("audit-exporter").authorities(new SimpleGrantedAuthority(EXPORT_AUTHORITY))))
            .andExpect(status().isForbidden());
    }

    @Test
    void audit_realAuthorizationAuditController_byEventType_allow_shouldReturn200_whenRequirementSatisfied()
        throws Exception {
        stubRequirement(BY_EVENT_TYPE_ENDPOINT_ID, VIEW_AUTHORITY, true);

        mockMvc.perform(get("/sys/audit/authorization/by-event-type")
                .param("eventType", "REQUIREMENT_AWARE_ACCESS")
                .accept(MediaType.APPLICATION_JSON)
                .with(user("audit-viewer").authorities(new SimpleGrantedAuthority(VIEW_AUTHORITY))))
            .andExpect(status().isOk());
    }

    @Test
    void audit_realAuthorizationAuditController_byEventType_deny_shouldReturn403_whenPermissionDisabled()
        throws Exception {
        stubRequirement(BY_EVENT_TYPE_ENDPOINT_ID, VIEW_AUTHORITY, false);

        mockMvc.perform(get("/sys/audit/authorization/by-event-type")
                .param("eventType", "REQUIREMENT_AWARE_ACCESS")
                .accept(MediaType.APPLICATION_JSON)
                .with(user("audit-viewer").authorities(new SimpleGrantedAuthority(VIEW_AUTHORITY))))
            .andExpect(status().isForbidden());
    }

    @Test
    void audit_realAuthorizationAuditController_byUser_allow_shouldReturn200_whenRequirementSatisfied_dynamicUri()
        throws Exception {
        stubRequirement(BY_USER_ENDPOINT_ID, VIEW_AUTHORITY, true);

        mockMvc.perform(get("/sys/audit/authorization/by-user/42")
                .accept(MediaType.APPLICATION_JSON)
                .with(user("audit-viewer").authorities(new SimpleGrantedAuthority(VIEW_AUTHORITY))))
            .andExpect(status().isOk());
    }

    @Test
    void audit_realAuthorizationAuditController_byUser_deny_shouldReturn403_whenPermissionDisabled_dynamicUri()
        throws Exception {
        stubRequirement(BY_USER_ENDPOINT_ID, VIEW_AUTHORITY, false);

        mockMvc.perform(get("/sys/audit/authorization/by-user/42")
                .accept(MediaType.APPLICATION_JSON)
                .with(user("audit-viewer").authorities(new SimpleGrantedAuthority(VIEW_AUTHORITY))))
            .andExpect(status().isForbidden());
    }

    @Test
    void audit_realAuthorizationAuditController_purge_allow_shouldReturn200_whenRequirementSatisfied()
        throws Exception {
        stubRequirement(PURGE_ENDPOINT_ID, PURGE_AUTHORITY, true);

        mockMvc.perform(delete("/sys/audit/authorization/purge")
                .param("retentionDays", "90")
                .accept(MediaType.APPLICATION_JSON)
                .with(user("audit-purger").authorities(new SimpleGrantedAuthority(PURGE_AUTHORITY))))
            .andExpect(status().isOk());
    }

    @Test
    void audit_realAuthorizationAuditController_purge_deny_shouldReturn403_whenPermissionDisabled()
        throws Exception {
        stubRequirement(PURGE_ENDPOINT_ID, PURGE_AUTHORITY, false);

        mockMvc.perform(delete("/sys/audit/authorization/purge")
                .param("retentionDays", "90")
                .accept(MediaType.APPLICATION_JSON)
                .with(user("audit-purger").authorities(new SimpleGrantedAuthority(PURGE_AUTHORITY))))
            .andExpect(status().isForbidden());
    }

    private void stubRequirement(long carrierId, String permissionCode, boolean permissionEnabled) {
        CarrierPermissionRequirementRow row = requirementRow(carrierId, permissionCode, permissionEnabled);
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(row));
    }

    private static CarrierPermissionRequirementRow requirementRow(long carrierId,
                                                                   String permissionCode,
                                                                   boolean permissionEnabled) {
        CarrierPermissionRequirementRow row = Mockito.mock(CarrierPermissionRequirementRow.class);
        Mockito.when(row.getCarrierId()).thenReturn(carrierId);
        Mockito.when(row.getRequirementGroup()).thenReturn(0);
        Mockito.when(row.getSortOrder()).thenReturn(1);
        Mockito.when(row.getPermissionCode()).thenReturn(permissionCode);
        Mockito.when(row.getNegated()).thenReturn(false);
        Mockito.when(row.getPermissionEnabled()).thenReturn(permissionEnabled);
        return row;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
        org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class,
        org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration.class,
        org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration.class,
        org.camunda.bpm.spring.boot.starter.CamundaBpmAutoConfiguration.class,
        org.camunda.bpm.spring.boot.starter.rest.CamundaBpmRestJerseyAutoConfiguration.class,
        com.tiny.platform.infrastructure.idempotent.starter.autoconfigure.IdempotentAutoConfiguration.class
    })
    @Import(TestConfig.class)
    static class TestApp {}

    static class TestConfig {
        @Bean
        public AuthorizationAuditController authorizationAuditController(AuthorizationAuditService auditService,
                                                                        TenantLifecycleAccessGuard tenantLifecycleAccessGuard) {
            return new AuthorizationAuditController(auditService, tenantLifecycleAccessGuard);
        }

        @Bean("authorizationAuditAccessGuard")
        public AuthorizationAuditAccessGuard authorizationAuditAccessGuard() {
            return new AuthorizationAuditAccessGuard();
        }

        @Bean
        public TenantLifecycleAccessGuard tenantLifecycleAccessGuard() {
            TenantLifecycleAccessGuard guard = Mockito.mock(TenantLifecycleAccessGuard.class);
            Mockito.doNothing().when(guard)
                .assertPlatformTargetTenantReadable(Mockito.anyLong(), Mockito.anyString());
            return guard;
        }

        @Bean
        public AuthorizationAuditService auditService() {
            return Mockito.mock(AuthorizationAuditService.class);
        }

        @Bean
        public ApiEndpointEntryRepository apiEndpointEntryRepository() {
            return Mockito.mock(ApiEndpointEntryRepository.class);
        }

        @Bean
        public ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository() {
            return Mockito.mock(ApiEndpointPermissionRequirementRepository.class);
        }

        @Bean
        public ResourceService resourceService(ApiEndpointEntryRepository apiEndpointEntryRepository,
                                               ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository,
                                               AuthorizationAuditService auditService) {
            RoleRepository roleRepository = Mockito.mock(RoleRepository.class);
            EffectiveRoleResolutionService effectiveRoleResolutionService = Mockito.mock(EffectiveRoleResolutionService.class);
            TenantUserRepository tenantUserRepository = Mockito.mock(TenantUserRepository.class);
            UserUnitRepository userUnitRepository = Mockito.mock(UserUnitRepository.class);
            MenuEntryRepository menuEntryRepository = Mockito.mock(MenuEntryRepository.class);
            UiActionEntryRepository uiActionEntryRepository = Mockito.mock(UiActionEntryRepository.class);
            ResourcePermissionBindingService resourcePermissionBindingService = Mockito.mock(ResourcePermissionBindingService.class);
            CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService = Mockito.mock(CarrierPermissionReferenceSafetyService.class);

            CarrierPermissionRequirementEvaluator evaluator = new CarrierPermissionRequirementEvaluator(
                Mockito.mock(MenuPermissionRequirementRepository.class),
                Mockito.mock(com.tiny.platform.infrastructure.auth.resource.repository.UiActionPermissionRequirementRepository.class),
                apiEndpointPermissionRequirementRepository
            );

            return new ResourceServiceImpl(
                roleRepository,
                effectiveRoleResolutionService,
                tenantUserRepository,
                userUnitRepository,
                menuEntryRepository,
                uiActionEntryRepository,
                apiEndpointEntryRepository,
                resourcePermissionBindingService,
                carrierPermissionReferenceSafetyService,
                evaluator,
                auditService
            );
        }

        @Bean
        public ApiEndpointRequirementFilter apiEndpointRequirementFilter(ResourceService resourceService) {
            return new ApiEndpointRequirementFilter(resourceService);
        }

        @Bean
        public SecurityFilterChain securityFilterChain(org.springframework.security.config.annotation.web.builders.HttpSecurity http,
                                                       ApiEndpointRequirementFilter apiEndpointRequirementFilter) throws Exception {
            http.csrf(csrf -> csrf.disable());
            http.authorizeHttpRequests(registry -> registry.anyRequest().permitAll());
            http.addFilterAfter(apiEndpointRequirementFilter, AnonymousAuthenticationFilter.class);
            return http.build();
        }
    }
}
