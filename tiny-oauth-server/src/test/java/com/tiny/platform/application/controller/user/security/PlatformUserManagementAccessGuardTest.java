package com.tiny.platform.application.controller.user.security;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformUserManagementAccessGuardTest {

    private final PlatformUserManagementAccessGuard guard = new PlatformUserManagementAccessGuard();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void canRead_whenPlatformScopeAndPlatformUserListAuthority_returnsTrue() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            "platform-admin",
            "n/a",
            List.of(new SimpleGrantedAuthority("platform:user:list"))
        );

        assertThat(guard.canRead(authentication)).isTrue();
    }

    @Test
    void canRead_whenPlatformScopeAndPlatformUserViewAuthority_returnsTrue() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            "platform-admin",
            "n/a",
            List.of(new SimpleGrantedAuthority("platform:user:view"))
        );

        assertThat(guard.canRead(authentication)).isTrue();
    }

    @Test
    void canCreate_whenTenantScopeEvenWithAuthority_returnsFalse() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            "platform-admin",
            "n/a",
            List.of(new SimpleGrantedAuthority("platform:user:create"))
        );

        assertThat(guard.canCreate(authentication)).isFalse();
    }

    @Test
    void canUpdate_whenPlatformScopeAndPlatformUserEditAuthority_returnsTrue() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            "platform-admin",
            "n/a",
            List.of(new SimpleGrantedAuthority("platform:user:edit"))
        );

        assertThat(guard.canUpdate(authentication)).isTrue();
    }

    @Test
    void canUpdate_whenNoMatchingAuthority_returnsFalse() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            "platform-admin",
            "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
        );

        assertThat(guard.canUpdate(authentication)).isFalse();
    }
}
