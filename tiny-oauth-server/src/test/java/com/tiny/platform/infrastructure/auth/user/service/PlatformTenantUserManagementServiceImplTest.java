package com.tiny.platform.infrastructure.auth.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tiny.platform.core.oauth.security.LoginFailurePolicy;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.dto.UserRequestDto;
import com.tiny.platform.infrastructure.auth.user.dto.UserResponseDto;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

class PlatformTenantUserManagementServiceImplTest {

    private TenantUserRepository tenantUserRepository;
    private UserRepository userRepository;
    private LoginFailurePolicy loginFailurePolicy;
    private PlatformTenantUserManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        tenantUserRepository = mock(TenantUserRepository.class);
        userRepository = mock(UserRepository.class);
        loginFailurePolicy = mock(LoginFailurePolicy.class);
        service = new PlatformTenantUserManagementServiceImpl(
            tenantUserRepository,
            userRepository,
            loginFailurePolicy
        );
    }

    @Test
    void list_shouldReturnTenantMembershipScopedUsers() {
        Pageable pageable = PageRequest.of(0, 10);
        UserRequestDto query = new UserRequestDto();
        query.setUsername("alice");
        User user = user(30L, "alice", "Alice");
        when(tenantUserRepository.findUserIdsByTenantIdAndStatus(9L, "ACTIVE")).thenReturn(List.of(30L));
        when(userRepository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(user), pageable, 1));
        when(loginFailurePolicy.isTemporarilyLocked(eq(user), any(LocalDateTime.class))).thenReturn(false);

        var result = service.list(9L, query, pageable);

        assertThat(result.getContent()).hasSize(1);
        UserResponseDto dto = result.getContent().getFirst();
        assertThat(dto.getId()).isEqualTo(30L);
        assertThat(dto.getUsername()).isEqualTo("alice");
        verify(tenantUserRepository).findUserIdsByTenantIdAndStatus(9L, "ACTIVE");
    }

    @Test
    void list_shouldReturnEmptyWhenTenantHasNoActiveUsers() {
        Pageable pageable = PageRequest.of(0, 10);
        when(tenantUserRepository.findUserIdsByTenantIdAndStatus(9L, "ACTIVE")).thenReturn(List.of());

        var result = service.list(9L, new UserRequestDto(), pageable);

        assertThat(result.getContent()).isEmpty();
        verify(userRepository, never()).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void get_shouldReturnEmptyWhenMembershipMissing() {
        when(tenantUserRepository.existsByTenantIdAndUserIdAndStatus(9L, 30L, "ACTIVE")).thenReturn(false);

        Optional<UserResponseDto> result = service.get(9L, 30L);

        assertThat(result).isEmpty();
        verify(userRepository, never()).findById(30L);
    }

    @Test
    void get_shouldReturnUserDetailWhenMembershipExists() {
        User user = user(30L, "alice", "Alice");
        when(tenantUserRepository.existsByTenantIdAndUserIdAndStatus(9L, 30L, "ACTIVE")).thenReturn(true);
        when(userRepository.findById(30L)).thenReturn(Optional.of(user));
        when(loginFailurePolicy.isTemporarilyLocked(eq(user), any(LocalDateTime.class))).thenReturn(false);

        Optional<UserResponseDto> result = service.get(9L, 30L);

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("alice");
    }

    private User user(Long id, String username, String nickname) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(nickname);
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);
        return user;
    }
}
