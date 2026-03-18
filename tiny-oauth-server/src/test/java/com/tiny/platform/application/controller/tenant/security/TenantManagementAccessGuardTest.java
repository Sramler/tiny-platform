package com.tiny.platform.application.controller.tenant.security;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.tenant.config.PlatformTenantProperties;
import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantManagementAccessGuardTest {

    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final PlatformTenantProperties platformTenantProperties = new PlatformTenantProperties();
    private final TenantManagementAccessGuard guard = new TenantManagementAccessGuard(tenantRepository, platformTenantProperties);

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void should_allow_platform_admin_from_active_tenant_claim_when_context_missing() {
        Tenant platformTenant = new Tenant();
        platformTenant.setId(1L);
        platformTenant.setCode("default");
        when(tenantRepository.findByCode("default")).thenReturn(Optional.of(platformTenant));

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("activeTenantId", "1")
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        assertThat(guard.canManage(authentication)).isTrue();
    }
}
