package com.tiny.platform.application.controller.dict.security;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DictPlatformAccessGuardTest {

    private final DictPlatformAccessGuard guard = new DictPlatformAccessGuard();

    @Test
    void canManagePlatformDict_whenHasDictPlatformManage_returnsTrue() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "admin",
                "n/a",
                List.of(new SimpleGrantedAuthority("dict:platform:manage"))
        );

        assertThat(guard.canManagePlatformDict(authentication)).isTrue();
        TenantContext.clear();
    }

    @Test
    void canManagePlatformDict_whenOnlyRoleAdmin_returnsFalse() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "admin",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        assertThat(guard.canManagePlatformDict(authentication)).isFalse();
        TenantContext.clear();
    }

    @Test
    void canManagePlatformDict_whenNoRelevantAuthority_returnsFalse() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "user",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        assertThat(guard.canManagePlatformDict(authentication)).isFalse();
        TenantContext.clear();
    }

    @Test
    void canManagePlatformDict_whenTenantScopeEvenWithAuthority_returnsFalse() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "platform-admin",
                "n/a",
                List.of(new SimpleGrantedAuthority("dict:platform:manage"))
        );

        assertThat(guard.canManagePlatformDict(authentication)).isFalse();
        TenantContext.clear();
    }
}
