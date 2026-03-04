package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserDetailsServiceImplTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void should_reject_when_tenant_missing_or_user_not_found() {
        UserRepository userRepository = mock(UserRepository.class);
        UserDetailsServiceImpl service = new UserDetailsServiceImpl(userRepository);

        TenantContext.clear();
        assertThatThrownBy(() -> service.loadUserByUsername("alice"))
            .isInstanceOf(UsernameNotFoundException.class)
            .hasMessageContaining("缺少租户信息");

        TenantContext.setTenantId(1L);
        when(userRepository.findUserByUsernameAndTenantId("alice", 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.loadUserByUsername("alice"))
            .isInstanceOf(UsernameNotFoundException.class)
            .hasMessageContaining("用户不存在");
    }

    @Test
    void should_load_user_without_mutating_login_metadata_and_return_security_user() {
        UserRepository userRepository = mock(UserRepository.class);
        UserDetailsServiceImpl service = new UserDetailsServiceImpl(userRepository);
        TenantContext.setTenantId(2L);

        User user = new User();
        user.setId(10L);
        user.setTenantId(2L);
        user.setUsername("alice");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);
        Role role = new Role();
        role.setName("ROLE_ADMIN");
        user.setRoles(Set.of(role));

        when(userRepository.findUserByUsernameAndTenantId("alice", 2L)).thenReturn(Optional.of(user));

        SecurityUser result = (SecurityUser) service.loadUserByUsername("alice");

        assertThat(result.getUserId()).isEqualTo(10L);
        assertThat(result.getTenantId()).isEqualTo(2L);
        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(user.getLastLoginAt()).isNull();
        verify(userRepository).findUserByUsernameAndTenantId("alice", 2L);
    }
}
