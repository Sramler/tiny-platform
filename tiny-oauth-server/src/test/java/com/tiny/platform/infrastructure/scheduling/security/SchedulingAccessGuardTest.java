package com.tiny.platform.infrastructure.scheduling.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingAccessGuardTest {

    private final SchedulingAccessGuard guard = new SchedulingAccessGuard();

    @Test
    void canRead_shouldReturnFalseWhenUnauthenticated() {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("user", "n/a", List.of());
        authentication.setAuthenticated(false);

        assertThat(guard.canRead(authentication)).isFalse();
    }

    @Test
    void canRead_shouldReturnTrueWhenHasReadAuthority() {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "user",
                        "n/a",
                        List.of(new SimpleGrantedAuthority(SchedulingAccessGuard.READ_AUTHORITY))
                );

        assertThat(guard.canRead(authentication)).isTrue();
    }

    @Test
    void canManageConfig_shouldReturnTrueWhenHasWildcardAuthority() {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "user",
                        "n/a",
                        List.of(new SimpleGrantedAuthority("scheduling:*"))
                );

        assertThat(guard.canManageConfig(authentication)).isTrue();
        assertThat(guard.canOperateRun(authentication)).isTrue();
        assertThat(guard.canViewAudit(authentication)).isTrue();
        assertThat(guard.canViewClusterStatus(authentication)).isTrue();
        assertThat(guard.canRead(authentication)).isTrue();
    }

    @Test
    void canOperateRun_shouldReturnFalseWhenMissingAuthority() {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "user",
                        "n/a",
                        List.of(new SimpleGrantedAuthority(SchedulingAccessGuard.READ_AUTHORITY))
                );

        assertThat(guard.canOperateRun(authentication)).isFalse();
    }

    @Test
    void canViewAudit_shouldReturnTrueWhenHasAuditAuthority() {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "user",
                        "n/a",
                        List.of(new SimpleGrantedAuthority(SchedulingAccessGuard.VIEW_AUDIT_AUTHORITY))
                );

        assertThat(guard.canViewAudit(authentication)).isTrue();
        assertThat(guard.canRead(authentication)).isFalse();
    }

    @Test
    void canViewClusterStatus_shouldReturnTrueWhenHasClusterStatusAuthority() {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "user",
                        "n/a",
                        List.of(new SimpleGrantedAuthority(SchedulingAccessGuard.VIEW_CLUSTER_STATUS_AUTHORITY))
                );

        assertThat(guard.canViewClusterStatus(authentication)).isTrue();
        assertThat(guard.canRead(authentication)).isFalse();
    }
}

