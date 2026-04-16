package com.tiny.platform.application.controller.user;

import com.tiny.platform.application.controller.user.security.PlatformUserManagementAccessGuard;
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
import com.tiny.platform.infrastructure.auth.user.service.PlatformUserManagementService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = PlatformUserManagementApiEndpointGuardRealControllerIntegrationTest.TestApp.class
)
@AutoConfigureMockMvc
@ActiveProfiles("rbac-test")
class PlatformUserManagementApiEndpointGuardRealControllerIntegrationTest {

    private static final long REQUIRED_PERMISSION_ID = 94301L;
    private static final String REQUIRED_AUTH = "platform:user:list";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiEndpointEntryRepository apiEndpointEntryRepository;

    @Autowired
    private ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository;

    @Autowired
    private PlatformUserManagementService platformUserManagementService;

    @BeforeEach
    void setUp() {
        TenantContext.setActiveTenantId(1L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        TenantContext.setTenantSource(TenantContext.SOURCE_UNKNOWN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void platformUsers_realController_allow_shouldReturn200_whenRequirementSatisfied() throws Exception {
        ApiEndpointEntry listEntry = endpoint(94311L, "/platform/users", "GET", REQUIRED_AUTH);
        when(apiEndpointEntryRepository.findAll(
            Mockito.<Specification<ApiEndpointEntry>>any(),
            Mockito.<Sort>any()
        )).thenReturn(List.of(listEntry));
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(requirementRow(94311L, REQUIRED_AUTH, true)));
        when(platformUserManagementService.list(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(Pageable.class)))
            .thenReturn(Page.empty());

        mockMvc.perform(get("/platform/users")
                .accept(MediaType.APPLICATION_JSON)
                .with(user("platform-admin").authorities(new SimpleGrantedAuthority(REQUIRED_AUTH))))
            .andExpect(status().isOk());
    }

    @Test
    void platformUsers_realController_allow_shouldReturn200_whenViewAuthorityMatchesReadOrGroup() throws Exception {
        ApiEndpointEntry listEntry = endpoint(94314L, "/platform/users", "GET", REQUIRED_AUTH);
        when(apiEndpointEntryRepository.findAll(
            Mockito.<Specification<ApiEndpointEntry>>any(),
            Mockito.<Sort>any()
        )).thenReturn(List.of(listEntry));
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(
                requirementRow(94314L, 0, REQUIRED_AUTH, true),
                requirementRow(94314L, 1, "platform:user:view", true)
            ));
        when(platformUserManagementService.list(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(Pageable.class)))
            .thenReturn(Page.empty());

        mockMvc.perform(get("/platform/users")
                .accept(MediaType.APPLICATION_JSON)
                .with(user("platform-viewer").authorities(new SimpleGrantedAuthority("platform:user:view"))))
            .andExpect(status().isOk());
    }

    @Test
    void platformUserDetail_realController_allow_shouldReturn200_whenListAuthorityMatchesReadOrGroup() throws Exception {
        ApiEndpointEntry detailEntry = endpoint(94315L, "/platform/users/{id}", "GET", "platform:user:view");
        when(apiEndpointEntryRepository.findAll(
            Mockito.<Specification<ApiEndpointEntry>>any(),
            Mockito.<Sort>any()
        )).thenReturn(List.of(detailEntry));
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(
                requirementRow(94315L, 0, "platform:user:view", true),
                requirementRow(94315L, 1, "platform:user:list", true)
            ));
        when(platformUserManagementService.get(9L)).thenReturn(java.util.Optional.of(
            new com.tiny.platform.infrastructure.auth.user.dto.PlatformUserManagementDtos.PlatformUserDetailDto(
                9L,
                "platform_admin",
                "平台管理员",
                "平台管理员",
                "platform@example.com",
                "13800000000",
                true,
                true,
                true,
                true,
                "ACTIVE",
                true,
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now()
            )
        ));

        mockMvc.perform(get("/platform/users/9")
                .accept(MediaType.APPLICATION_JSON)
                .with(user("platform-reader").authorities(new SimpleGrantedAuthority("platform:user:list"))))
            .andExpect(status().isOk());
    }

