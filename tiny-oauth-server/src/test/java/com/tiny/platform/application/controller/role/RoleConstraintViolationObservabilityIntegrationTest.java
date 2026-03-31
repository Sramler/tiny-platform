package com.tiny.platform.application.controller.role;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tiny.platform.OauthServerApplication;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.domain.RoleMutex;
import com.tiny.platform.infrastructure.auth.role.integration.Rbac3RoleConstraintIntegrationTestCleanup;
import com.tiny.platform.infrastructure.auth.role.repository.RoleAssignmentRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleConstraintViolationLogRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleMutexRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.role.service.RoleAssignmentSyncService;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = OauthServerApplication.class,
    properties = {
        "authentication.jwt.public-key-path=classpath:keys/public.pem",
        "authentication.jwt.private-key-path=classpath:keys/private.pem",
        "authentication.clients[0].client-id=test-client",
        "authentication.clients[0].client-secret=test-secret",
        "authentication.clients[0].authentication-methods[0]=client_secret_basic",
        "authentication.clients[0].grant-types[0]=authorization_code",
        "authentication.clients[0].redirect-uris[0]=http://localhost:9000/",
        "authentication.clients[0].scopes[0]=openid",
        "authentication.clients[0].client-setting.require-authorization-consent=false"
    }
)
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
@EnabledIfEnvironmentVariable(named = "E2E_DB_PASSWORD", matches = ".+")
@SuppressWarnings({"null"})
class RoleConstraintViolationObservabilityIntegrationTest {

    private static final Long TENANT_ID = 1L;
    private static final String TENANT_HEADER = "X-Active-Tenant-Id";
    private static final String TENANT_SESSION_KEY = "AUTH_ACTIVE_TENANT_ID";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RoleMutexRepository roleMutexRepository;

    @Autowired
    private RoleAssignmentSyncService roleAssignmentSyncService;

    @Autowired
    private RoleAssignmentRepository roleAssignmentRepository;

    @Autowired
    private TenantUserRepository tenantUserRepository;

    @Autowired
    private RoleConstraintViolationLogRepository violationLogRepository;

    private Long userId;
    private Long roleAId;
    private Long roleBId;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        if (userId != null) {
            Rbac3RoleConstraintIntegrationTestCleanup.purgeUserTenantArtifacts(
                TENANT_ID,
                userId,
                roleAssignmentRepository,
                violationLogRepository,
                tenantUserRepository,
                userRepository
            );
            userId = null;
        }
        if (roleAId != null && roleBId != null) {
            try {
                roleMutexRepository.deleteByTenantIdAndLeftRoleIdAndRightRoleId(
                    TENANT_ID,
                    Math.min(roleAId, roleBId),
                    Math.max(roleAId, roleBId)
                );
            } catch (RuntimeException ignored) {
                // best-effort
            }
        }
        Rbac3RoleConstraintIntegrationTestCleanup.deleteRoleBestEffort(roleRepository, roleAId);
        Rbac3RoleConstraintIntegrationTestCleanup.deleteRoleBestEffort(roleRepository, roleBId);
        roleAId = null;
        roleBId = null;
    }

    @Test
    void violation_shouldBeQueryable_viaViolationsEndpoint() throws Exception {
        User userEntity = new User();
        userEntity.setTenantId(TENANT_ID);
        userEntity.setUsername("rbac3_violation_obs_user_" + System.currentTimeMillis());
        userEntity.setNickname("rbac3 violation obs");
        userEntity = userRepository.save(userEntity);
        userId = userEntity.getId();

        long ts = System.currentTimeMillis();
        Role roleA = new Role();
        roleA.setTenantId(TENANT_ID);
        roleA.setRoleLevel("TENANT");
        roleA.setCode("ROLE_RBAC3_OBS_A_" + ts);
        roleA.setName("RBAC3 OBS A " + ts);
        roleA.setEnabled(true);
        roleA.setBuiltin(false);
        roleA = roleRepository.save(roleA);
        roleAId = roleA.getId();

        Role roleB = new Role();
        roleB.setTenantId(TENANT_ID);
        roleB.setRoleLevel("TENANT");
        roleB.setCode("ROLE_RBAC3_OBS_B_" + ts);
        roleB.setName("RBAC3 OBS B " + ts);
        roleB.setEnabled(true);
        roleB.setBuiltin(false);
        roleB = roleRepository.save(roleB);
        roleBId = roleB.getId();

        roleMutexRepository.deleteByTenantIdAndLeftRoleIdAndRightRoleId(TENANT_ID, Math.min(roleAId, roleBId), Math.max(roleAId, roleBId));
        RoleMutex mutex = new RoleMutex();
        mutex.setTenantId(TENANT_ID);
        mutex.setLeftRoleId(Math.min(roleAId, roleBId));
        mutex.setRightRoleId(Math.max(roleAId, roleBId));
        roleMutexRepository.save(mutex);

        // Trigger a dry-run mutex violation log by granting both roles.
        roleAssignmentSyncService.replaceUserTenantRoleAssignments(userId, TENANT_ID, List.of(roleAId, roleBId));

        var principal = user("role-permission-assigner")
            .authorities(new SimpleGrantedAuthority("system:role:permission:assign"));

        mockMvc.perform(get("/sys/role-constraints/violations")
                .param("principalId", String.valueOf(userId))
                .param("violationType", "MUTEX")
                .param("size", "10")
                .param("sort", "createdAt,desc")
                .header(TENANT_HEADER, TENANT_ID.toString())
                .sessionAttr(TENANT_SESSION_KEY, TENANT_ID)
                .with(principal))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.content[0].principalId", equalTo(userId.intValue())))
            .andExpect(jsonPath("$.content[0].violationType", equalTo("MUTEX")))
            .andExpect(jsonPath("$.content[0].violationCode", equalTo("ROLE_CONFLICT_MUTEX")));
    }
}

