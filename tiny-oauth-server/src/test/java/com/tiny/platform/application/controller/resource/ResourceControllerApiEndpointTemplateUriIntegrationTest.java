package com.tiny.platform.application.controller.resource;

import com.tiny.platform.core.oauth.security.ApiEndpointRequirementFilter;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
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
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.role.service.EffectiveRoleResolutionService;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.menu.repository.MenuEntryRepository;
import com.tiny.platform.infrastructure.menu.repository.MenuPermissionRequirementRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.UiActionPermissionRequirementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = ResourceControllerApiEndpointTemplateUriIntegrationTest.TemplateUriTestApp.class
)
@AutoConfigureMockMvc
@ActiveProfiles("rbac-test")
class ResourceControllerApiEndpointTemplateUriIntegrationTest {

    private static final long API_ENDPOINT_ID = 1000L;
    private static final long REQUIRED_PERMISSION_ID = 2000L;

    private static final String REQUIRED_AUTH = "system:resource:list";

    private long tenantId = 9L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiEndpointEntryRepository apiEndpointEntryRepository;

    @Autowired
    private ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository;

    @Autowired
    private AuthorizationAuditService authorizationAuditService;

    @BeforeEach
    void seedTenantContextAndEndpoint() {
        TenantContext.setActiveTenantId(tenantId);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);
        TenantContext.setTenantSource(TenantContext.SOURCE_UNKNOWN);

        Mockito.reset(apiEndpointPermissionRequirementRepository);
        Mockito.reset(authorizationAuditService);

