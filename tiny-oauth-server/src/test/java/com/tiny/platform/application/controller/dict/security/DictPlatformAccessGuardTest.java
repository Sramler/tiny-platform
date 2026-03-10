package com.tiny.platform.application.controller.dict.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DictPlatformAccessGuardTest {

    private final DictPlatformAccessGuard guard = new DictPlatformAccessGuard();

    @Test
    void canManagePlatformDict_whenRoleAdmin_returnsTrue() {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "admin",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        assertThat(guard.canManagePlatformDict(authentication)).isTrue();
    }

    @Test
    void canManagePlatformDict_whenNoAdminAuthority_returnsFalse() {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "user",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        assertThat(guard.canManagePlatformDict(authentication)).isFalse();
    }
}
