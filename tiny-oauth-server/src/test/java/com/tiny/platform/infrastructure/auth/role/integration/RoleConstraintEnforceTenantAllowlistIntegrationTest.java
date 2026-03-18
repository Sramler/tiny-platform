package com.tiny.platform.infrastructure.auth.role.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tiny.platform.OauthServerApplication;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.domain.RoleMutex;
import com.tiny.platform.infrastructure.auth.role.repository.RoleAssignmentRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleMutexRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.role.service.RoleAssignmentSyncService;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    classes = OauthServerApplication.class,
    properties = {
        "tiny.platform.auth.rbac3.enforce=true",
        "tiny.platform.auth.rbac3.enforce-tenant-ids=999",
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
@ActiveProfiles("e2e")
@EnabledIfEnvironmentVariable(named = "E2E_DB_PASSWORD", matches = ".+")
class RoleConstraintEnforceTenantAllowlistIntegrationTest {

    @Autowired
    private RoleMutexRepository roleMutexRepository;

    @Autowired
    private RoleAssignmentRepository roleAssignmentRepository;

    @Autowired
    private RoleAssignmentSyncService roleAssignmentSyncService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void enforce_shouldNotBlock_whenTenantNotInAllowlist() {
        Long tenantId = 1L;

        User user = new User();
        user.setTenantId(tenantId);
        user.setUsername("rbac3_enforce_allowlist_user_" + System.currentTimeMillis());
        user.setNickname("rbac3 enforce allowlist");
        user = userRepository.save(user);
        Long userId = user.getId();

        Role leftRole = new Role();
        leftRole.setTenantId(tenantId);
        leftRole.setRoleLevel("TENANT");
        leftRole.setCode("ROLE_RBAC3_ENFORCE_AL_LEFT_" + System.currentTimeMillis());
        leftRole.setName("RBAC3 Enforce Allowlist Left " + System.currentTimeMillis());
        leftRole.setEnabled(true);
        leftRole.setBuiltin(false);
        leftRole = roleRepository.save(leftRole);

        Role rightRole = new Role();
        rightRole.setTenantId(tenantId);
        rightRole.setRoleLevel("TENANT");
        rightRole.setCode("ROLE_RBAC3_ENFORCE_AL_RIGHT_" + System.currentTimeMillis());
        rightRole.setName("RBAC3 Enforce Allowlist Right " + System.currentTimeMillis());
        rightRole.setEnabled(true);
        rightRole.setBuiltin(false);
        rightRole = roleRepository.save(rightRole);

        Long leftRoleId = leftRole.getId();
        Long rightRoleId = rightRole.getId();

        roleAssignmentRepository.deleteUserAssignmentsInTenant(userId, tenantId);
        roleMutexRepository.deleteByTenantIdAndLeftRoleIdAndRightRoleId(tenantId, leftRoleId, rightRoleId);

        RoleMutex mutex = new RoleMutex();
        mutex.setTenantId(tenantId);
        mutex.setLeftRoleId(leftRoleId);
        mutex.setRightRoleId(rightRoleId);
        roleMutexRepository.save(mutex);

        // Should not throw because tenantId=1 is NOT in enforce allowlist.
        roleAssignmentSyncService.replaceUserTenantRoleAssignments(userId, tenantId, List.of(leftRoleId, rightRoleId));

        var assigned = roleAssignmentRepository.findAll().stream()
            .filter(ra -> tenantId.equals(ra.getTenantId()))
            .filter(ra -> "USER".equals(ra.getPrincipalType()) && userId.equals(ra.getPrincipalId()))
            .filter(ra -> "TENANT".equals(ra.getScopeType()) && tenantId.equals(ra.getScopeId()))
            .filter(ra -> "ACTIVE".equals(ra.getStatus()))
            .map(ra -> ra.getRoleId())
            .toList();
        assertThat(assigned).contains(leftRoleId, rightRoleId);
    }
}

