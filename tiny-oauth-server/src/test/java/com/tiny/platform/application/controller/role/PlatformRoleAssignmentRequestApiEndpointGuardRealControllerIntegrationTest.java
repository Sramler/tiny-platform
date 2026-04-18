package com.tiny.platform.application.controller.role;

import com.tiny.platform.application.controller.role.security.PlatformRoleApprovalAccessGuard;
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
import com.tiny.platform.infrastructure.auth.role.dto.PlatformRoleAssignmentRequestDtos.PlatformRoleAssignmentRequestResponseDto;
import com.tiny.platform.infrastructure.auth.role.service.PlatformRoleAssignmentRequestService;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.menu.repository.MenuEntryRepository;
import com.tiny.platform.infrastructure.menu.repository.MenuPermissionRequirementRepository;
import java.time.LocalDateTime;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = PlatformRoleAssignmentRequestApiEndpointGuardRealControllerIntegrationTest.TestApp.class
)
@AutoConfigureMockMvc
@ActiveProfiles("rbac-test")
class PlatformRoleAssignmentRequestApiEndpointGuardRealControllerIntegrationTest {

    private static final long REQUIRED_PERMISSION_ID = 96101L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiEndpointEntryRepository apiEndpointEntryRepository;

    @Autowired
    private ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository;

    @Autowired
    private PlatformRoleAssignmentRequestService platformRoleAssignmentRequestService;

