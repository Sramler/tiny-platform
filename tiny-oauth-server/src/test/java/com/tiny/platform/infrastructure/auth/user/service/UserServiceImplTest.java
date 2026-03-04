package com.tiny.platform.infrastructure.auth.user.service;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.dto.UserCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.user.dto.UserRequestDto;
import com.tiny.platform.infrastructure.auth.user.dto.UserResponseDto;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationMethodRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Test
    void should_include_failed_login_fields_in_user_list_dto() {
        UserRepository userRepository = mock(UserRepository.class);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                mock(PasswordEncoder.class),
                mock(RoleRepository.class),
                mock(UserAuthenticationMethodRepository.class)
        );

        TenantContext.setTenantId(1L);

        LocalDateTime lastFailedAt = LocalDateTime.of(2026, 3, 4, 10, 15);
        User existingUser = new User();
        existingUser.setId(11L);
        existingUser.setTenantId(1L);
        existingUser.setUsername("carol");
        existingUser.setNickname("Carol");
        existingUser.setEnabled(true);
        existingUser.setAccountNonExpired(true);
        existingUser.setAccountNonLocked(false);
        existingUser.setCredentialsNonExpired(true);
        existingUser.setLastLoginAt(LocalDateTime.of(2026, 3, 4, 10, 0));
        existingUser.setFailedLoginCount(5);
        existingUser.setLastFailedLoginAt(lastFailedAt);

        PageRequest pageable = PageRequest.of(0, 10);
        when(userRepository.findAll(org.mockito.ArgumentMatchers.<Specification<User>>any(), eq(pageable)))
            .thenReturn(new PageImpl<>(java.util.List.of(existingUser), pageable, 1));

        Page<UserResponseDto> page = service.users(new UserRequestDto(), pageable);

        assertThat(page.getContent()).hasSize(1);
        UserResponseDto dto = page.getContent().getFirst();
        assertThat(dto.getUsername()).isEqualTo("carol");
        assertThat(dto.isAccountNonLocked()).isFalse();
        assertThat(dto.getFailedLoginCount()).isEqualTo(5);
        assertThat(dto.getLastLoginAt()).isEqualTo(LocalDateTime.of(2026, 3, 4, 10, 0));
        assertThat(dto.getLastFailedLoginAt()).isEqualTo(lastFailedAt);
    }
}
