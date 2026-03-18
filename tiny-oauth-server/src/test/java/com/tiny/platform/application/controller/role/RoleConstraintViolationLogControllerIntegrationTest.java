package com.tiny.platform.application.controller.role;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tiny.platform.OauthServerApplication;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.role.domain.RoleConstraintViolationLog;
import com.tiny.platform.infrastructure.auth.role.repository.RoleConstraintViolationLogRepository;
import java.time.LocalDateTime;
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
class RoleConstraintViolationLogControllerIntegrationTest {

    private static final Long TENANT_ID = 1L;
    private static final String TENANT_HEADER = "X-Active-Tenant-Id";
    private static final String TENANT_SESSION_KEY = "AUTH_ACTIVE_TENANT_ID";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoleConstraintViolationLogRepository logRepository;

    private Long logId;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        if (logId != null) {
            logRepository.deleteById(logId);
        }
    }

    @Test
    void listViolations_shouldReturnPageContent() throws Exception {
        long base = System.currentTimeMillis() % 1_000_000L;
        long uniquePrincipalId = 10_000_000L + base;

        RoleConstraintViolationLog log = new RoleConstraintViolationLog();
        log.setTenantId(TENANT_ID);
        log.setPrincipalType("USER");
        log.setPrincipalId(uniquePrincipalId);
        log.setScopeType("TENANT");
        log.setScopeId(TENANT_ID);
        log.setViolationType("MUTEX");
        log.setViolationCode("ROLE_CONFLICT_MUTEX");
        log.setDirectRoleIds("1,2");
        log.setEffectiveRoleIds("1,2,3");
        log.setDetails("{\"conflicts\":\"1&2\"}");
        log.setCreatedAt(LocalDateTime.now().minusSeconds(1));
        log = logRepository.save(log);
        logId = log.getId();

        var principal = user("role-permission-assigner")
            .authorities(new SimpleGrantedAuthority("system:role:permission:assign"));

        mockMvc.perform(get("/sys/role-constraints/violations")
                .param("violationType", "MUTEX")
                .param("principalId", String.valueOf(uniquePrincipalId))
                .param("size", "10")
                .param("sort", "createdAt,desc")
                .header(TENANT_HEADER, TENANT_ID.toString())
                .sessionAttr(TENANT_SESSION_KEY, TENANT_ID)
                .with(principal))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.content[0].id", equalTo(logId.intValue())))
            .andExpect(jsonPath("$.content[0].violationType", equalTo("MUTEX")))
            .andExpect(jsonPath("$.content[0].violationCode", equalTo("ROLE_CONFLICT_MUTEX")))
            .andExpect(jsonPath("$.content[0].principalType", equalTo("USER")))
            .andExpect(jsonPath("$.content[0].principalId", equalTo((int) uniquePrincipalId)));
    }
}

