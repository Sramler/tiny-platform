package com.tiny.platform.application.controller.audit;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformTokenDebugControllerTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void decode_shouldReturnExpectedClaimsAndCollections() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        PlatformTokenDebugController controller = new PlatformTokenDebugController(decoder);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        Map<String, Object> claims = Map.of(
            "authorities", List.of("system:audit:auth:view", "ROLE_ADMIN"),
            "permissions", List.of("system:audit:auth:view", "system:audit:authentication:view"),
            "roleCodes", List.of("ROLE_ADMIN"),
            "permissionsVersion", "perm-v-20260414",
            "activeScopeType", "PLATFORM",
            "activeTenantId", 9
        );
        Jwt jwt = new Jwt(
            "token-value",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "none"),
            claims
        );
        when(decoder.decode("token-value")).thenReturn(jwt);

        var response = controller.decode(new PlatformTokenDebugController.TokenDecodeRequest("token-value"));

        var body = Objects.requireNonNull(response.getBody());
        assertThat(body.permissions()).containsExactly("system:audit:auth:view", "system:audit:authentication:view");
        assertThat(body.roleCodes()).containsExactly("ROLE_ADMIN");
        assertThat(body.permissionsVersion()).isEqualTo("perm-v-20260414");
        assertThat(body.activeScopeType()).isEqualTo("PLATFORM");
        assertThat(body.activeTenantId()).isEqualTo(9L);
        assertThat(body.authorities()).contains("system:audit:auth:view");
        assertThat(body.authorities()).doesNotContain("ROLE_ADMIN");
    }

    @Test
    void decode_shouldRejectWhenNotPlatformScope() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        PlatformTokenDebugController controller = new PlatformTokenDebugController(decoder);
        TenantContext.setActiveTenantId(9L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);

        assertThatThrownBy(() -> controller.decode(new PlatformTokenDebugController.TokenDecodeRequest("token-value")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("仅平台作用域允许使用 token decode 工具");
    }
}

