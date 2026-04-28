package com.tiny.platform.infrastructure.auth.role.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tiny.platform.OauthServerApplication;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.repository.RoleCardinalityRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleHierarchyRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleMutexRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RolePrerequisiteRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.role.service.RoleConstraintRuleAdminService;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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
class RoleConstraintRuleAdminServiceIntegrationTest {

    @Autowired
    private RoleConstraintRuleAdminService adminService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RoleHierarchyRepository roleHierarchyRepository;

    @Autowired
    private RoleMutexRepository roleMutexRepository;

    @Autowired
    private RoleCardinalityRepository roleCardinalityRepository;

    @Autowired
    private RolePrerequisiteRepository rolePrerequisiteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void roleHierarchy_shouldRejectCycle() {
        Long tenantId = 1L;
        long ts = System.currentTimeMillis();

        Long aId = null;
        Long bId = null;
        Long cId = null;
        try {
            Role a = new Role();
            a.setTenantId(tenantId);
            a.setRoleLevel("TENANT");
            a.setCode("ROLE_RBAC3_RULE_A_" + ts);
            a.setName("RBAC3 Rule A " + ts);
            a.setEnabled(true);
            a.setBuiltin(false);
            a = roleRepository.save(a);

            Role b = new Role();
            b.setTenantId(tenantId);
            b.setRoleLevel("TENANT");
            b.setCode("ROLE_RBAC3_RULE_B_" + ts);
            b.setName("RBAC3 Rule B " + ts);
            b.setEnabled(true);
            b.setBuiltin(false);
            b = roleRepository.save(b);

            Role c = new Role();
            c.setTenantId(tenantId);
            c.setRoleLevel("TENANT");
            c.setCode("ROLE_RBAC3_RULE_C_" + ts);
            c.setName("RBAC3 Rule C " + ts);
            c.setEnabled(true);
            c.setBuiltin(false);
            c = roleRepository.save(c);

            aId = a.getId();
            bId = b.getId();
            cId = c.getId();
            final Long finalAId = aId;
            final Long finalBId = bId;
            final Long finalCId = cId;

            // Best-effort clean.
            roleHierarchyRepository.deleteByTenantIdAndChildRoleIdAndParentRoleId(tenantId, finalAId, finalBId);
            roleHierarchyRepository.deleteByTenantIdAndChildRoleIdAndParentRoleId(tenantId, finalBId, finalCId);
            roleHierarchyRepository.deleteByTenantIdAndChildRoleIdAndParentRoleId(tenantId, finalCId, finalAId);

            adminService.upsertRoleHierarchyEdge(tenantId, finalAId, finalBId);
            adminService.upsertRoleHierarchyEdge(tenantId, finalBId, finalCId);

            assertThatThrownBy(() -> adminService.upsertRoleHierarchyEdge(tenantId, finalCId, finalAId))
                .isInstanceOf(BusinessException.class);
        } finally {
            Rbac3RoleConstraintIntegrationTestCleanup.purgeRoleConstraintArtifacts(
                tenantId,
                List.of(aId, bId, cId),
                roleHierarchyRepository,
                roleMutexRepository,
                roleCardinalityRepository,
                rolePrerequisiteRepository,
                roleRepository
            );
        }
    }

    @Test
    void platformRoleHierarchy_shouldRejectCycle() {
        long ts = System.currentTimeMillis();
        Long aId = null;
        Long bId = null;
        Long cId = null;
        try {
            Role a = new Role();
            a.setTenantId(null);
            a.setRoleLevel("PLATFORM");
            a.setCode("ROLE_PLATFORM_RBAC3_RULE_A_" + ts);
            a.setName("Platform RBAC3 Rule A " + ts);
            a.setEnabled(true);
            a.setBuiltin(false);
            a = roleRepository.save(a);

            Role b = new Role();
            b.setTenantId(null);
            b.setRoleLevel("PLATFORM");
            b.setCode("ROLE_PLATFORM_RBAC3_RULE_B_" + ts);
            b.setName("Platform RBAC3 Rule B " + ts);
            b.setEnabled(true);
            b.setBuiltin(false);
            b = roleRepository.save(b);

            Role c = new Role();
            c.setTenantId(null);
            c.setRoleLevel("PLATFORM");
            c.setCode("ROLE_PLATFORM_RBAC3_RULE_C_" + ts);
            c.setName("Platform RBAC3 Rule C " + ts);
            c.setEnabled(true);
            c.setBuiltin(false);
            c = roleRepository.save(c);

            aId = a.getId();
            bId = b.getId();
            cId = c.getId();

            roleHierarchyRepository.deleteByTenantIdIsNullAndChildRoleIdAndParentRoleId(aId, bId);
            roleHierarchyRepository.deleteByTenantIdIsNullAndChildRoleIdAndParentRoleId(bId, cId);
            roleHierarchyRepository.deleteByTenantIdIsNullAndChildRoleIdAndParentRoleId(cId, aId);

            adminService.upsertPlatformRoleHierarchyEdge(aId, bId);
            adminService.upsertPlatformRoleHierarchyEdge(bId, cId);

            final Long finalAId = aId;
            final Long finalCId = cId;
            assertThatThrownBy(() -> adminService.upsertPlatformRoleHierarchyEdge(finalCId, finalAId))
                .isInstanceOf(BusinessException.class);
        } finally {
            Rbac3RoleConstraintIntegrationTestCleanup.purgeRoleConstraintArtifacts(
                null,
                List.of(aId, bId, cId),
                roleHierarchyRepository,
                roleMutexRepository,
                roleCardinalityRepository,
                rolePrerequisiteRepository,
                roleRepository
            );
        }
    }

