package com.tiny.platform.application.controller.idempotent.security;

import com.tiny.platform.application.controller.idempotent.controller.IdempotentMetricsController;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotentMetricsAccessGuardTest {

    private final IdempotentMetricsAccessGuard guard = new IdempotentMetricsAccessGuard();

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void should_require_platform_guard_on_metrics_controller() {
        PreAuthorize annotation = IdempotentMetricsController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("@idempotentMetricsAccessGuard.canAccess(authentication)");
    }

    @Test
    void should_allow_platform_user_with_ops_authority() {
        TenantContext.setActiveTenantId(1L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);

        UsernamePasswordAuthenticationToken authentication = sessionAuth(
                IdempotentMetricsAccessGuard.PLATFORM_METRICS_AUTHORITY
        );

        assertThat(guard.canAccess(authentication)).isTrue();
    }

    @Test
    void should_reject_non_platform_tenant_even_with_ops_authority() {
        TenantContext.setActiveTenantId(2L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);

        UsernamePasswordAuthenticationToken authentication = sessionAuth(
                "ROLE_ADMIN",
                IdempotentMetricsAccessGuard.PLATFORM_METRICS_AUTHORITY
        );

        assertThat(guard.canAccess(authentication)).isFalse();
    }

    @Test
    void should_reject_platform_user_without_ops_authority() {
        TenantContext.setActiveTenantId(1L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);

        UsernamePasswordAuthenticationToken authentication = sessionAuth("ROLE_ADMIN");

        assertThat(guard.canAccess(authentication)).isFalse();
    }

    @Test
    void should_reject_when_scope_type_not_set() {
        TenantContext.setActiveTenantId(1L);

        UsernamePasswordAuthenticationToken authentication = sessionAuth(
                IdempotentMetricsAccessGuard.PLATFORM_METRICS_AUTHORITY
        );

        assertThat(guard.canAccess(authentication)).isFalse();
    }

    private static UsernamePasswordAuthenticationToken sessionAuth(String... authorities) {
        List<SimpleGrantedAuthority> grantedAuthorities = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new UsernamePasswordAuthenticationToken("test-user", "n/a", grantedAuthorities);
    }
}
