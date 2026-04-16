package com.tiny.platform.application.controller.role;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.OauthServerApplication;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.role.dto.RoleCardinalityRuleUpsertDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleHierarchyEdgeUpsertDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleMutexRuleUpsertDto;
import com.tiny.platform.infrastructure.auth.role.dto.RolePrerequisiteRuleUpsertDto;
import com.tiny.platform.infrastructure.auth.role.repository.RoleCardinalityRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleHierarchyRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleMutexRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RolePrerequisiteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
class RoleConstraintRuleControllerIntegrationTest {

    private static final Long TENANT_ID = 1L;
    private static final String TENANT_HEADER = "X-Active-Tenant-Id";
    private static final String TENANT_SESSION_KEY = "AUTH_ACTIVE_TENANT_ID";

    private Long hChild;
    private Long hParent;
    private Long mRoleA;
    private Long mRoleB;
    private Long pRole;
    private Long pRequired;
    private Long cRole;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoleHierarchyRepository roleHierarchyRepository;

    @Autowired
    private RoleMutexRepository roleMutexRepository;

    @Autowired
    private RolePrerequisiteRepository rolePrerequisiteRepository;

    @Autowired
    private RoleCardinalityRepository roleCardinalityRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        if (hChild != null && hParent != null) {
            roleHierarchyRepository.deleteByTenantIdAndChildRoleIdAndParentRoleId(TENANT_ID, hChild, hParent);
        }
        if (mRoleA != null && mRoleB != null) {
            roleMutexRepository.deleteByTenantIdAndLeftRoleIdAndRightRoleId(TENANT_ID, Math.min(mRoleA, mRoleB), Math.max(mRoleA, mRoleB));
        }
        if (pRole != null && pRequired != null) {
            rolePrerequisiteRepository.deleteByTenantIdAndRoleIdAndRequiredRoleId(TENANT_ID, pRole, pRequired);
        }
        if (cRole != null) {
            roleCardinalityRepository.deleteByTenantIdAndRoleIdAndScopeType(TENANT_ID, cRole, "TENANT");
        }
    }

    @Test
    void crud_shouldPersistAndListRules_asDtos() throws Exception {
        var principal = user("role-constraint-operator")
            .authorities(
                new SimpleGrantedAuthority("system:role:constraint:edit"),
                new SimpleGrantedAuthority("system:role:constraint:view")
            );
        long base = (System.currentTimeMillis() % 1_000_000L) * 10_000L;
        hChild = base + 1;
        hParent = base + 2;
        mRoleA = base + 3;
        mRoleB = base + 4;
        pRole = base + 5;
        pRequired = base + 6;
        cRole = base + 7;

        long normalizedLeft = Math.min(mRoleA, mRoleB);
        long normalizedRight = Math.max(mRoleA, mRoleB);

        RoleHierarchyEdgeUpsertDto hierarchy = new RoleHierarchyEdgeUpsertDto();
        hierarchy.setChildRoleId(hChild);
        hierarchy.setParentRoleId(hParent);
        mockMvc.perform(post("/sys/role-constraints/hierarchy")
                .header(TENANT_HEADER, TENANT_ID.toString())
                .sessionAttr(TENANT_SESSION_KEY, TENANT_ID)
                .header("X-Idempotency-Key", "it-hierarchy-" + base)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(hierarchy))
                .with(principal))
            .andExpect(status().isOk());

        RoleMutexRuleUpsertDto mutex = new RoleMutexRuleUpsertDto();
        mutex.setRoleIdA(mRoleA);
        mutex.setRoleIdB(mRoleB);
        mockMvc.perform(post("/sys/role-constraints/mutex")
                .header(TENANT_HEADER, TENANT_ID.toString())
                .sessionAttr(TENANT_SESSION_KEY, TENANT_ID)
                .header("X-Idempotency-Key", "it-mutex-" + base)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mutex))
                .with(principal))
            .andExpect(status().isOk());

        RolePrerequisiteRuleUpsertDto prerequisite = new RolePrerequisiteRuleUpsertDto();
        prerequisite.setRoleId(pRole);
        prerequisite.setRequiredRoleId(pRequired);
        mockMvc.perform(post("/sys/role-constraints/prerequisite")
                .header(TENANT_HEADER, TENANT_ID.toString())
                .sessionAttr(TENANT_SESSION_KEY, TENANT_ID)
                .header("X-Idempotency-Key", "it-prereq-" + base)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(prerequisite))
                .with(principal))
            .andExpect(status().isOk());

        RoleCardinalityRuleUpsertDto cardinality = new RoleCardinalityRuleUpsertDto();
        cardinality.setRoleId(cRole);
        cardinality.setScopeType("TENANT");
        cardinality.setMaxAssignments(1);
        mockMvc.perform(post("/sys/role-constraints/cardinality")
                .header(TENANT_HEADER, TENANT_ID.toString())
                .sessionAttr(TENANT_SESSION_KEY, TENANT_ID)
                .header("X-Idempotency-Key", "it-card-" + base)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cardinality))
                .with(principal))
            .andExpect(status().isOk());

        mockMvc.perform(get("/sys/role-constraints/hierarchy")
                .header(TENANT_HEADER, TENANT_ID.toString())
                .sessionAttr(TENANT_SESSION_KEY, TENANT_ID)
                .with(principal))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.childRoleId == " + hChild + " && @.parentRoleId == " + hParent + ")]").exists());

        mockMvc.perform(get("/sys/role-constraints/mutex")
                .header(TENANT_HEADER, TENANT_ID.toString())
                .sessionAttr(TENANT_SESSION_KEY, TENANT_ID)
                .with(principal))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.leftRoleId == " + normalizedLeft + " && @.rightRoleId == " + normalizedRight + ")]").exists());

        mockMvc.perform(get("/sys/role-constraints/prerequisite")
                .header(TENANT_HEADER, TENANT_ID.toString())
                .sessionAttr(TENANT_SESSION_KEY, TENANT_ID)
                .with(principal))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.roleId == " + pRole + " && @.requiredRoleId == " + pRequired + ")]").exists());

        mockMvc.perform(get("/sys/role-constraints/cardinality")
                .header(TENANT_HEADER, TENANT_ID.toString())
                .sessionAttr(TENANT_SESSION_KEY, TENANT_ID)
                .with(principal))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.roleId == " + cRole + " && @.scopeType == 'TENANT' && @.maxAssignments == 1)]").exists());
    }
}
