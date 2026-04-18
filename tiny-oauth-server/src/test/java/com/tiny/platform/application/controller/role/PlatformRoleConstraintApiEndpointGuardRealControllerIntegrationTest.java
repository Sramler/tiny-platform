package com.tiny.platform.application.controller.role;

import com.tiny.platform.application.controller.role.security.RoleManagementAccessGuard;
import com.tiny.platform.core.oauth.security.ApiEndpointRequirementFilter;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditService;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
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
import com.tiny.platform.infrastructure.auth.role.service.RoleConstraintRuleAdminService;
import com.tiny.platform.infrastructure.auth.role.service.RoleConstraintViolationLogQueryService;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.menu.repository.MenuEntryRepository;
import com.tiny.platform.infrastructure.menu.repository.MenuPermissionRequirementRepository;
import java.util.List;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = PlatformRoleConstraintApiEndpointGuardRealControllerIntegrationTest.TestApp.class
)
@AutoConfigureMockMvc
@ActiveProfiles("rbac-test")
class PlatformRoleConstraintApiEndpointGuardRealControllerIntegrationTest {

    private static final long REQUIRED_PERMISSION_ID = 95601L;
    private static final String REQUIRED_AUTH = "system:role:constraint:view";
    private static final String EDIT_AUTH = "system:role:constraint:edit";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiEndpointEntryRepository apiEndpointEntryRepository;

    @Autowired
    private ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository;

    @Autowired
    private RoleConstraintRuleAdminService adminService;

