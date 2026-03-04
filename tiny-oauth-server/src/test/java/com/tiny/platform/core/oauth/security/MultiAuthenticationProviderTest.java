package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.config.CustomWebAuthenticationDetailsSource;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.service.SecurityService;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationMethodRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultiAuthenticationProviderTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void should_cover_guard_clauses_and_supports() {
        MultiAuthenticationProvider provider = newProvider();

        assertThat(provider.supports(MultiFactorAuthenticationToken.class)).isTrue();
        assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isTrue();
        assertThat(provider.supports(String.class)).isFalse();

        assertThatThrownBy(() -> provider.authenticate(mock(Authentication.class)))
            .isInstanceOf(BadCredentialsException.class)
            .hasMessageContaining("不支持的认证类型");

        UsernamePasswordAuthenticationToken blank = UsernamePasswordAuthenticationToken.unauthenticated("", "x");
        assertThatThrownBy(() -> provider.authenticate(blank))
            .isInstanceOf(BadCredentialsException.class)
            .hasMessageContaining("用户名不能为空");
    }

    @Test
    void should_reject_when_tenant_missing_user_missing_or_method_invalid() {
        UserRepository userRepository = mock(UserRepository.class);
        UserAuthenticationMethodRepository methodRepository = mock(UserAuthenticationMethodRepository.class);
        MultiAuthenticationProvider provider = newProvider(userRepository, methodRepository, mock(PasswordEncoder.class),
            mock(UserDetailsService.class), mock(TotpService.class), mock(SecurityService.class));

        UsernamePasswordAuthenticationToken auth = auth("alice", "raw", "LOCAL", "PASSWORD");

        TenantContext.clear();
        assertThatThrownBy(() -> provider.authenticate(auth))
            .isInstanceOf(BadCredentialsException.class)
            .hasMessageContaining("缺少租户信息");

        TenantContext.setTenantId(1L);
        when(userRepository.findUserByUsernameAndTenantId("alice", 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> provider.authenticate(auth))
            .isInstanceOf(BadCredentialsException.class)
            .hasMessageContaining("用户不存在");

        User user = user(1L, "alice");
        when(userRepository.findUserByUsernameAndTenantId("alice", 1L)).thenReturn(Optional.of(user));
        when(methodRepository.findEnabledMethodsByUserId(1L, 1L)).thenReturn(List.of());
        UsernamePasswordAuthenticationToken noSelection = UsernamePasswordAuthenticationToken.unauthenticated("alice", "raw");
        assertThatThrownBy(() -> provider.authenticate(noSelection))
            .isInstanceOf(BadCredentialsException.class)
            .hasMessageContaining("未配置任何认证方式");

        when(methodRepository.findEnabledMethodsByUserId(1L, 1L))
            .thenReturn(List.of(method(10L, "LOCAL", "TOTP", Map.of("secret", "ABC"))));
        assertThatThrownBy(() -> provider.authenticate(auth))
            .isInstanceOf(BadCredentialsException.class)
            .hasMessageContaining("未配置此认证方式");

        UsernamePasswordAuthenticationToken invalid = auth("alice", "raw", "BAD!", "PASSWORD");
        assertThatThrownBy(() -> provider.authenticate(invalid))
            .isInstanceOf(BadCredentialsException.class)
            .hasMessageContaining("认证参数格式错误");
    }

    @Test
    void should_auto_select_single_password_method_and_record_verification() {
        UserRepository userRepository = mock(UserRepository.class);
        UserAuthenticationMethodRepository methodRepository = mock(UserAuthenticationMethodRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        MultiAuthenticationProvider provider = newProvider(userRepository, methodRepository, passwordEncoder,
            userDetailsService, mock(TotpService.class), mock(SecurityService.class));

        TenantContext.setTenantId(1L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        User user = user(1L, "alice");
        UserAuthenticationMethod passwordMethod = method(11L, "LOCAL", "PASSWORD", Map.of("password", "{bcrypt}encoded"));
        SecurityUser securityUser = securityUser(user);

        when(userRepository.findUserByUsernameAndTenantId("alice", 1L)).thenReturn(Optional.of(user));
        when(methodRepository.findEnabledMethodsByUserId(1L, 1L)).thenReturn(List.of(passwordMethod));
        when(passwordEncoder.matches("raw", "{bcrypt}encoded")).thenReturn(true);
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(securityUser);

        Authentication result = provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("alice", "raw"));

        assertThat(result).isInstanceOf(MultiFactorAuthenticationToken.class);
        MultiFactorAuthenticationToken token = (MultiFactorAuthenticationToken) result;
        assertThat(token.isAuthenticated()).isTrue();
        assertThat(token.getProvider()).isEqualTo(MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL);
        assertThat(token.getCompletedFactors()).containsExactly(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD);
        assertThat(token.getDetails()).isEqualTo(securityUser);
        assertThat(passwordMethod.getLastVerifiedAt()).isNotNull();
        assertThat(passwordMethod.getLastVerifiedIp()).isEqualTo("127.0.0.1");
        verify(methodRepository).save(passwordMethod);
        verify(userDetailsService).loadUserByUsername("alice");
    }

    @Test
    void should_handle_mfa_password_then_totp_and_non_required_totp() {
        UserRepository userRepository = mock(UserRepository.class);
        UserAuthenticationMethodRepository methodRepository = mock(UserAuthenticationMethodRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        TotpService totpService = mock(TotpService.class);
        SecurityService securityService = mock(SecurityService.class);
        MultiAuthenticationProvider provider = newProvider(userRepository, methodRepository, passwordEncoder,
            userDetailsService, totpService, securityService);

        TenantContext.setTenantId(1L);
        User user = user(1L, "alice");
        UserAuthenticationMethod passwordMethod = method(21L, "LOCAL", "PASSWORD", Map.of("password", "{bcrypt}encoded"));
        UserAuthenticationMethod totpMethod = method(22L, "LOCAL", "TOTP", Map.of("secret", "BASE32SECRET"));
        SecurityUser securityUser = securityUser(user);
        when(userRepository.findUserByUsernameAndTenantId("alice", 1L)).thenReturn(Optional.of(user));
        when(methodRepository.findEnabledMethodsByUserId(1L, 1L)).thenReturn(List.of(passwordMethod, totpMethod));
        when(passwordEncoder.matches("raw", "{bcrypt}encoded")).thenReturn(true);
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(securityUser);

        when(securityService.getSecurityStatus(user)).thenReturn(Map.of("requireTotp", false));
        Authentication noTotpRequired = provider.authenticate(auth("alice", "raw", "LOCAL", "PASSWORD"));
        assertThat(((MultiFactorAuthenticationToken) noTotpRequired).getCompletedFactors())
            .containsExactly(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD);

        when(securityService.getSecurityStatus(user)).thenReturn(Map.of("requireTotp", true));
        Authentication partial = provider.authenticate(auth("alice", "raw", "LOCAL", "PASSWORD"));
        assertThat(((MultiFactorAuthenticationToken) partial).getCompletedFactors())
            .containsExactly(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD);

        when(totpService.verify("BASE32SECRET", "123456")).thenReturn(true);
        Authentication totp = provider.authenticate(auth("alice", "123456", "LOCAL", "TOTP"));
        assertThat(((MultiFactorAuthenticationToken) totp).getCompletedFactors())
            .containsExactly(MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP);
        verify(userDetailsService, times(3)).loadUserByUsername("alice");
    }

    private static MultiAuthenticationProvider newProvider() {
        return newProvider(mock(UserRepository.class), mock(UserAuthenticationMethodRepository.class), mock(PasswordEncoder.class),
            mock(UserDetailsService.class), mock(TotpService.class), mock(SecurityService.class));
    }

    private static MultiAuthenticationProvider newProvider(UserRepository userRepository,
                                                           UserAuthenticationMethodRepository methodRepository,
                                                           PasswordEncoder passwordEncoder,
                                                           UserDetailsService userDetailsService,
                                                           TotpService totpService,
                                                           SecurityService securityService) {
        return new MultiAuthenticationProvider(userRepository, methodRepository, passwordEncoder, userDetailsService, totpService, securityService);
    }

    private static UsernamePasswordAuthenticationToken auth(String username, String credential, String provider, String type) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("authenticationProvider", provider);
        request.setParameter("authenticationType", type);
        UsernamePasswordAuthenticationToken token = UsernamePasswordAuthenticationToken.unauthenticated(username, credential);
        token.setDetails(new CustomWebAuthenticationDetailsSource().buildDetails(request));
        return token;
    }

    private static UserAuthenticationMethod method(Long id, String provider, String type, Map<String, Object> config) {
        UserAuthenticationMethod method = new UserAuthenticationMethod();
        method.setId(id);
        method.setUserId(1L);
        method.setTenantId(1L);
        method.setAuthenticationProvider(provider);
        method.setAuthenticationType(type);
        method.setAuthenticationConfiguration(config);
        method.setIsMethodEnabled(true);
        return method;
    }

    private static User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setTenantId(1L);
        user.setUsername(username);
        Role role = new Role();
        role.setName("ROLE_USER");
        user.setRoles(Set.of(role));
        return user;
    }

    private static SecurityUser securityUser(User user) {
        return new SecurityUser(
            user.getId(), user.getTenantId(), user.getUsername(), "",
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            true, true, true, true
        );
    }
}
