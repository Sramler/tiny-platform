package com.tiny.platform.application.controller.idempotent.security;

import com.tiny.platform.application.controller.idempotent.controller.IdempotentMetricsController;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.idempotent.starter.properties.IdempotentProperties;
import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdempotentMetricsAccessGuardTest {

    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final IdempotentProperties properties = new IdempotentProperties();
    private final IdempotentMetricsAccessGuard guard = new IdempotentMetricsAccessGuard(tenantRepository, properties);

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
    void should_allow_platform_admin_with_ops_authority_from_session_principal() {
        when(tenantRepository.findByCode(properties.getOps().getPlatformTenantCode()))
                .thenReturn(Optional.of(platformTenant(1L)));

        SecurityUser principal = new SecurityUser(
                10L,
                1L,
                "admin",
                "",
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority(IdempotentMetricsAccessGuard.PLATFORM_METRICS_AUTHORITY)
                ),
                true,
                true,
                true,
                true
        );

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                "n/a",
                principal.getAuthorities()
        );

        assertThat(guard.canAccess(authentication)).isTrue();
    }

    @Test
    void should_reject_admin_from_non_platform_tenant() {
        when(tenantRepository.findByCode(properties.getOps().getPlatformTenantCode()))
                .thenReturn(Optional.of(platformTenant(1L)));

        SecurityUser principal = new SecurityUser(
                10L,
                2L,
                "tenant-admin",
                "",
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority(IdempotentMetricsAccessGuard.PLATFORM_METRICS_AUTHORITY)
                ),
                true,
                true,
                true,
                true
        );

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                "n/a",
                principal.getAuthorities()
        );

        assertThat(guard.canAccess(authentication)).isFalse();
    }

    @Test
    void should_reject_platform_admin_without_ops_authority() {
        when(tenantRepository.findByCode(properties.getOps().getPlatformTenantCode()))
                .thenReturn(Optional.of(platformTenant(1L)));

        SecurityUser principal = new SecurityUser(
                10L,
                1L,
                "admin",
                "",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
                true,
                true,
                true,
                true
        );

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                "n/a",
                principal.getAuthorities()
        );

        assertThat(guard.canAccess(authentication)).isFalse();
    }

    @Test
    void should_allow_platform_admin_from_jwt_claims_when_tenant_context_exists() {
        when(tenantRepository.findByCode(properties.getOps().getPlatformTenantCode()))
                .thenReturn(Optional.of(platformTenant(1L)));
        TenantContext.setActiveTenantId(1L);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("activeTenantId", "1")
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt,
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority(IdempotentMetricsAccessGuard.PLATFORM_METRICS_AUTHORITY)
                )
        );

        assertThat(guard.canAccess(authentication)).isTrue();
    }

    @Test
    void should_allow_platform_admin_from_active_tenant_claim_when_context_missing() {
        when(tenantRepository.findByCode(properties.getOps().getPlatformTenantCode()))
                .thenReturn(Optional.of(platformTenant(1L)));

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("activeTenantId", "1")
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt,
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority(IdempotentMetricsAccessGuard.PLATFORM_METRICS_AUTHORITY)
                )
        );

        assertThat(guard.canAccess(authentication)).isTrue();
    }

    @Test
    void should_honor_configured_platform_tenant_code() {
        properties.getOps().setPlatformTenantCode("platform-main");
        when(tenantRepository.findByCode("platform-main"))
                .thenReturn(Optional.of(platformTenant(9L, "platform-main")));

        SecurityUser principal = new SecurityUser(
                10L,
                9L,
                "platform-admin",
                "",
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority(IdempotentMetricsAccessGuard.PLATFORM_METRICS_AUTHORITY)
                ),
                true,
                true,
                true,
                true
        );

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                "n/a",
                principal.getAuthorities()
        );

        assertThat(guard.canAccess(authentication)).isTrue();
    }

    private static Tenant platformTenant(Long id) {
        return platformTenant(id, "default");
    }

    private static Tenant platformTenant(Long id, String code) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setCode(code);
        tenant.setName("默认租户");
        tenant.setEnabled(true);
        return tenant;
    }
}
