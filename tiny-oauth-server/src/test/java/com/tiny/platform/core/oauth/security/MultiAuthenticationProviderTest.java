package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.config.CustomWebAuthenticationDetailsSource;
import com.tiny.platform.core.oauth.config.MfaProperties;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.service.SecurityService;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationMethodRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
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

    private final AuthUserResolutionService authUserResolutionService = mock(AuthUserResolutionService.class);

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
        TenantRepository tenantRepository = mock(TenantRepository.class);
        MultiAuthenticationProvider provider = newProvider(userRepository, tenantRepository, methodRepository, mock(PasswordEncoder.class),
            mock(UserDetailsService.class), mock(TotpService.class), mock(SecurityService.class));

        UsernamePasswordAuthenticationToken auth = auth("alice", "raw", "LOCAL", "PASSWORD");

        TenantContext.clear();
        assertThatThrownBy(() -> provider.authenticate(auth))
            .isInstanceOf(BadCredentialsException.class)
            .hasMessageContaining("缺少租户信息");

        TenantContext.setActiveTenantId(1L);
        when(tenantRepository.isTenantFrozen(1L)).thenReturn(false);
        when(authUserResolutionService.resolveUserRecordInActiveTenant("alice", 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> provider.authenticate(auth))
            .isInstanceOf(BadCredentialsException.class)
            .hasMessageContaining("用户不存在");

        User user = user(1L, "alice");
        when(authUserResolutionService.resolveUserRecordInActiveTenant("alice", 1L)).thenReturn(Optional.of(user));
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
    void should_fallback_to_session_active_tenant_when_context_missing() {
        UserRepository userRepository = mock(UserRepository.class);
        UserAuthenticationMethodRepository methodRepository = mock(UserAuthenticationMethodRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        MultiAuthenticationProvider provider = newProvider(userRepository, mock(TenantRepository.class), methodRepository, passwordEncoder,
            userDetailsService, mock(TotpService.class), mock(SecurityService.class));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 6L);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        User user = user(1L, "alice");
        UserAuthenticationMethod passwordMethod = method(11L, "LOCAL", "PASSWORD", Map.of("password", "{bcrypt}encoded"));
        SecurityUser securityUser = securityUser(user);

        when(authUserResolutionService.resolveUserRecordInActiveTenant("alice", 6L)).thenReturn(Optional.of(user));
        when(methodRepository.findEnabledMethodsByUserId(1L, 6L)).thenReturn(List.of(passwordMethod));
        when(passwordEncoder.matches("raw", "{bcrypt}encoded")).thenReturn(true);
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(securityUser);

        Authentication result = provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("alice", "raw"));

        assertThat(result).isInstanceOf(MultiFactorAuthenticationToken.class);
        verify(authUserResolutionService).resolveUserRecordInActiveTenant("alice", 6L);
    }

    @Test
    void should_auto_select_single_password_method_and_record_verification() {
        UserRepository userRepository = mock(UserRepository.class);
        UserAuthenticationMethodRepository methodRepository = mock(UserAuthenticationMethodRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        MultiAuthenticationProvider provider = newProvider(userRepository, mock(TenantRepository.class), methodRepository, passwordEncoder,
            userDetailsService, mock(TotpService.class), mock(SecurityService.class));

        TenantContext.setActiveTenantId(1L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        User user = user(1L, "alice");
        UserAuthenticationMethod passwordMethod = method(11L, "LOCAL", "PASSWORD", Map.of("password", "{bcrypt}encoded"));
        SecurityUser securityUser = securityUser(user);

        when(authUserResolutionService.resolveUserRecordInActiveTenant("alice", 1L)).thenReturn(Optional.of(user));
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
        assertThat(token.getAuthorities())
            .extracting(authority -> authority.getAuthority())
            .contains("ROLE_USER", AuthenticationFactorAuthorities.FACTOR_AUTHORITY_PREFIX + "PASSWORD");
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
        MultiAuthenticationProvider provider = newProvider(userRepository, mock(TenantRepository.class), methodRepository, passwordEncoder,
            userDetailsService, totpService, securityService);

        TenantContext.setActiveTenantId(1L);
        User user = user(1L, "alice");
        UserAuthenticationMethod passwordMethod = method(21L, "LOCAL", "PASSWORD", Map.of("password", "{bcrypt}encoded"));
        UserAuthenticationMethod totpMethod = method(22L, "LOCAL", "TOTP", Map.of("secret", "BASE32SECRET"));
        SecurityUser securityUser = securityUser(user);
        when(authUserResolutionService.resolveUserRecordInActiveTenant("alice", 1L)).thenReturn(Optional.of(user));
        when(methodRepository.findEnabledMethodsByUserId(1L, 1L)).thenReturn(List.of(passwordMethod, totpMethod));
        when(passwordEncoder.matches("raw", "{bcrypt}encoded")).thenReturn(true);
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(securityUser);

        when(securityService.getSecurityStatus(user)).thenReturn(Map.of("requireTotp", false));
        Authentication noTotpRequired = provider.authenticate(auth("alice", "raw", "LOCAL", "PASSWORD"));
        assertThat(((MultiFactorAuthenticationToken) noTotpRequired).getCompletedFactors())
            .containsExactly(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD);
        assertThat(noTotpRequired.getAuthorities())
            .extracting(authority -> authority.getAuthority())
            .contains(AuthenticationFactorAuthorities.FACTOR_AUTHORITY_PREFIX + "PASSWORD");

        assertThatThrownBy(() -> provider.authenticate(auth("alice", "123456", "LOCAL", "TOTP")))
            .isInstanceOf(BadCredentialsException.class)
            .hasMessageContaining("必须先完成前置认证步骤");

        when(securityService.getSecurityStatus(user)).thenReturn(Map.of("requireTotp", true));
        Authentication partial = provider.authenticate(auth("alice", "raw", "LOCAL", "PASSWORD"));
        assertThat(partial.isAuthenticated()).isFalse();
        assertThat(((MultiFactorAuthenticationToken) partial).getCompletedFactors())
            .containsExactly(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD);
        assertThat(partial.getAuthorities())
            .extracting(authority -> authority.getAuthority())
            .contains(AuthenticationFactorAuthorities.FACTOR_AUTHORITY_PREFIX + "PASSWORD");

        when(totpService.verify("BASE32SECRET", "123456")).thenReturn(true);
        assertThatThrownBy(() -> provider.authenticate(auth("alice", "123456", "LOCAL", "TOTP")))
            .isInstanceOf(BadCredentialsException.class)
            .hasMessageContaining("必须先完成前置认证步骤");

        MultiFactorAuthenticationToken oneShot = new MultiFactorAuthenticationToken(
            "alice",
            Map.of("password", "raw", "totp", "123456"),
            MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
            MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD
        );

        Authentication fullyAuthenticated = provider.authenticate(oneShot);
        assertThat(fullyAuthenticated.isAuthenticated()).isTrue();
        assertThat(((MultiFactorAuthenticationToken) fullyAuthenticated).getCompletedFactors())
            .containsExactlyInAnyOrder(
                MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD,
                MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP
            );
        assertThat(fullyAuthenticated.getAuthorities())
            .extracting(authority -> authority.getAuthority())
            .contains(
                AuthenticationFactorAuthorities.FACTOR_AUTHORITY_PREFIX + "PASSWORD",
                AuthenticationFactorAuthorities.FACTOR_AUTHORITY_PREFIX + "TOTP"
            );
        verify(userDetailsService, times(3)).loadUserByUsername("alice");
    }

    @Test
    void should_lock_totp_after_repeated_failures() {
        UserRepository userRepository = mock(UserRepository.class);
        UserAuthenticationMethodRepository methodRepository = mock(UserAuthenticationMethodRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        TotpService totpService = mock(TotpService.class);
        SecurityService securityService = mock(SecurityService.class);
        MultiAuthenticationProvider provider = newProvider(userRepository, mock(TenantRepository.class), methodRepository, passwordEncoder,
            userDetailsService, totpService, securityService);

        TenantContext.setActiveTenantId(1L);
        User user = user(1L, "alice");
        UserAuthenticationMethod passwordMethod = method(21L, "LOCAL", "PASSWORD", Map.of("password", "{bcrypt}encoded"));
        UserAuthenticationMethod totpMethod = method(22L, "LOCAL", "TOTP", new java.util.HashMap<>(Map.of("secret", "BASE32SECRET")));
        SecurityUser securityUser = securityUser(user);
        when(authUserResolutionService.resolveUserRecordInActiveTenant("alice", 1L)).thenReturn(Optional.of(user));
        when(methodRepository.findEnabledMethodsByUserId(1L, 1L)).thenReturn(List.of(passwordMethod, totpMethod));
        when(passwordEncoder.matches("raw", "{bcrypt}encoded")).thenReturn(true);
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(securityUser);
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of("requireTotp", true));
        when(totpService.verify("BASE32SECRET", "000000")).thenReturn(false);

        MultiFactorAuthenticationToken oneShot = new MultiFactorAuthenticationToken(
                "alice",
                Map.of("password", "raw", "totp", "000000"),
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD
        );

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> provider.authenticate(oneShot))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessageContaining("TOTP 验证失败");
        }

        assertThatThrownBy(() -> provider.authenticate(oneShot))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("TOTP 验证尝试过多");
    }

    @Test
    void should_reject_manually_locked_user_before_password_verification() {
        UserRepository userRepository = mock(UserRepository.class);
        UserAuthenticationMethodRepository methodRepository = mock(UserAuthenticationMethodRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        MultiAuthenticationProvider provider = newProvider(userRepository, mock(TenantRepository.class), methodRepository, passwordEncoder,
            mock(UserDetailsService.class), mock(TotpService.class), mock(SecurityService.class));

        TenantContext.setActiveTenantId(1L);
        User user = user(1L, "alice");
        user.setAccountNonLocked(false);
        when(authUserResolutionService.resolveUserRecordInActiveTenant("alice", 1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> provider.authenticate(auth("alice", "raw", "LOCAL", "PASSWORD")))
            .isInstanceOf(LockedException.class)
            .hasMessageContaining("账号已被锁定");
        verify(passwordEncoder, times(0)).matches(any(), any());
    }

    @Test
    void should_reject_temporarily_locked_user_and_clear_expired_window_before_authentication() {
        UserRepository userRepository = mock(UserRepository.class);
        UserAuthenticationMethodRepository methodRepository = mock(UserAuthenticationMethodRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        MultiAuthenticationProvider provider = newProvider(userRepository, mock(TenantRepository.class), methodRepository, passwordEncoder,
            userDetailsService, mock(TotpService.class), mock(SecurityService.class));

        TenantContext.setActiveTenantId(1L);
        User temporarilyLocked = user(1L, "alice");
        temporarilyLocked.setFailedLoginCount(5);
        temporarilyLocked.setLastFailedLoginAt(java.time.LocalDateTime.now());
        when(authUserResolutionService.resolveUserRecordInActiveTenant("alice", 1L)).thenReturn(Optional.of(temporarilyLocked));

        assertThatThrownBy(() -> provider.authenticate(auth("alice", "raw", "LOCAL", "PASSWORD")))
            .isInstanceOf(LockedException.class)
            .hasMessageContaining("登录失败次数过多");
        verify(passwordEncoder, times(0)).matches(any(), any());

        User expiredLocked = user(1L, "bob");
        expiredLocked.setFailedLoginCount(5);
        expiredLocked.setLastFailedLoginAt(java.time.LocalDateTime.now().minusMinutes(30));
        UserAuthenticationMethod passwordMethod = method(31L, "LOCAL", "PASSWORD", Map.of("password", "{bcrypt}encoded"));
        SecurityUser securityUser = securityUser(expiredLocked);
        when(authUserResolutionService.resolveUserRecordInActiveTenant("bob", 1L)).thenReturn(Optional.of(expiredLocked));
        when(methodRepository.findEnabledMethodsByUserId(1L, 1L)).thenReturn(List.of(passwordMethod));
        when(passwordEncoder.matches("raw", "{bcrypt}encoded")).thenReturn(true);
        when(userDetailsService.loadUserByUsername("bob")).thenReturn(securityUser);

        Authentication result = provider.authenticate(auth("bob", "raw", "LOCAL", "PASSWORD"));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(expiredLocked.getFailedLoginCount()).isZero();
        verify(userRepository).save(expiredLocked);
    }

    @Test
    void should_authenticate_membership_user_via_resolution_service() {
        UserRepository userRepository = mock(UserRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        UserAuthenticationMethodRepository methodRepository = mock(UserAuthenticationMethodRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        SecurityService securityService = mock(SecurityService.class);
        AuthUserResolutionService authUserResolutionService = mock(AuthUserResolutionService.class);
        TotpService totpService = mock(TotpService.class);
        MfaProperties mfaProperties = new MfaProperties();
        TotpVerificationGuard guard = new TotpVerificationGuard(methodRepository, mfaProperties, totpService);
        com.tiny.platform.core.oauth.config.LoginProtectionProperties loginProtectionProperties =
                new com.tiny.platform.core.oauth.config.LoginProtectionProperties();
        LoginFailurePolicy loginFailurePolicy = new LoginFailurePolicy(loginProtectionProperties);
        MultiAuthenticationProvider provider = new MultiAuthenticationProvider(
                userRepository,
                tenantRepository,
                methodRepository,
                passwordEncoder,
                userDetailsService,
                securityService,
                authUserResolutionService,
                guard,
                loginFailurePolicy
        );

        TenantContext.setActiveTenantId(1L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        User user = user(3L, "shared.alice");
        user.setTenantId(99L);
        UserAuthenticationMethod passwordMethod = method(13L, "LOCAL", "PASSWORD", Map.of("password", "{bcrypt}encoded"));
        SecurityUser securityUser = new SecurityUser(user, "", 1L, Set.of());

        when(authUserResolutionService.resolveUserRecordInActiveTenant("shared.alice", 1L)).thenReturn(Optional.of(user));
        when(tenantRepository.isTenantFrozen(1L)).thenReturn(false);
        when(methodRepository.findEnabledMethodsByUserId(3L, 1L)).thenReturn(List.of(passwordMethod));
        when(passwordEncoder.matches("raw", "{bcrypt}encoded")).thenReturn(true);
        when(userDetailsService.loadUserByUsername("shared.alice")).thenReturn(securityUser);

        Authentication result = provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("shared.alice", "raw")
        );

        assertThat(result.isAuthenticated()).isTrue();
        verify(authUserResolutionService).resolveUserRecordInActiveTenant("shared.alice", 1L);
    }

    private MultiAuthenticationProvider newProvider() {
        return newProvider(mock(UserRepository.class), mock(TenantRepository.class), mock(UserAuthenticationMethodRepository.class), mock(PasswordEncoder.class),
            mock(UserDetailsService.class), mock(TotpService.class), mock(SecurityService.class));
    }

    private MultiAuthenticationProvider newProvider(UserRepository userRepository,
                                                    TenantRepository tenantRepository,
                                                    UserAuthenticationMethodRepository methodRepository,
                                                    PasswordEncoder passwordEncoder,
                                                    UserDetailsService userDetailsService,
                                                    TotpService totpService,
                                                    SecurityService securityService) {
        MfaProperties mfaProperties = new MfaProperties();
        TotpVerificationGuard guard = new TotpVerificationGuard(methodRepository, mfaProperties, totpService);
        com.tiny.platform.core.oauth.config.LoginProtectionProperties loginProtectionProperties =
            new com.tiny.platform.core.oauth.config.LoginProtectionProperties();
        LoginFailurePolicy loginFailurePolicy = new LoginFailurePolicy(loginProtectionProperties);
        return new MultiAuthenticationProvider(
                userRepository,
                tenantRepository,
                methodRepository,
                passwordEncoder,
                userDetailsService,
                securityService,
                authUserResolutionService,
                guard,
                loginFailurePolicy
        );
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