    @BeforeEach
    void setUp() {
        TenantContext.setActiveTenantId(1L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        TenantContext.setTenantSource(TenantContext.SOURCE_UNKNOWN);
        when(adminService.listPlatformRoleHierarchyEdges()).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void platformRoleConstraints_realController_allow_shouldReturn200_whenRequirementSatisfied() throws Exception {
        ApiEndpointEntry entry = endpoint(95611L, "/platform/role-constraints/hierarchy", "GET", REQUIRED_AUTH);
        when(apiEndpointEntryRepository.findAll(
            Mockito.<Specification<ApiEndpointEntry>>any(),
            Mockito.<Sort>any()
        )).thenReturn(List.of(entry));
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(requirementRow(95611L, REQUIRED_AUTH, true)));

        mockMvc.perform(get("/platform/role-constraints/hierarchy")
                .with(user("platform-viewer").authorities(new SimpleGrantedAuthority(REQUIRED_AUTH))))
            .andExpect(status().isOk());
    }

    @Test
    void platformRoleConstraints_realController_deny_shouldReturn403_whenRequirementUnsatisfied() throws Exception {
        ApiEndpointEntry entry = endpoint(95612L, "/platform/role-constraints/hierarchy", "GET", REQUIRED_AUTH);
        when(apiEndpointEntryRepository.findAll(
            Mockito.<Specification<ApiEndpointEntry>>any(),
            Mockito.<Sort>any()
        )).thenReturn(List.of(entry));
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(requirementRow(95612L, REQUIRED_AUTH, true)));

        mockMvc.perform(get("/platform/role-constraints/hierarchy")
                .with(user("platform-viewer").authorities(new SimpleGrantedAuthority("system:role:list"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void platformRoleConstraints_deleteEndpoints_shouldReturn204_whenRequirementSatisfied() throws Exception {
        ApiEndpointEntry deleteHierarchy = endpoint(95621L, "/platform/role-constraints/hierarchy", "DELETE", EDIT_AUTH);
        ApiEndpointEntry deleteMutex = endpoint(95622L, "/platform/role-constraints/mutex", "DELETE", EDIT_AUTH);
        ApiEndpointEntry deletePrerequisite = endpoint(95623L, "/platform/role-constraints/prerequisite", "DELETE", EDIT_AUTH);
        ApiEndpointEntry deleteCardinality = endpoint(95624L, "/platform/role-constraints/cardinality", "DELETE", EDIT_AUTH);
        when(apiEndpointEntryRepository.findAll(
            Mockito.<Specification<ApiEndpointEntry>>any(),
            Mockito.<Sort>any()
        )).thenReturn(List.of(deleteHierarchy, deleteMutex, deletePrerequisite, deleteCardinality));
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(
                requirementRow(95621L, EDIT_AUTH, true),
                requirementRow(95622L, EDIT_AUTH, true),
                requirementRow(95623L, EDIT_AUTH, true),
                requirementRow(95624L, EDIT_AUTH, true)
            ));

        mockMvc.perform(delete("/platform/role-constraints/hierarchy")
                .param("childRoleId", "10")
                .param("parentRoleId", "11")
                .with(user("platform-editor").authorities(new SimpleGrantedAuthority(EDIT_AUTH))))
            .andExpect(status().isNoContent());
        mockMvc.perform(delete("/platform/role-constraints/mutex")
                .param("roleIdA", "10")
                .param("roleIdB", "11")
                .with(user("platform-editor").authorities(new SimpleGrantedAuthority(EDIT_AUTH))))
            .andExpect(status().isNoContent());
        mockMvc.perform(delete("/platform/role-constraints/prerequisite")
                .param("roleId", "10")
                .param("requiredRoleId", "11")
                .with(user("platform-editor").authorities(new SimpleGrantedAuthority(EDIT_AUTH))))
            .andExpect(status().isNoContent());
        mockMvc.perform(delete("/platform/role-constraints/cardinality")
                .param("roleId", "10")
                .param("scopeType", "PLATFORM")
                .with(user("platform-editor").authorities(new SimpleGrantedAuthority(EDIT_AUTH))))
            .andExpect(status().isNoContent());
    }

    @Test
    void platformRoleConstraints_deleteEndpoints_shouldReturn403_whenEditRequirementUnsatisfied() throws Exception {
        ApiEndpointEntry deleteHierarchy = endpoint(95631L, "/platform/role-constraints/hierarchy", "DELETE", EDIT_AUTH);
        when(apiEndpointEntryRepository.findAll(
            Mockito.<Specification<ApiEndpointEntry>>any(),
            Mockito.<Sort>any()
        )).thenReturn(List.of(deleteHierarchy));
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(requirementRow(95631L, EDIT_AUTH, true)));

        mockMvc.perform(delete("/platform/role-constraints/hierarchy")
                .param("childRoleId", "10")
                .param("parentRoleId", "11")
                .with(user("platform-viewer").authorities(new SimpleGrantedAuthority(REQUIRED_AUTH))))
            .andExpect(status().isForbidden());
    }

    private static ApiEndpointEntry endpoint(long id, String uri, String method, String permission) {
        ApiEndpointEntry entry = new ApiEndpointEntry();
        entry.setId(id);
        entry.setTenantId(null);
        entry.setResourceLevel("PLATFORM");
        entry.setName("platform-role-constraints");
        entry.setTitle("platform role constraints");
        entry.setUri(uri);
        entry.setMethod(method);
        entry.setPermission(permission);
        entry.setRequiredPermissionId(REQUIRED_PERMISSION_ID);
        entry.setEnabled(true);
        return entry;
    }

    private static CarrierPermissionRequirementRow requirementRow(long carrierId, String permissionCode, boolean permissionEnabled) {
        return new CarrierPermissionRequirementRow() {
            @Override
            public Long getCarrierId() {
                return carrierId;
            }

            @Override
            public Integer getRequirementGroup() {
                return 0;
            }

            @Override
            public Integer getSortOrder() {
                return 1;
            }

            @Override
            public String getPermissionCode() {
                return permissionCode;
            }

            @Override
            public Boolean getNegated() {
                return false;
            }

            @Override
            public Boolean getPermissionEnabled() {
                return permissionEnabled;
            }
        };
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
    @EnableMethodSecurity(prePostEnabled = true)
    @Import(TestConfig.class)
    static class TestApp {}

    static class TestConfig {
        @Bean
        public PlatformRoleConstraintRuleController platformRoleConstraintRuleController(
            RoleConstraintRuleAdminService adminService,
            RoleConstraintViolationLogQueryService violationLogQueryService
        ) {
            return new PlatformRoleConstraintRuleController(adminService, violationLogQueryService);
        }

        @Bean
        public RoleConstraintRuleAdminService roleConstraintRuleAdminService() {
            return Mockito.mock(RoleConstraintRuleAdminService.class);
        }

        @Bean
        public RoleConstraintViolationLogQueryService roleConstraintViolationLogQueryService() {
            return Mockito.mock(RoleConstraintViolationLogQueryService.class);
        }

        @Bean("roleManagementAccessGuard")
        public RoleManagementAccessGuard roleManagementAccessGuard() {
            return new RoleManagementAccessGuard();
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
        public SecurityFilterChain securityFilterChain(
            org.springframework.security.config.annotation.web.builders.HttpSecurity http,
            ApiEndpointRequirementFilter apiEndpointRequirementFilter
        ) throws Exception {
            http.csrf(csrf -> csrf.disable());
            http.authorizeHttpRequests(registry -> registry.anyRequest().permitAll());
            http.addFilterAfter(apiEndpointRequirementFilter, AnonymousAuthenticationFilter.class);
            return http.build();
        }
    }
}
