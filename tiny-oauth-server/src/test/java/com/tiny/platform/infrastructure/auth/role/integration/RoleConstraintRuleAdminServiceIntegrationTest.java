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

    @Test
    void roleHierarchy_shouldRejectCycle() {
        Long tenantId = 1L;
        long ts = System.currentTimeMillis();

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

        Long aId = a.getId();
        Long bId = b.getId();
        Long cId = c.getId();

        // Best-effort clean.
        roleHierarchyRepository.deleteByTenantIdAndChildRoleIdAndParentRoleId(tenantId, aId, bId);
        roleHierarchyRepository.deleteByTenantIdAndChildRoleIdAndParentRoleId(tenantId, bId, cId);
        roleHierarchyRepository.deleteByTenantIdAndChildRoleIdAndParentRoleId(tenantId, cId, aId);

        adminService.upsertRoleHierarchyEdge(tenantId, aId, bId);
        adminService.upsertRoleHierarchyEdge(tenantId, bId, cId);

        assertThatThrownBy(() -> adminService.upsertRoleHierarchyEdge(tenantId, cId, aId))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void roleMutex_shouldRejectSelfMutex() {
        Long tenantId = 1L;
        long ts = System.currentTimeMillis();
        Role r = new Role();
        r.setTenantId(tenantId);
        r.setRoleLevel("TENANT");
        r.setCode("ROLE_RBAC3_RULE_MUTEX_" + ts);
        r.setName("RBAC3 Rule Mutex " + ts);
        r.setEnabled(true);
        r.setBuiltin(false);
        r = roleRepository.save(r);

        Long roleId = r.getId();
        roleMutexRepository.deleteByTenantIdAndLeftRoleIdAndRightRoleId(tenantId, roleId, roleId);

        assertThatThrownBy(() -> adminService.upsertRoleMutex(tenantId, roleId, roleId))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void roleCardinality_shouldRejectInvalidParams() {
        Long tenantId = 1L;
        long ts = System.currentTimeMillis();
        Role r = new Role();
        r.setTenantId(tenantId);
        r.setRoleLevel("TENANT");
        r.setCode("ROLE_RBAC3_RULE_CARD_" + ts);
        r.setName("RBAC3 Rule Card " + ts);
        r.setEnabled(true);
        r.setBuiltin(false);
        r = roleRepository.save(r);

        Long roleId = r.getId();
        roleCardinalityRepository.deleteByTenantIdAndRoleIdAndScopeType(tenantId, roleId, "TENANT");

        assertThatThrownBy(() -> adminService.upsertRoleCardinality(tenantId, roleId, "ORG", 1))
            .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> adminService.upsertRoleCardinality(tenantId, roleId, "TENANT", 0))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void rolePrerequisite_shouldRejectSelfPrerequisite() {
        Long tenantId = 1L;
        long ts = System.currentTimeMillis();

        Role r = new Role();
        r.setTenantId(tenantId);
        r.setRoleLevel("TENANT");
        r.setCode("ROLE_RBAC3_RULE_PRE_" + ts);
        r.setName("RBAC3 Rule Pre " + ts);
        r.setEnabled(true);
        r.setBuiltin(false);
        r = roleRepository.save(r);

        Long roleId = r.getId();
        rolePrerequisiteRepository.deleteByTenantIdAndRoleIdAndRequiredRoleId(tenantId, roleId, roleId);

        assertThatThrownBy(() -> adminService.upsertRolePrerequisite(tenantId, roleId, roleId))
            .isInstanceOf(BusinessException.class);
    }
}

