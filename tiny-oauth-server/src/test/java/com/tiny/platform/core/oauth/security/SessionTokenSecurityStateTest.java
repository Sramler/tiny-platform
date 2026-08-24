package com.tiny.platform.core.oauth.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTokenSecurityStateTest {

    @Test
    void shouldPersistIndependentSerializableVersionsPerScope() {
        MockHttpSession session = new MockHttpSession();
        TokenSecurityState tenantState = state(7L, 11L, "TENANT", 11L, "tenant-v1");
        TokenSecurityState platformState = state(7L, null, "PLATFORM", null, "platform-v1");

        SessionTokenSecurityState.stamp(session, tenantState);
        SessionTokenSecurityState.stamp(session, platformState);

        assertThat(SessionTokenSecurityState.readVersion(session, tenantState)).isEqualTo("tenant-v1");
        assertThat(SessionTokenSecurityState.readVersion(session, platformState)).isEqualTo("platform-v1");
    }

    @Test
    void shouldReturnNullForMissingOrInvalidSnapshot() {
        MockHttpSession session = new MockHttpSession();
        TokenSecurityState state = state(7L, 11L, "TENANT", 11L, "tenant-v1");

        assertThat(SessionTokenSecurityState.readVersion(session, state)).isNull();
        SessionTokenSecurityState.stamp(session, state(7L, 11L, "TENANT", 11L, ""));
        assertThat(SessionTokenSecurityState.readVersion(session, state)).isNull();
    }

    private TokenSecurityState state(Long userId,
                                     Long tenantId,
                                     String scopeType,
                                     Long scopeId,
                                     String version) {
        return new TokenSecurityState(
            userId, tenantId, scopeType, scopeId, version,
            LocalDateTime.of(2026, 8, 24, 0, 0), 1L, 1L
        );
    }
}
