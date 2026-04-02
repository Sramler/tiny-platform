package com.tiny.platform.infrastructure.auth.role.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tiny.platform.OauthServerApplication;
import com.tiny.platform.infrastructure.auth.role.domain.RoleAssignment;
import com.tiny.platform.infrastructure.auth.role.domain.RoleConstraintViolationLog;
import com.tiny.platform.infrastructure.auth.role.domain.RoleHierarchy;
import com.tiny.platform.infrastructure.auth.role.domain.RoleMutex;
import java.time.LocalDateTime;
import com.tiny.platform.infrastructure.auth.role.repository.RoleAssignmentRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleCardinalityRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleConstraintViolationLogRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleHierarchyRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleMutexRepository;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.repository.RolePrerequisiteRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.role.service.RoleAssignmentSyncService;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
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
class RoleConstraintDryRunIntegrationTest {

    @Autowired
    private RoleHierarchyRepository roleHierarchyRepository;

    @Autowired
    private RoleMutexRepository roleMutexRepository;

    @Autowired
    private RoleConstraintViolationLogRepository violationLogRepository;

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

    @Autowired
    private RoleCardinalityRepository roleCardinalityRepository;

    @Autowired
    private RoleAssignmentRepository roleAssignmentRepository;

    @Test
    void dryRun_shouldWriteViolationLog_whenHierarchyExpansionCreatesMutexConflict() {
        Long tenantId = 1L;
        User user = new User();
        user.setUsername("rbac3_dryrun_user_" + System.currentTimeMillis());
        user.setNickname("rbac3 dry-run");
        user = userRepository.save(user);
        Long userId = user.getId();

        Role childRole = new Role();
        childRole.setTenantId(tenantId);
        childRole.setRoleLevel("TENANT");
        childRole.setCode("ROLE_RBAC3_DRYRUN_CHILD_" + System.currentTimeMillis());
        childRole.setName("RBAC3 DryRun Child " + System.currentTimeMillis());
        childRole.setEnabled(true);
        childRole.setBuiltin(false);
        childRole = roleRepository.save(childRole);

        Role parentRole = new Role();
        parentRole.setTenantId(tenantId);
        parentRole.setRoleLevel("TENANT");
        parentRole.setCode("ROLE_RBAC3_DRYRUN_PARENT_" + System.currentTimeMillis());
        parentRole.setName("RBAC3 DryRun Parent " + System.currentTimeMillis());
        parentRole.setEnabled(true);
        parentRole.setBuiltin(false);
        parentRole = roleRepository.save(parentRole);

        Long childRoleId = childRole.getId();
        Long parentRoleId = parentRole.getId();

        try {
            // Clean up test artifacts (best-effort).
            violationLogRepository.findAll().stream()
                .filter(v -> tenantId.equals(v.getTenantId()) && userId.equals(v.getPrincipalId()))
                .map(RoleConstraintViolationLog::getId)
                .forEach(violationLogRepository::deleteById);

            // Make the test idempotent across local runs.
            roleHierarchyRepository.deleteByTenantIdAndChildRoleIdAndParentRoleId(tenantId, childRoleId, parentRoleId);
            roleMutexRepository.deleteByTenantIdAndLeftRoleIdAndRightRoleId(tenantId, childRoleId, parentRoleId);

            RoleHierarchy edge = new RoleHierarchy();
            edge.setTenantId(tenantId);
            edge.setChildRoleId(childRoleId);
            edge.setParentRoleId(parentRoleId);
            roleHierarchyRepository.save(edge);

            RoleMutex mutex = new RoleMutex();
            mutex.setTenantId(tenantId);
            mutex.setLeftRoleId(childRoleId);
            mutex.setRightRoleId(parentRoleId);
            roleMutexRepository.save(mutex);

            // Should not throw (dry-run).
            roleAssignmentSyncService.replaceUserTenantRoleAssignments(userId, tenantId, List.of(childRoleId));

            var logs = violationLogRepository.findAll().stream()
                .filter(v -> tenantId.equals(v.getTenantId()) && userId.equals(v.getPrincipalId()))
                .toList();

            assertThat(logs).isNotEmpty();
            assertThat(logs.get(0).getViolationType()).isEqualTo("MUTEX");
            assertThat(logs.get(0).getViolationCode()).isEqualTo("ROLE_CONFLICT_MUTEX");
            assertThat(logs.get(0).getDirectRoleIds()).contains(String.valueOf(childRoleId));
            assertThat(logs.get(0).getEffectiveRoleIds()).contains(String.valueOf(parentRoleId));
        } finally {
            Rbac3RoleConstraintIntegrationTestCleanup.purgeUserTenantArtifacts(
                tenantId,
                userId,
                roleAssignmentRepository,
                violationLogRepository,
                tenantUserRepository,
                userRepository
            );
            try {
                roleHierarchyRepository.deleteByTenantIdAndChildRoleIdAndParentRoleId(tenantId, childRoleId, parentRoleId);
            } catch (RuntimeException ignored) {
                // best-effort
            }
            try {
                roleMutexRepository.deleteByTenantIdAndLeftRoleIdAndRightRoleId(tenantId, childRoleId, parentRoleId);
            } catch (RuntimeException ignored) {
                // best-effort
            }
            Rbac3RoleConstraintIntegrationTestCleanup.deleteRoleBestEffort(roleRepository, childRoleId);
            Rbac3RoleConstraintIntegrationTestCleanup.deleteRoleBestEffort(roleRepository, parentRoleId);
        }
    }