    @BeforeEach
    void setUp() {
        TenantContext.setActiveTenantId(1L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        TenantContext.setTenantSource(TenantContext.SOURCE_UNKNOWN);
        when(platformRoleAssignmentRequestService.list(org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(Pageable.class)))
            .thenReturn(Page.empty());
        PlatformRoleAssignmentRequestResponseDto dto = new PlatformRoleAssignmentRequestResponseDto(
            1L,
            10L,
            7L,
            "ROLE_X",
            "X",
            "GRANT",
            "PENDING",
            1L,
            LocalDateTime.now(),
            null,
            null,
            null,
            null,
            null,
            null
        );
        when(platformRoleAssignmentRequestService.submit(any())).thenReturn(dto);
        when(platformRoleAssignmentRequestService.approve(anyLong(), any())).thenReturn(dto);
        when(platformRoleAssignmentRequestService.reject(anyLong(), any())).thenReturn(dto);
        doNothing().when(platformRoleAssignmentRequestService).cancel(anyLong());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void list_realController_allow_whenListPermission() throws Exception {
        ApiEndpointEntry entry = endpoint(96111L, "/platform/role-assignment-requests", "GET", "platform:role:approval:list");
        when(apiEndpointEntryRepository.findAll(
            Mockito.<Specification<ApiEndpointEntry>>any(),
            Mockito.<Sort>any()
        )).thenReturn(List.of(entry));
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(requirementRow(96111L, "platform:role:approval:list", true)));

        mockMvc.perform(get("/platform/role-assignment-requests")
                .accept(MediaType.APPLICATION_JSON)
                .with(user("a").authorities(new SimpleGrantedAuthority("platform:role:approval:list"))))
            .andExpect(status().isOk());
    }

    @Test
    void list_realController_deny_whenRequirementUnsatisfied() throws Exception {
        ApiEndpointEntry entry = endpoint(96112L, "/platform/role-assignment-requests", "GET", "platform:role:approval:list");
        when(apiEndpointEntryRepository.findAll(
            Mockito.<Specification<ApiEndpointEntry>>any(),
            Mockito.<Sort>any()
        )).thenReturn(List.of(entry));
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(requirementRow(96112L, "platform:role:approval:list", true)));

        mockMvc.perform(get("/platform/role-assignment-requests")
                .accept(MediaType.APPLICATION_JSON)
                .with(user("a").authorities(new SimpleGrantedAuthority("platform:user:list"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void list_realController_allow_whenSubmitPermissionMatchesOrGroup() throws Exception {
        ApiEndpointEntry entry = endpoint(96117L, "/platform/role-assignment-requests", "GET", "platform:role:approval:list");
        when(apiEndpointEntryRepository.findAll(
            Mockito.<Specification<ApiEndpointEntry>>any(),
            Mockito.<Sort>any()
        )).thenReturn(List.of(entry));
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(
                requirementRow(96117L, 0, "platform:role:approval:list", true),
                requirementRow(96117L, 1, "platform:role:approval:submit", true)
            ));

        mockMvc.perform(get("/platform/role-assignment-requests")
                .accept(MediaType.APPLICATION_JSON)
                .with(user("a").authorities(new SimpleGrantedAuthority("platform:role:approval:submit"))))
            .andExpect(status().isOk());
    }

    @Test
    void submit_realController_allow_whenSubmitPermission() throws Exception {
        ApiEndpointEntry entry = endpoint(96113L, "/platform/role-assignment-requests", "POST", "platform:role:approval:submit");
        when(apiEndpointEntryRepository.findAll(
            Mockito.<Specification<ApiEndpointEntry>>any(),
            Mockito.<Sort>any()
        )).thenReturn(List.of(entry));
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(requirementRow(96113L, "platform:role:approval:submit", true)));

        mockMvc.perform(post("/platform/role-assignment-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUserId\":1,\"roleId\":2,\"actionType\":\"GRANT\"}")
                .header("X-Idempotency-Key", "k1")
                .with(user("a").authorities(new SimpleGrantedAuthority("platform:role:approval:submit"))))
            .andExpect(status().isOk());
    }

    @Test
    void approve_realController_allow_whenApprovePermission() throws Exception {
        ApiEndpointEntry entry = endpoint(96114L, "/platform/role-assignment-requests/{id}/approve", "POST", "platform:role:approval:approve");
        when(apiEndpointEntryRepository.findAll(
            Mockito.<Specification<ApiEndpointEntry>>any(),
            Mockito.<Sort>any()
        )).thenReturn(List.of(entry));
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(requirementRow(96114L, "platform:role:approval:approve", true)));

        mockMvc.perform(post("/platform/role-assignment-requests/9/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .header("X-Idempotency-Key", "k2")
                .with(user("a").authorities(new SimpleGrantedAuthority("platform:role:approval:approve"))))
            .andExpect(status().isOk());
    }

    @Test
    void reject_realController_allow_whenRejectPermission() throws Exception {
        ApiEndpointEntry entry = endpoint(96115L, "/platform/role-assignment-requests/{id}/reject", "POST", "platform:role:approval:reject");
        when(apiEndpointEntryRepository.findAll(
            Mockito.<Specification<ApiEndpointEntry>>any(),
            Mockito.<Sort>any()
        )).thenReturn(List.of(entry));
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(requirementRow(96115L, "platform:role:approval:reject", true)));

        mockMvc.perform(post("/platform/role-assignment-requests/9/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .header("X-Idempotency-Key", "k3")
                .with(user("a").authorities(new SimpleGrantedAuthority("platform:role:approval:reject"))))
            .andExpect(status().isOk());
    }

    @Test
    void cancel_realController_allow_whenCancelPermission() throws Exception {
        ApiEndpointEntry entry = endpoint(96116L, "/platform/role-assignment-requests/{id}/cancel", "POST", "platform:role:approval:cancel");
        when(apiEndpointEntryRepository.findAll(
            Mockito.<Specification<ApiEndpointEntry>>any(),
            Mockito.<Sort>any()
        )).thenReturn(List.of(entry));
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection()))
            .thenReturn(List.of(requirementRow(96116L, "platform:role:approval:cancel", true)));

        mockMvc.perform(post("/platform/role-assignment-requests/9/cancel")
                .header("X-Idempotency-Key", "k4")
                .with(user("a").authorities(new SimpleGrantedAuthority("platform:role:approval:cancel"))))
            .andExpect(status().isNoContent());
    }

    private static ApiEndpointEntry endpoint(long id, String uri, String method, String permission) {
        ApiEndpointEntry e = new ApiEndpointEntry();
        e.setId(id);
        e.setTenantId(null);
        e.setResourceLevel("PLATFORM");
        e.setName("platform-role-assignment-requests");
        e.setTitle("platform role assignment requests");
        e.setUri(uri);
        e.setMethod(method);
        e.setPermission(permission);
        e.setRequiredPermissionId(REQUIRED_PERMISSION_ID);
        e.setEnabled(true);
        return e;
    }

    private static CarrierPermissionRequirementRow requirementRow(long carrierId, String permissionCode, boolean permissionEnabled) {
        return requirementRow(carrierId, 0, permissionCode, permissionEnabled);
    }

    private static CarrierPermissionRequirementRow requirementRow(long carrierId, int requirementGroup, String permissionCode,
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
        public PlatformRoleAssignmentRequestController platformRoleAssignmentRequestController(
            PlatformRoleAssignmentRequestService platformRoleAssignmentRequestService
        ) {
            return new PlatformRoleAssignmentRequestController(platformRoleAssignmentRequestService);
        }

        @Bean
        public PlatformRoleAssignmentRequestService platformRoleAssignmentRequestService() {
            return Mockito.mock(PlatformRoleAssignmentRequestService.class);
        }

        @Bean("platformRoleApprovalAccessGuard")
        public PlatformRoleApprovalAccessGuard platformRoleApprovalAccessGuard() {
            return new PlatformRoleApprovalAccessGuard();
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
