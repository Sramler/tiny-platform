package com.tiny.platform.infrastructure.auth.user.support;

import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UserAuthenticationMethodProfilesTest {

    @Test
    void create_should_materialize_credential_and_scope_policy_into_storage_record() {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(30);
        UserAuthenticationMethod method = UserAuthenticationMethodProfiles.create(
            new UserAuthenticationCredential(
                99L,
                "LOCAL",
                "PASSWORD",
                Map.of("password", "{bcrypt}secret"),
                null,
                null,
                expiresAt
            ),
            new UserAuthenticationScopePolicy(
                99L,
                8L,
                "LOCAL",
                "PASSWORD",
                true,
                true,
                0
            )
        );

        assertThat(method.getUserId()).isEqualTo(99L);
        assertThat(method.getTenantId()).isEqualTo(8L);
        assertThat(method.getAuthenticationProvider()).isEqualTo("LOCAL");
        assertThat(method.getAuthenticationType()).isEqualTo("PASSWORD");
        assertThat(method.getAuthenticationConfiguration()).containsEntry("password", "{bcrypt}secret");
        assertThat(method.getIsPrimaryMethod()).isTrue();
        assertThat(method.getIsMethodEnabled()).isTrue();
        assertThat(method.getAuthenticationPriority()).isZero();
        assertThat(method.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void apply_should_update_existing_record_without_reusing_mutable_configuration_map() {
        UserAuthenticationMethod method = new UserAuthenticationMethod();
        method.setId(7L);
        method.setUserId(10L);
        method.setTenantId(5L);
        method.setAuthenticationProvider("LOCAL");
        method.setAuthenticationType("TOTP");

        Map<String, Object> config = new HashMap<>();
        config.put("secretKey", "abc123");

        UserAuthenticationMethodProfiles.apply(
            method,
            new UserAuthenticationCredential(
                10L,
                "LOCAL",
                "TOTP",
                config,
                null,
                "127.0.0.1",
                null
            ),
            new UserAuthenticationScopePolicy(
                10L,
                null,
                "LOCAL",
                "TOTP",
                false,
                true,
                1
            )
        );

        config.put("secretKey", "mutated");

        assertThat(method.getId()).isEqualTo(7L);
        assertThat(method.getTenantId()).isEqualTo(5L);
        assertThat(method.getAuthenticationConfiguration()).containsEntry("secretKey", "abc123");
        assertThat(method.getLastVerifiedIp()).isEqualTo("127.0.0.1");
        assertThat(method.getAuthenticationPriority()).isEqualTo(1);
    }
}