    @Test
    void dryRun_shouldWriteViolationLog_whenPrerequisiteMissing() {
        Long tenantId = 1L;
        User user = new User();
        user.setUsername("rbac3_dryrun_user_prereq_" + System.currentTimeMillis());
        user.setNickname("rbac3 dry-run prereq");
        user = userRepository.save(user);
        Long userId = user.getId();

        Role targetRole = new Role();
        targetRole.setTenantId(tenantId);
        targetRole.setRoleLevel("TENANT");
        targetRole.setCode("ROLE_RBAC3_DRYRUN_TARGET_" + System.currentTimeMillis());
        targetRole.setName("RBAC3 DryRun Target " + System.currentTimeMillis());
        targetRole.setEnabled(true);
        targetRole.setBuiltin(false);
        targetRole = roleRepository.save(targetRole);

        Role requiredRole = new Role();
        requiredRole.setTenantId(tenantId);
        requiredRole.setRoleLevel("TENANT");
        requiredRole.setCode("ROLE_RBAC3_DRYRUN_REQUIRED_" + System.currentTimeMillis());
        requiredRole.setName("RBAC3 DryRun Required " + System.currentTimeMillis());
        requiredRole.setEnabled(true);
        requiredRole.setBuiltin(false);
        requiredRole = roleRepository.save(requiredRole);

        Long targetRoleId = targetRole.getId();
        Long requiredRoleId = requiredRole.getId();

        try {
            // Ensure clean.
            violationLogRepository.findAll().stream()
                .filter(v -> tenantId.equals(v.getTenantId()) && userId.equals(v.getPrincipalId()))
                .map(RoleConstraintViolationLog::getId)
                .forEach(violationLogRepository::deleteById);

            // Configure prerequisite: target requires requiredRole.
            // We only grant target, so prerequisite should be missing.
            rolePrerequisiteRepository.deleteByTenantIdAndRoleIdAndRequiredRoleId(
                tenantId,
                targetRoleId,
                requiredRoleId
            );

            com.tiny.platform.infrastructure.auth.role.domain.RolePrerequisite rp =
                new com.tiny.platform.infrastructure.auth.role.domain.RolePrerequisite();
            rp.setTenantId(tenantId);
            rp.setRoleId(targetRoleId);
            rp.setRequiredRoleId(requiredRoleId);
            rolePrerequisiteRepository.save(rp);

            // Should not throw (dry-run).
            roleAssignmentSyncService.replaceUserTenantRoleAssignments(userId, tenantId, List.of(targetRoleId));

            var logs = violationLogRepository.findAll().stream()
                .filter(v -> tenantId.equals(v.getTenantId()) && userId.equals(v.getPrincipalId()))
                .toList();

            assertThat(logs.stream().anyMatch(v -> "PREREQUISITE".equals(v.getViolationType()))).isTrue();
        } finally {
            Rbac3RoleConstraintIntegrationTestCleanup.purgeUserTenantArtifacts(
                tenantId,
                userId,
                roleAssignmentRepository,
                violationLogRepository,
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
    void dryRun_shouldWriteViolationLog_whenCardinalityExceeded() {
        Long tenantId = 1L;

        User userA = new User();
        userA.setUsername("rbac3_dryrun_user_card_a_" + System.currentTimeMillis());
        userA.setNickname("rbac3 dry-run card a");
        userA = userRepository.save(userA);

        User userB = new User();
        userB.setUsername("rbac3_dryrun_user_card_b_" + System.currentTimeMillis());
        userB.setNickname("rbac3 dry-run card b");
        userB = userRepository.save(userB);

        Long userAId = userA.getId();
        Long userBId = userB.getId();

        Role role = new Role();
        role.setTenantId(tenantId);
        role.setRoleLevel("TENANT");
        role.setCode("ROLE_RBAC3_DRYRUN_CARD_" + System.currentTimeMillis());
        role.setName("RBAC3 DryRun Cardinality " + System.currentTimeMillis());
        role.setEnabled(true);
        role.setBuiltin(false);
        role = roleRepository.save(role);

        Long roleId = role.getId();

        try {
            // Clean up best-effort.
            violationLogRepository.findAll().stream()
                .filter(v -> tenantId.equals(v.getTenantId())
                    && (userAId.equals(v.getPrincipalId()) || userBId.equals(v.getPrincipalId())))
                .map(RoleConstraintViolationLog::getId)
                .forEach(violationLogRepository::deleteById);

            // Ensure role assignments clean for these users.
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

            assertThat(
                roleCardinalityRepository.findByTenantIdAndScopeTypeAndRoleIdIn(tenantId, "TENANT", List.of(roleId))
            ).isNotEmpty();

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

            // Exceed with userB (should not throw, but should log).
            roleAssignmentSyncService.replaceUserTenantRoleAssignments(userBId, tenantId, List.of(roleId));

            var logsB = violationLogRepository.findAll().stream()
                .filter(v -> tenantId.equals(v.getTenantId()) && userBId.equals(v.getPrincipalId()))
                .toList();

            assertThat(logsB.stream().anyMatch(v -> "CARDINALITY".equals(v.getViolationType()))).isTrue();
        } finally {
            Rbac3RoleConstraintIntegrationTestCleanup.purgeUserTenantArtifactsBatch(
                tenantId,
                List.of(userAId, userBId),
                roleAssignmentRepository,
                violationLogRepository,
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

