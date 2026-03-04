package com.tiny.platform.infrastructure.auth.user.service;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.dto.UserCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationMethodRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceImplTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void should_clear_failed_login_state_when_admin_unlocks_user() {
        UserRepository userRepository = mock(UserRepository.class);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                mock(PasswordEncoder.class),
                mock(RoleRepository.class),
                mock(UserAuthenticationMethodRepository.class)
        );

        TenantContext.setTenantId(1L);

        User existingUser = new User();
        existingUser.setId(9L);
        existingUser.setTenantId(1L);
        existingUser.setUsername("alice");
        existingUser.setNickname("Alice");
        existingUser.setEnabled(true);
        existingUser.setAccountNonExpired(true);
        existingUser.setAccountNonLocked(false);
        existingUser.setCredentialsNonExpired(true);
        existingUser.setFailedLoginCount(7);
        existingUser.setLastFailedLoginAt(LocalDateTime.now().minusMinutes(3));

        when(userRepository.findByIdAndTenantId(9L, 1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.findUserByUsernameAndTenantId("alice", 1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserCreateUpdateDto dto = new UserCreateUpdateDto();
        dto.setId(9L);
        dto.setUsername("alice");
        dto.setNickname("Alice");
        dto.setEnabled(true);
        dto.setAccountNonExpired(true);
        dto.setAccountNonLocked(true);
        dto.setCredentialsNonExpired(true);

        User updated = service.updateFromDto(dto);

        assertThat(updated.isAccountNonLocked()).isTrue();
        assertThat(updated.getFailedLoginCount()).isZero();
        assertThat(updated.getLastFailedLoginAt()).isNull();
        verify(userRepository).save(existingUser);
    }

    @Test
    void should_keep_failed_login_state_when_user_remains_locked() {
        UserRepository userRepository = mock(UserRepository.class);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                mock(PasswordEncoder.class),
                mock(RoleRepository.class),
                mock(UserAuthenticationMethodRepository.class)
        );

        TenantContext.setTenantId(1L);

        LocalDateTime failedAt = LocalDateTime.now().minusMinutes(2);
        User existingUser = new User();
        existingUser.setId(10L);
        existingUser.setTenantId(1L);
        existingUser.setUsername("bob");
        existingUser.setNickname("Bob");
        existingUser.setEnabled(true);
        existingUser.setAccountNonExpired(true);
        existingUser.setAccountNonLocked(false);
        existingUser.setCredentialsNonExpired(true);
        existingUser.setFailedLoginCount(4);
        existingUser.setLastFailedLoginAt(failedAt);

        when(userRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.findUserByUsernameAndTenantId("bob", 1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserCreateUpdateDto dto = new UserCreateUpdateDto();
        dto.setId(10L);
        dto.setUsername("bob");
        dto.setNickname("Bob");
        dto.setEnabled(true);
        dto.setAccountNonExpired(true);
        dto.setAccountNonLocked(false);
        dto.setCredentialsNonExpired(true);

        User updated = service.updateFromDto(dto);

        assertThat(updated.isAccountNonLocked()).isFalse();
        assertThat(updated.getFailedLoginCount()).isEqualTo(4);
        assertThat(updated.getLastFailedLoginAt()).isEqualTo(failedAt);
    }
}
