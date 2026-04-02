package com.tiny.platform.infrastructure.auth.role.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.tiny.platform.OauthServerApplication;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.domain.RoleAssignment;
import com.tiny.platform.infrastructure.auth.role.domain.RoleMutex;
import com.tiny.platform.infrastructure.auth.role.domain.RolePrerequisite;
import com.tiny.platform.infrastructure.auth.role.repository.RoleCardinalityRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleAssignmentRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleMutexRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RolePrerequisiteRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.role.service.RoleAssignmentSyncService;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import java.time.LocalDateTime;
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
        "tiny.platform.auth.rbac3.enforce-tenant-ids=1",
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
class RoleConstraintEnforceIntegrationTest {

    @Autowired
    private RoleMutexRepository roleMutexRepository;

    @Autowired
    private RoleAssignmentRepository roleAssignmentRepository;

    @Autowired
    private RoleCardinalityRepository roleCardinalityRepository;

    @Autowired
    private RoleAssignmentSyncService roleAssignmentSyncService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantUserRepository tenantUserRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RolePrerequisiteRepository rolePrerequisiteRepository;

    @Test
    void enforce_shouldBlockAssignment_whenMutexConflict() {
        Long tenantId = 1L;

        User user = new User();
        user.setUsername("rbac3_enforce_user_" + System.currentTimeMillis());
        user.setNickname("rbac3 enforce");
        user = userRepository.save(user);
        Long userId = user.getId();

        Role leftRole = new Role();
        leftRole.setTenantId(tenantId);
        leftRole.setRoleLevel("TENANT");
        leftRole.setCode("ROLE_RBAC3_ENFORCE_LEFT_" + System.currentTimeMillis());
        leftRole.setName("RBAC3 Enforce Left " + System.currentTimeMillis());
        leftRole.setEnabled(true);
        leftRole.setBuiltin(false);
        leftRole = roleRepository.save(leftRole);

        Role rightRole = new Role();
        rightRole.setTenantId(tenantId);
        rightRole.setRoleLevel("TENANT");
        rightRole.setCode("ROLE_RBAC3_ENFORCE_RIGHT_" + System.currentTimeMillis());
        rightRole.setName("RBAC3 Enforce Right " + System.currentTimeMillis());
        rightRole.setEnabled(true);
        rightRole.setBuiltin(false);
        rightRole = roleRepository.save(rightRole);

        Long leftRoleId = leftRole.getId();
        Long rightRoleId = rightRole.getId();

        try {
            // Clean up user assignments (best-effort).
            roleAssignmentRepository.deleteUserAssignmentsInTenant(userId, tenantId);

            // Ensure no existing mutex rule.
            roleMutexRepository.deleteByTenantIdAndLeftRoleIdAndRightRoleId(tenantId, leftRoleId, rightRoleId);

            RoleMutex mutex = new RoleMutex();
            mutex.setTenantId(tenantId);
            mutex.setLeftRoleId(leftRoleId);
            mutex.setRightRoleId(rightRoleId);
            roleMutexRepository.save(mutex);

            assertThatThrownBy(() ->
                roleAssignmentSyncService.replaceUserTenantRoleAssignments(
                    userId,
                    tenantId,
                    List.of(leftRoleId, rightRoleId)
                )
            ).isInstanceOf(BusinessException.class);

            // Should not write assignments if blocked.
            assertThat(roleAssignmentRepository.findActiveRoleIdsForUserInTenant(userId, tenantId, LocalDateTime.now()))
                .isEmpty();
        } finally {
            Rbac3RoleConstraintIntegrationTestCleanup.purgeUserTenantArtifacts(
                tenantId,
                userId,
                roleAssignmentRepository,
                tenantUserRepository,
                userRepository
            );
            try {
                roleMutexRepository.deleteByTenantIdAndLeftRoleIdAndRightRoleId(tenantId, leftRoleId, rightRoleId);
            } catch (RuntimeException ignored) {
                // best-effort
            }
            Rbac3RoleConstraintIntegrationTestCleanup.deleteRoleBestEffort(roleRepository, leftRoleId);
            Rbac3RoleConstraintIntegrationTestCleanup.deleteRoleBestEffort(roleRepository, rightRoleId);
        }
    }

