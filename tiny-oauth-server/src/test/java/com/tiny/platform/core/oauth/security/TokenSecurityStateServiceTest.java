package com.tiny.platform.core.oauth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TokenSecurityStateServiceTest {

    private TokenSecurityStateService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:token_security_state;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
            "sa",
            ""
        );
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS `user_token_security_state`");
        jdbcTemplate.execute("""
            CREATE TABLE `user_token_security_state` (
              `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
              `user_id` BIGINT NOT NULL,
              `tenant_id` BIGINT NULL,
              `normalized_tenant_id` BIGINT AS (IFNULL(`tenant_id`, 0)),
              `scope_type` VARCHAR(32) NOT NULL,
              `scope_id` BIGINT NULL,
              `normalized_scope_id` BIGINT AS (IFNULL(`scope_id`, 0)),
              `token_version` BIGINT NOT NULL DEFAULT 1,
              `token_not_before` DATETIME NOT NULL,
              `reason` VARCHAR(128),
              `created_at` DATETIME,
              `created_by` BIGINT,
              `updated_at` DATETIME,
              `updated_by` BIGINT,
              UNIQUE (`user_id`, `normalized_tenant_id`, `scope_type`, `normalized_scope_id`)
            )
            """);
        service = new TokenSecurityStateService(
            new NamedParameterJdbcTemplate(dataSource),
            Clock.fixed(Instant.parse("2026-05-08T10:15:30Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void shouldResolveStableInitialVersionAndEpochNotBefore() {
        TokenSecurityState first = service.resolveEffectiveState(7L, 2L, "TENANT", 2L);
        TokenSecurityState second = service.resolveEffectiveState(7L, 2L, "TENANT", 2L);

        assertThat(first.tokenSecurityVersion()).isEqualTo(second.tokenSecurityVersion());
        assertThat(first.tokenNotBefore()).isEqualTo("1970-01-01T00:00:00");
        assertThat(first.globalTokenVersion()).isEqualTo(1L);
        assertThat(first.scopeTokenVersion()).isEqualTo(1L);
    }

    @Test
    void shouldChangeEffectiveVersionAndNotBeforeAfterGlobalRevoke() {
        TokenSecurityState before = service.resolveEffectiveState(7L, 2L, "TENANT", 2L);

        service.revokeAllUserTokens(7L, "password_reset", 99L);
        TokenSecurityState after = service.resolveEffectiveState(7L, 2L, "TENANT", 2L);

        assertThat(after.tokenSecurityVersion()).isNotEqualTo(before.tokenSecurityVersion());
        assertThat(after.tokenNotBefore()).isEqualTo("2026-05-08T10:15:30");
        assertThat(after.globalTokenVersion()).isEqualTo(2L);
        assertThat(after.scopeTokenVersion()).isEqualTo(1L);
    }

    @Test
    void shouldChangeOnlyTargetScopeVersionAfterScopedRevoke() {
        TokenSecurityState tenantBefore = service.resolveEffectiveState(7L, 2L, "TENANT", 2L);
        TokenSecurityState platformBefore = service.resolveEffectiveState(7L, null, "PLATFORM", null);

        service.revokeScopedTokens(7L, 2L, "TENANT", 2L, "tenant_security_update", 99L);

        TokenSecurityState tenantAfter = service.resolveEffectiveState(7L, 2L, "TENANT", 2L);
        TokenSecurityState platformAfter = service.resolveEffectiveState(7L, null, "PLATFORM", null);
        assertThat(tenantAfter.tokenSecurityVersion()).isNotEqualTo(tenantBefore.tokenSecurityVersion());
        assertThat(platformAfter.tokenSecurityVersion()).isEqualTo(platformBefore.tokenSecurityVersion());
    }
}