    @Test
    void roleMutex_shouldRejectSelfMutex() {
        Long tenantId = 1L;
        long ts = System.currentTimeMillis();
        Long roleId = null;
        try {
            Role r = new Role();
            r.setTenantId(tenantId);
            r.setRoleLevel("TENANT");
            r.setCode("ROLE_RBAC3_RULE_MUTEX_" + ts);
            r.setName("RBAC3 Rule Mutex " + ts);
            r.setEnabled(true);
            r.setBuiltin(false);
            r = roleRepository.save(r);

            roleId = r.getId();
            final Long finalRoleId = roleId;
            roleMutexRepository.deleteByTenantIdAndLeftRoleIdAndRightRoleId(tenantId, finalRoleId, finalRoleId);

            assertThatThrownBy(() -> adminService.upsertRoleMutex(tenantId, finalRoleId, finalRoleId))
                .isInstanceOf(BusinessException.class);
        } finally {
            Rbac3RoleConstraintIntegrationTestCleanup.purgeRoleConstraintArtifacts(
                tenantId,
                List.of(roleId),
                roleHierarchyRepository,
                roleMutexRepository,
                roleCardinalityRepository,
                rolePrerequisiteRepository,
                roleRepository
            );
        }
    }

    @Test
    void roleCardinality_shouldRejectInvalidParams() {
        Long tenantId = 1L;
        long ts = System.currentTimeMillis();
        Long roleId = null;
        try {
            Role r = new Role();
            r.setTenantId(tenantId);
            r.setRoleLevel("TENANT");
            r.setCode("ROLE_RBAC3_RULE_CARD_" + ts);
            r.setName("RBAC3 Rule Card " + ts);
            r.setEnabled(true);
            r.setBuiltin(false);
            r = roleRepository.save(r);

            roleId = r.getId();
            final Long finalRoleId = roleId;
            roleCardinalityRepository.deleteByTenantIdAndRoleIdAndScopeType(tenantId, finalRoleId, "TENANT");

            assertThatThrownBy(() -> adminService.upsertRoleCardinality(tenantId, finalRoleId, "INVALID_SCOPE", 1))
                .isInstanceOf(BusinessException.class);

            assertThatThrownBy(() -> adminService.upsertRoleCardinality(tenantId, finalRoleId, "TENANT", 0))
                .isInstanceOf(BusinessException.class);
        } finally {
            Rbac3RoleConstraintIntegrationTestCleanup.purgeRoleConstraintArtifacts(
                tenantId,
                List.of(roleId),
                roleHierarchyRepository,
                roleMutexRepository,
                roleCardinalityRepository,
                rolePrerequisiteRepository,
                roleRepository
            );
        }
    }