    @Test
    void platformUserStatus_realController_allow_shouldReturn200_whenEditAuthorityMatchesUpdateOrGroup() throws Exception {
        ApiEndpointEntry patchEntry = endpoint(94316L, "/platform/users/{id}/status", "PATCH", "platform:user:disable");
        when(apiEndpointEntryRepository.findAll(
            Mockito.<Specification<ApiEndpointEntry>>any(),
            Mockito.<Sort>any()
        )).thenReturn(List.of(patchEntry));
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(
                requirementRow(94316L, 0, "platform:user:disable", true),
                requirementRow(94316L, 1, "platform:user:edit", true)
            ));
        when(platformUserManagementService.updateStatus(9L, "DISABLED")).thenReturn(true);

        mockMvc.perform(patch("/platform/users/9/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DISABLED\"}")
                .with(user("platform-editor").authorities(new SimpleGrantedAuthority("platform:user:edit"))))
            .andExpect(status().isOk());
    }

    @Test
    void platformUsers_realController_deny_shouldReturn403_whenUnauthorized() throws Exception {
        ApiEndpointEntry listEntry = endpoint(94312L, "/platform/users", "GET", REQUIRED_AUTH);
        when(apiEndpointEntryRepository.findAll(
            Mockito.<Specification<ApiEndpointEntry>>any(),
            Mockito.<Sort>any()
        )).thenReturn(List.of(listEntry));
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(requirementRow(94312L, REQUIRED_AUTH, true)));

        mockMvc.perform(get("/platform/users")
                .accept(MediaType.APPLICATION_JSON)
                .with(user("tenant-admin").authorities(new SimpleGrantedAuthority("system:user:list"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void platformUsers_realController_deny_shouldReturn403_whenTenantScope() throws Exception {
        TenantContext.setActiveTenantId(7L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);
        ApiEndpointEntry listEntry = endpoint(94313L, "/platform/users", "GET", REQUIRED_AUTH);
        when(apiEndpointEntryRepository.findAll(
            Mockito.<Specification<ApiEndpointEntry>>any(),
            Mockito.<Sort>any()
        )).thenReturn(List.of(listEntry));
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(requirementRow(94313L, REQUIRED_AUTH, true)));

        mockMvc.perform(get("/platform/users")
                .accept(MediaType.APPLICATION_JSON)
                .with(user("platform-admin").authorities(new SimpleGrantedAuthority(REQUIRED_AUTH))))
            .andExpect(status().isForbidden());
    }

    private static ApiEndpointEntry endpoint(long id, String uri, String method, String permission) {
        ApiEndpointEntry entry = new ApiEndpointEntry();
        entry.setId(id);
        entry.setTenantId(null);
        entry.setResourceLevel("PLATFORM");
        entry.setName("platform-users");
        entry.setTitle("platform users");
        entry.setUri(uri);
        entry.setMethod(method);
        entry.setPermission(permission);
        entry.setRequiredPermissionId(REQUIRED_PERMISSION_ID);
        entry.setEnabled(true);
        return entry;
    }

    private static CarrierPermissionRequirementRow requirementRow(long carrierId,
                                                                  int requirementGroup,
                                                                  String permissionCode,
                                                                  boolean permissionEnabled) {
        return new CarrierPermissionRequirementRow() {
            @Override
            public Long getCarrierId() {
                return carrierId;
            }

            @Override
            public Integer getRequirementGroup() {
                return requirementGroup;
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

    private static CarrierPermissionRequirementRow requirementRow(long carrierId, String permissionCode, boolean permissionEnabled) {
        return requirementRow(carrierId, 0, permissionCode, permissionEnabled);
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
        public PlatformUserManagementController platformUserManagementController(
            PlatformUserManagementService platformUserManagementService
        ) {
            return new PlatformUserManagementController(platformUserManagementService);
        }

        @Bean
        public PlatformUserManagementService platformUserManagementService() {
            return Mockito.mock(PlatformUserManagementService.class);
        }

        @Bean("platformUserManagementAccessGuard")
        public PlatformUserManagementAccessGuard platformUserManagementAccessGuard() {
            return new PlatformUserManagementAccessGuard();
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