    @Test
    void enforce_shouldBlockAssignment_whenPrerequisiteMissing() {
        Long tenantId = 1L;

        User user = new User();
        user.setUsername("rbac3_enforce_user_pre_" + System.currentTimeMillis());
        user.setNickname("rbac3 enforce prereq");
        user = userRepository.save(user);
        Long userId = user.getId();

        Role targetRole = new Role();
        targetRole.setTenantId(tenantId);
        targetRole.setRoleLevel("TENANT");
        targetRole.setCode("ROLE_RBAC3_ENFORCE_PRE_T_" + System.currentTimeMillis());
        targetRole.setName("RBAC3 Enforce Pre Target " + System.currentTimeMillis());
        targetRole.setEnabled(true);
        targetRole.setBuiltin(false);
        targetRole = roleRepository.save(targetRole);

        Role requiredRole = new Role();
        requiredRole.setTenantId(tenantId);
        requiredRole.setRoleLevel("TENANT");
        requiredRole.setCode("ROLE_RBAC3_ENFORCE_PRE_R_" + System.currentTimeMillis());
        requiredRole.setName("RBAC3 Enforce Pre Required " + System.currentTimeMillis());
        requiredRole.setEnabled(true);
        requiredRole.setBuiltin(false);
        requiredRole = roleRepository.save(requiredRole);

        Long targetRoleId = targetRole.getId();
        Long requiredRoleId = requiredRole.getId();

        try {
            // Clean up user assignments (best-effort).
            roleAssignmentRepository.deleteUserAssignmentsInTenant(userId, tenantId);

            // Ensure prerequisite rule exists.
            rolePrerequisiteRepository.deleteByTenantIdAndRoleIdAndRequiredRoleId(
                tenantId,
                targetRoleId,
                requiredRoleId
            );
            RolePrerequisite rp = new RolePrerequisite();
            rp.setTenantId(tenantId);
            rp.setRoleId(targetRoleId);
            rp.setRequiredRoleId(requiredRoleId);
            rolePrerequisiteRepository.save(rp);

            assertThatThrownBy(() ->
                roleAssignmentSyncService.replaceUserTenantRoleAssignments(userId, tenantId, List.of(targetRoleId))
            ).isInstanceOf(BusinessException.class);

            // Should not write assignments if blocked.
            assertThat(roleAssignmentRepository.findActiveRoleIdsForUserInTenant(userId, tenantId, LocalDateTime.now()))
                .isEmpty();
        } finally {
            Rbac3RoleConstraintIntegrationTestCleanup.purgeUserTenantArtifacts(
                tenantId,
                userId,
                roleAssignmentRepository,
                tenantUserRepository,
                userRepository
            );
            try {
                rolePrerequisiteRepository.deleteByTenantIdAndRoleIdAndRequiredRoleId(
                    tenantId,
                    targetRoleId,
                    requiredRoleId
                );
            } catch (RuntimeException ignored) {
                // best-effort
            }
            Rbac3RoleConstraintIntegrationTestCleanup.deleteRoleBestEffort(roleRepository, targetRoleId);
            Rbac3RoleConstraintIntegrationTestCleanup.deleteRoleBestEffort(roleRepository, requiredRoleId);
        }
    }

    @Test
    void enforce_shouldBlockAssignment_whenCardinalityExceeded() {
        Long tenantId = 1L;

        User userA = new User();
        userA.setUsername("rbac3_enforce_user_card_a_" + System.currentTimeMillis());
        userA.setNickname("rbac3 enforce card a");
        userA = userRepository.save(userA);

        User userB = new User();
        userB.setUsername("rbac3_enforce_user_card_b_" + System.currentTimeMillis());
        userB.setNickname("rbac3 enforce card b");
        userB = userRepository.save(userB);

        Long userAId = userA.getId();
        Long userBId = userB.getId();

        Role role = new Role();
        role.setTenantId(tenantId);
        role.setRoleLevel("TENANT");
        role.setCode("ROLE_RBAC3_ENFORCE_CARD_" + System.currentTimeMillis());
        role.setName("RBAC3 Enforce Cardinality " + System.currentTimeMillis());
        role.setEnabled(true);
        role.setBuiltin(false);
        role = roleRepository.save(role);
        Long roleId = role.getId();

        try {
            // Clean up assignments (best-effort).
            roleAssignmentRepository.deleteUserAssignmentsInTenant(userAId, tenantId);
            roleAssignmentRepository.deleteUserAssignmentsInTenant(userBId, tenantId);

            // Configure cardinality: within TENANT scope, max 1 assignment.
            roleCardinalityRepository.deleteByTenantIdAndRoleIdAndScopeType(tenantId, roleId, "TENANT");
            com.tiny.platform.infrastructure.auth.role.domain.RoleCardinality c =
                new com.tiny.platform.infrastructure.auth.role.domain.RoleCardinality();
            c.setTenantId(tenantId);
            c.setRoleId(roleId);
            c.setScopeType("TENANT");
            c.setMaxAssignments(1);
            roleCardinalityRepository.save(c);

            // Occupy the quota with userA by inserting an ACTIVE assignment that is definitely effective.
            roleAssignmentSyncService.ensureTenantMembership(userAId, tenantId, false);
            RoleAssignment existing = new RoleAssignment();
            existing.setPrincipalType("USER");
            existing.setPrincipalId(userAId);
            existing.setRoleId(roleId);
            existing.setTenantId(tenantId);
            existing.setScopeType("TENANT");
            existing.setScopeId(tenantId);
            existing.setStatus("ACTIVE");
            LocalDateTime past = LocalDateTime.now().minusDays(1);
            existing.setStartTime(past);
            existing.setEndTime(null);
            existing.setGrantedAt(past);
            roleAssignmentRepository.save(existing);

            assertThatThrownBy(() ->
                roleAssignmentSyncService.replaceUserTenantRoleAssignments(userBId, tenantId, List.of(roleId))
            ).isInstanceOf(BusinessException.class);

            // Should not write assignments for userB if blocked.
            assertThat(
                roleAssignmentRepository.findAll().stream()
                    .filter(ra -> tenantId.equals(ra.getTenantId()))
                    .filter(ra -> "USER".equals(ra.getPrincipalType()) && userBId.equals(ra.getPrincipalId()))
                    .filter(ra -> "TENANT".equals(ra.getScopeType()) && tenantId.equals(ra.getScopeId()))
                    .filter(ra -> "ACTIVE".equals(ra.getStatus()))
                    .map(ra -> ra.getRoleId())
                    .toList()
            ).isEmpty();
        } finally {
            Rbac3RoleConstraintIntegrationTestCleanup.purgeUserTenantArtifactsBatch(
                tenantId,
                List.of(userAId, userBId),
                roleAssignmentRepository,
                tenantUserRepository,
                userRepository
            );
            try {
                roleCardinalityRepository.deleteByTenantIdAndRoleIdAndScopeType(tenantId, roleId, "TENANT");
            } catch (RuntimeException ignored) {
                // best-effort
            }
            Rbac3RoleConstraintIntegrationTestCleanup.deleteRoleBestEffort(roleRepository, roleId);
        }
    }
}