    @Test
    void rolePrerequisite_shouldRejectSelfPrerequisite() {
        Long tenantId = 1L;
        long ts = System.currentTimeMillis();
        Long roleId = null;
        try {
            Role r = new Role();
            r.setTenantId(tenantId);
            r.setRoleLevel("TENANT");
            r.setCode("ROLE_RBAC3_RULE_PRE_" + ts);
            r.setName("RBAC3 Rule Pre " + ts);
            r.setEnabled(true);
            r.setBuiltin(false);
            r = roleRepository.save(r);

            roleId = r.getId();
            final Long finalRoleId = roleId;
            rolePrerequisiteRepository.deleteByTenantIdAndRoleIdAndRequiredRoleId(tenantId, finalRoleId, finalRoleId);

            assertThatThrownBy(() -> adminService.upsertRolePrerequisite(tenantId, finalRoleId, finalRoleId))
                .isInstanceOf(BusinessException.class);
        } finally {
            Rbac3RoleConstraintIntegrationTestCleanup.purgeRoleConstraintArtifacts(
                tenantId,
                List.of(roleId),
                roleHierarchyRepository,
                roleMutexRepository,
                roleCardinalityRepository,
                rolePrerequisiteRepository,
                roleRepository
            );
        }
    }

    @Test
    void platformRolePrerequisite_shouldRejectSelfPrerequisite() {
        long ts = System.currentTimeMillis();
        Long roleId = null;
        try {
            Role r = new Role();
            r.setTenantId(null);
            r.setRoleLevel("PLATFORM");
            r.setCode("ROLE_PLATFORM_RBAC3_RULE_PRE_" + ts);
            r.setName("Platform RBAC3 Rule Pre " + ts);
            r.setEnabled(true);
            r.setBuiltin(false);
            r = roleRepository.save(r);

            roleId = r.getId();
            rolePrerequisiteRepository.deleteByTenantIdIsNullAndRoleIdAndRequiredRoleId(roleId, roleId);

            Long finalRoleId = roleId;
            assertThatThrownBy(() -> adminService.upsertPlatformRolePrerequisite(finalRoleId, finalRoleId))
                .isInstanceOf(BusinessException.class);
        } finally {
            Rbac3RoleConstraintIntegrationTestCleanup.purgeRoleConstraintArtifacts(
                null,
                List.of(roleId),
                roleHierarchyRepository,
                roleMutexRepository,
                roleCardinalityRepository,
                rolePrerequisiteRepository,
                roleRepository
            );
        }
    }

    /**
     * 159：平台 null-tenant 约束行依赖 normalized_tenant_id 唯一键；重复插入应被数据库拒绝（回归锁定）。
     */
    @Test
    @Transactional
    void platformRoleMutex_database_shouldRejectDuplicateNormalizedTenantRows() {
        long ts = System.currentTimeMillis();
        Long leftId = null;
        Long rightId = null;
        try {
            Role left = new Role();
            left.setTenantId(null);
            left.setRoleLevel("PLATFORM");
            left.setCode("ROLE_PLATFORM_RBAC3_DUP_A_" + ts);
            left.setName("Platform RBAC3 Dup A " + ts);
            left.setEnabled(true);
            left.setBuiltin(false);
            left = roleRepository.save(left);

            Role right = new Role();
            right.setTenantId(null);
            right.setRoleLevel("PLATFORM");
            right.setCode("ROLE_PLATFORM_RBAC3_DUP_B_" + ts);
            right.setName("Platform RBAC3 Dup B " + ts);
            right.setEnabled(true);
            right.setBuiltin(false);
            right = roleRepository.save(right);

            leftId = left.getId();
            rightId = right.getId();
            final Long dupLeftId = leftId;
            final Long dupRightId = rightId;

            jdbcTemplate.update(
                "DELETE FROM role_mutex WHERE tenant_id IS NULL AND left_role_id = ? AND right_role_id = ?",
                dupLeftId,
                dupRightId
            );
            jdbcTemplate.update(
                "DELETE FROM role_mutex WHERE tenant_id IS NULL AND left_role_id = ? AND right_role_id = ?",
                dupRightId,
                dupLeftId
            );

            jdbcTemplate.update(
                "INSERT INTO role_mutex (tenant_id, left_role_id, right_role_id, created_at, updated_at) VALUES (NULL, ?, ?, NOW(), NOW())",
                dupLeftId,
                dupRightId
            );

            assertThatThrownBy(() ->
                jdbcTemplate.update(
                    "INSERT INTO role_mutex (tenant_id, left_role_id, right_role_id, created_at, updated_at) VALUES (NULL, ?, ?, NOW(), NOW())",
                    dupLeftId,
                    dupRightId
                )
            ).isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            Rbac3RoleConstraintIntegrationTestCleanup.purgeRoleConstraintArtifacts(
                null,
                List.of(leftId, rightId),
                roleHierarchyRepository,
                roleMutexRepository,
                roleCardinalityRepository,
                rolePrerequisiteRepository,
                roleRepository
            );
        }
    }
}