        when(apiEndpointEntryRepository.findAll(
            Mockito.<org.springframework.data.jpa.domain.Specification<ApiEndpointEntry>>any(),
            Mockito.any(org.springframework.data.domain.Sort.class)
        ))
            .thenReturn(List.of());
    }

    @AfterEach
    void cleanupTenantContext() {
        TenantContext.clear();
    }

    @Test
    void templateUriAllow_shouldReturn200_whenRequirementSatisfied() throws Exception {
        stubRegisteredEndpoints(templateEndpoint());
        CarrierPermissionRequirementRow row = requirementRowWithPermissionEnabled(true);
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(row));

        mockMvc.perform(get("/sys/resources/{id}", 9L)
                .accept(MediaType.APPLICATION_JSON)
                .with(authenticatedTenantUser()))
            .andExpect(status().isOk());

        verify(apiEndpointPermissionRequirementRepository)
            .findRowsByApiEndpointIdIn(anyCollection());
    }

    @Test
    void templateUriDeny_shouldReturn403_whenPermissionEnabledFalse() throws Exception {
        stubRegisteredEndpoints(templateEndpoint());
        CarrierPermissionRequirementRow row = requirementRowWithPermissionEnabled(false);
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(row));

        mockMvc.perform(get("/sys/resources/{id}", 9L)
                .accept(MediaType.APPLICATION_JSON)
                .with(authenticatedTenantUser()))
            .andExpect(status().isForbidden());

        verify(apiEndpointPermissionRequirementRepository)
            .findRowsByApiEndpointIdIn(anyCollection());
    }

    @Test
    void unregisteredTemplateUri_shouldFailClosedInUnifiedGuard() throws Exception {
        stubRegisteredEndpoints(templateEndpoint());
        // 传入 URI 与模板 /sys/resources/{id} 不满足「段数一致」的严格匹配：
        // template: 3 segments, request: 4 segments => unregistered => fail-closed
        mockMvc.perform(get("/sys/resources/{id}/extra", 9L)
                .accept(MediaType.APPLICATION_JSON)
                .with(authenticatedTenantUser()))
            .andExpect(status().isForbidden());

        verify(apiEndpointPermissionRequirementRepository, never())
            .findRowsByApiEndpointIdIn(anyCollection());
    }

    @Test
    void staticUriAllow_shouldReturn200_whenRequirementSatisfied() throws Exception {
        ApiEndpointEntry endpoint = templateEndpoint();
        endpoint.setId(1001L);
        endpoint.setUri("/sys/resources");

        stubRegisteredEndpoints(endpoint);

        CarrierPermissionRequirementRow row = Mockito.mock(CarrierPermissionRequirementRow.class);
        Mockito.when(row.getCarrierId()).thenReturn(1001L);
        Mockito.when(row.getRequirementGroup()).thenReturn(0);
        Mockito.when(row.getSortOrder()).thenReturn(1);
        Mockito.when(row.getPermissionCode()).thenReturn(REQUIRED_AUTH);
        Mockito.when(row.getNegated()).thenReturn(false);
        Mockito.when(row.getPermissionEnabled()).thenReturn(true);

        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(row));

        mockMvc.perform(get("/sys/resources")
                .accept(MediaType.APPLICATION_JSON)
                .with(authenticatedTenantUser()))
            .andExpect(status().isOk());
    }

    private static CarrierPermissionRequirementRow requirementRowWithPermissionEnabled(boolean enabled) {
        CarrierPermissionRequirementRow row = Mockito.mock(CarrierPermissionRequirementRow.class);
        Mockito.when(row.getCarrierId()).thenReturn(API_ENDPOINT_ID);
        Mockito.when(row.getRequirementGroup()).thenReturn(0);
        Mockito.when(row.getSortOrder()).thenReturn(1);
        Mockito.when(row.getPermissionCode()).thenReturn(REQUIRED_AUTH);
        Mockito.when(row.getNegated()).thenReturn(false);
        Mockito.when(row.getPermissionEnabled()).thenReturn(enabled);
        return row;
    }

    private RequestPostProcessor authenticatedTenantUser() {
        return user("resource-reader").authorities(new SimpleGrantedAuthority(REQUIRED_AUTH));
    }

    private void stubRegisteredEndpoints(ApiEndpointEntry... endpoints) {
        when(apiEndpointEntryRepository.findAll(
            Mockito.<org.springframework.data.jpa.domain.Specification<ApiEndpointEntry>>any(),
            Mockito.any(org.springframework.data.domain.Sort.class)
        ))
            .thenReturn(List.of(endpoints));
    }

    private ApiEndpointEntry templateEndpoint() {
        ApiEndpointEntry endpoint = new ApiEndpointEntry();
        endpoint.setId(API_ENDPOINT_ID);
        endpoint.setTenantId(tenantId);
        endpoint.setResourceLevel("TENANT");
        endpoint.setName("sys-resources-get-by-id");
        endpoint.setTitle("sys resources get by id");
        endpoint.setUri("/sys/resources/{id}");
        endpoint.setMethod("GET");
        endpoint.setPermission(REQUIRED_AUTH);
        endpoint.setRequiredPermissionId(REQUIRED_PERMISSION_ID);
        endpoint.setEnabled(true);
        return endpoint;
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
    @Import({
        TemplateUriTestController.class,
        TemplateUriTestConfig.class
    })
    static class TemplateUriTestApp {}

    @RestController
    @RequestMapping
    static class TemplateUriTestController {
        @GetMapping("/sys/resources")
        public String list() {
            return "ok-list";
        }

        @GetMapping("/sys/resources/{id}")
        public String get(@PathVariable("id") Long id) {
            return "ok-" + id;
        }

        @GetMapping("/sys/resources/{id}/extra")
        public String getExtra(@PathVariable("id") Long id) {
            return "ok-extra-" + id;
        }
    }

    static class TemplateUriTestConfig {
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
                Mockito.mock(UiActionPermissionRequirementRepository.class),
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
        public org.springframework.security.web.SecurityFilterChain securityFilterChain(
            org.springframework.security.config.annotation.web.builders.HttpSecurity http,
            ApiEndpointRequirementFilter apiEndpointRequirementFilter
        ) throws Exception {
            http.csrf(csrf -> csrf.disable());
            http.authorizeHttpRequests(registry -> registry.anyRequest().permitAll());
            http.addFilterAfter(apiEndpointRequirementFilter, org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class);
            return http.build();
        }
    }
}
