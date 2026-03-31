package com.tiny.platform.application.controller.audit;

import com.tiny.platform.application.controller.audit.security.AuthorizationAuditAccessGuard;
import com.tiny.platform.core.oauth.security.ApiEndpointRequirementFilter;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.core.oauth.tenant.TenantLifecycleAccessGuard;
import com.tiny.platform.infrastructure.auth.audit.domain.AuthorizationAuditLog;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditQuery;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditService;
import com.tiny.platform.infrastructure.auth.resource.domain.ApiEndpointEntry;
import com.tiny.platform.infrastructure.auth.resource.repository.ApiEndpointEntryRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.ApiEndpointPermissionRequirementRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.CarrierPermissionRequirementRow;
import com.tiny.platform.infrastructure.auth.resource.repository.ResourceRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.UiActionEntryRepository;
import com.tiny.platform.infrastructure.auth.resource.service.CarrierCompatibilitySafetyService;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
    private static final long API_ENDPOINT_ID = 61101L;
    private static final long REQUIRED_PERMISSION_ID = 62101L;
    private static final String REQUIRED_AUTH = "system:audit:auth:view";

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

        ApiEndpointEntry entry = new ApiEndpointEntry();
        entry.setId(API_ENDPOINT_ID);
        entry.setTenantId(TENANT_ID);
        entry.setResourceLevel("TENANT");
        entry.setName("audit-authorization-list");
        entry.setTitle("authorization audit list");
        entry.setUri("/sys/audit/authorization");
        entry.setMethod("GET");
        entry.setPermission(REQUIRED_AUTH);
        entry.setRequiredPermissionId(REQUIRED_PERMISSION_ID);
        entry.setEnabled(true);

        when(apiEndpointEntryRepository.findAll(
            Mockito.<Specification<ApiEndpointEntry>>any(),
            Mockito.<Sort>any()
        )).thenReturn(List.of(entry));

        Page<AuthorizationAuditLog> empty = new PageImpl<>(List.of());
        when(auditService.search(any(AuthorizationAuditQuery.class), any(Pageable.class))).thenReturn(empty);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void audit_realAuthorizationAuditController_sysAuditAuthorization_allow_shouldReturn200_whenRequirementSatisfied_staticUri()
        throws Exception {
        CarrierPermissionRequirementRow row = requirementRow(true);
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(row));

        mockMvc.perform(get("/sys/audit/authorization")
                .accept(MediaType.APPLICATION_JSON)
                .with(user("audit-viewer").authorities(new SimpleGrantedAuthority(REQUIRED_AUTH))))
            .andExpect(status().isOk());
    }

    @Test
    void audit_realAuthorizationAuditController_sysAuditAuthorization_deny_shouldReturn403_whenPermissionDisabled_staticUri()
        throws Exception {
        CarrierPermissionRequirementRow row = requirementRow(false);
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(row));

        mockMvc.perform(get("/sys/audit/authorization")
                .accept(MediaType.APPLICATION_JSON)
                .with(user("audit-viewer").authorities(new SimpleGrantedAuthority(REQUIRED_AUTH))))
            .andExpect(status().isForbidden());
    }

    private static CarrierPermissionRequirementRow requirementRow(boolean permissionEnabled) {
        CarrierPermissionRequirementRow row = Mockito.mock(CarrierPermissionRequirementRow.class);
        Mockito.when(row.getCarrierId()).thenReturn(API_ENDPOINT_ID);
        Mockito.when(row.getRequirementGroup()).thenReturn(0);
        Mockito.when(row.getSortOrder()).thenReturn(1);
        Mockito.when(row.getPermissionCode()).thenReturn(REQUIRED_AUTH);
        Mockito.when(row.getNegated()).thenReturn(false);
        Mockito.when(row.getPermissionEnabled()).thenReturn(permissionEnabled);
        return row;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
        org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
        org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration.class,
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
            ResourceRepository resourceRepository = Mockito.mock(ResourceRepository.class);
            RoleRepository roleRepository = Mockito.mock(RoleRepository.class);
            EffectiveRoleResolutionService effectiveRoleResolutionService = Mockito.mock(EffectiveRoleResolutionService.class);
            TenantUserRepository tenantUserRepository = Mockito.mock(TenantUserRepository.class);
            UserUnitRepository userUnitRepository = Mockito.mock(UserUnitRepository.class);
            MenuEntryRepository menuEntryRepository = Mockito.mock(MenuEntryRepository.class);
            UiActionEntryRepository uiActionEntryRepository = Mockito.mock(UiActionEntryRepository.class);
            ResourcePermissionBindingService resourcePermissionBindingService = Mockito.mock(ResourcePermissionBindingService.class);
            CarrierCompatibilitySafetyService carrierCompatibilitySafetyService = Mockito.mock(CarrierCompatibilitySafetyService.class);

            CarrierPermissionRequirementEvaluator evaluator = new CarrierPermissionRequirementEvaluator(
                Mockito.mock(MenuPermissionRequirementRepository.class),
                Mockito.mock(com.tiny.platform.infrastructure.auth.resource.repository.UiActionPermissionRequirementRepository.class),
                apiEndpointPermissionRequirementRepository
            );

            return new ResourceServiceImpl(
                resourceRepository,
                roleRepository,
                effectiveRoleResolutionService,
                tenantUserRepository,
                userUnitRepository,
                menuEntryRepository,
                uiActionEntryRepository,
                apiEndpointEntryRepository,
                resourcePermissionBindingService,
                carrierCompatibilitySafetyService,
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

