package com.tiny.platform.application.controller.audit;

import com.tiny.platform.application.controller.audit.security.AuthenticationAuditAccessGuard;
import com.tiny.platform.core.oauth.security.ApiEndpointRequirementFilter;
import com.tiny.platform.core.oauth.service.AuthenticationAuditService;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.core.oauth.tenant.TenantLifecycleAccessGuard;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditService;
import com.tiny.platform.infrastructure.auth.resource.domain.ApiEndpointEntry;
import com.tiny.platform.infrastructure.auth.resource.repository.ApiEndpointEntryRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.ApiEndpointPermissionRequirementRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.CarrierPermissionRequirementRow;
import com.tiny.platform.infrastructure.auth.resource.repository.UiActionEntryRepository;
import com.tiny.platform.infrastructure.auth.resource.service.CarrierPermissionRequirementEvaluator;
import com.tiny.platform.infrastructure.auth.resource.service.CarrierPermissionReferenceSafetyService;
import com.tiny.platform.infrastructure.auth.resource.service.ResourcePermissionBindingService;
import com.tiny.platform.infrastructure.auth.resource.service.ResourceService;
import com.tiny.platform.infrastructure.auth.resource.service.ResourceServiceImpl;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.role.service.EffectiveRoleResolutionService;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationAudit;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = AuthenticationAuditControllerApiEndpointGuardRealControllerIntegrationTest.TestApp.class
)
@AutoConfigureMockMvc
@ActiveProfiles("rbac-test")
class AuthenticationAuditControllerApiEndpointGuardRealControllerIntegrationTest {

    private static final long TENANT_ID = 9L;
    private static final long API_ENDPOINT_ID = 85101L;
    private static final long REQUIRED_PERMISSION_ID = 85201L;
    private static final String REQUIRED_AUTH = "system:audit:authentication:view";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiEndpointEntryRepository apiEndpointEntryRepository;

    @Autowired
    private ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository;

    @Autowired
    private AuthenticationAuditService authenticationAuditService;

    @Autowired
    private TenantLifecycleAccessGuard tenantLifecycleAccessGuard;

    @BeforeEach
    void setUp() {
        TenantContext.setActiveTenantId(TENANT_ID);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);
        TenantContext.setTenantSource(TenantContext.SOURCE_UNKNOWN);

        Mockito.doNothing().when(tenantLifecycleAccessGuard)
            .assertPlatformTargetTenantReadable(Mockito.anyLong(), Mockito.anyString());

        UserAuthenticationAudit record = new UserAuthenticationAudit();
        record.setId(1L);
        record.setTenantId(TENANT_ID);
        record.setUsername("alice");
        record.setSuccess(true);
        record.setEventType("LOGIN_SUCCESS");
        record.setCreatedAt(LocalDateTime.of(2026, 3, 1, 10, 0));
        when(authenticationAuditService.search(any(), any()))
            .thenReturn(new PageImpl<>(List.of(record)));

        ApiEndpointEntry entry = new ApiEndpointEntry();
        entry.setId(API_ENDPOINT_ID);
        entry.setTenantId(TENANT_ID);
        entry.setResourceLevel("TENANT");
        entry.setName("audit-authentication-list");
        entry.setTitle("authentication audit list");
        entry.setUri("/sys/audit/authentication");
        entry.setMethod("GET");
        entry.setPermission(REQUIRED_AUTH);
        entry.setRequiredPermissionId(REQUIRED_PERMISSION_ID);
        entry.setEnabled(true);

        when(apiEndpointEntryRepository.findAll(
            Mockito.<Specification<ApiEndpointEntry>>any(),
            Mockito.<Sort>any()
        )).thenReturn(List.of(entry));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void audit_realAuthenticationAuditController_sysAuditAuthentication_allow_shouldReturn200_whenRequirementSatisfied_staticUri() throws Exception {
        CarrierPermissionRequirementRow row = requirementRow(true);
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(row));

        mockMvc.perform(get("/sys/audit/authentication")
                .param("page", "0")
                .param("size", "10")
                .accept(MediaType.APPLICATION_JSON)
                .with(user("audit-viewer").authorities(new SimpleGrantedAuthority(REQUIRED_AUTH))))
            .andExpect(status().isOk());
    }

    @Test
    void audit_realAuthenticationAuditController_sysAuditAuthentication_deny_shouldReturn403_whenPermissionDisabled_staticUri() throws Exception {
        CarrierPermissionRequirementRow row = requirementRow(false);
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(row));

        mockMvc.perform(get("/sys/audit/authentication")
                .param("page", "0")
                .param("size", "10")
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
    @EnableMethodSecurity(prePostEnabled = true)
    @Import(TestConfig.class)
    static class TestApp {}

    static class TestConfig {
        @Bean
        public AuthenticationAuditController authenticationAuditController(AuthenticationAuditService authenticationAuditService,
                                                                          TenantLifecycleAccessGuard tenantLifecycleAccessGuard) {
            return new AuthenticationAuditController(authenticationAuditService, tenantLifecycleAccessGuard);
        }

        @Bean
        public AuthenticationAuditService authenticationAuditService() {
            return Mockito.mock(AuthenticationAuditService.class);
        }

        @Bean
        public TenantLifecycleAccessGuard tenantLifecycleAccessGuard() {
            return Mockito.mock(TenantLifecycleAccessGuard.class);
        }

        @Bean("authenticationAuditAccessGuard")
        public AuthenticationAuditAccessGuard authenticationAuditAccessGuard() {
            return new AuthenticationAuditAccessGuard();
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
        public AuthorizationAuditService authorizationAuditService() {
            return Mockito.mock(AuthorizationAuditService.class);
        }

        @Bean
        public ResourceService resourceService(ApiEndpointEntryRepository apiEndpointEntryRepository,
                                               ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository,
                                               AuthorizationAuditService authorizationAuditService) {
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
                authorizationAuditService
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

