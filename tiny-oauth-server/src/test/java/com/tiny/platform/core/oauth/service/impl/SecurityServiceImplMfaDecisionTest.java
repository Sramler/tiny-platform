package com.tiny.platform.core.oauth.service.impl;

import com.tiny.platform.core.oauth.config.MfaProperties;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.security.TotpService;
import com.tiny.platform.core.oauth.security.TotpVerificationGuard;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationMethodRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityServiceImplMfaDecisionTest {

    private static void assertActiveTenantStatus(Map<String, Object> status, long expectedTenantId) {
        assertThat(status.get("activeTenantId")).isEqualTo(expectedTenantId);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void modeNoneShouldNeverRequireTotp() {
        SecurityServiceImpl service = createService("NONE", true, true);
        TenantContext.setActiveTenantId(1L);
        Map<String, Object> status = service.getSecurityStatus(mockUser());

        assertThat(status.get("requireTotp")).isEqualTo(false);
    }

    @Test
    void modeOptionalShouldRequireTotpOnlyWhenBoundAndActivated() {
        SecurityServiceImpl boundActivatedService = createService("OPTIONAL", true, true);
        SecurityServiceImpl boundNotActivatedService = createService("OPTIONAL", true, false);
        SecurityServiceImpl unboundService = createService("OPTIONAL", false, false);

        TenantContext.setActiveTenantId(1L);
        assertThat(boundActivatedService.getSecurityStatus(mockUser()).get("requireTotp")).isEqualTo(true);
        assertThat(boundNotActivatedService.getSecurityStatus(mockUser()).get("requireTotp")).isEqualTo(false);
        assertThat(unboundService.getSecurityStatus(mockUser()).get("requireTotp")).isEqualTo(false);
    }

    @Test
    void modeRequiredShouldRequireTotpOnlyAfterBindingAndActivation() {
        SecurityServiceImpl boundActivatedService = createService("REQUIRED", true, true);
        SecurityServiceImpl unboundService = createService("REQUIRED", false, false);

        TenantContext.setActiveTenantId(1L);
        assertThat(boundActivatedService.getSecurityStatus(mockUser()).get("requireTotp")).isEqualTo(true);
        assertThat(unboundService.getSecurityStatus(mockUser()).get("requireTotp")).isEqualTo(false);
    }

    @Test
    void should_prefer_active_tenant_context_for_membership_user_status_lookup() {
        UserAuthenticationMethodRepository repository = mock(UserAuthenticationMethodRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TotpService totpService = mock(TotpService.class);
        MfaProperties mfaProperties = new MfaProperties();
        mfaProperties.setMode("OPTIONAL");

        when(repository.existsByUserIdAndTenantIdAndAuthenticationProviderAndAuthenticationType(
                1L, 9L, "LOCAL", "TOTP"
        )).thenReturn(true);

        UserAuthenticationMethod method = new UserAuthenticationMethod();
        method.setAuthenticationConfiguration(Map.of("activated", true));
        when(repository.findByUserIdAndTenantIdAndAuthenticationProviderAndAuthenticationType(
                1L, 9L, "LOCAL", "TOTP"
        )).thenReturn(Optional.of(method));

        TotpVerificationGuard guard = new TotpVerificationGuard(repository, mfaProperties, totpService);
        SecurityServiceImpl service = new SecurityServiceImpl(repository, passwordEncoder, mfaProperties, guard);

        User user = mockUser();
        user.setTenantId(1L);
        TenantContext.setActiveTenantId(9L);

        Map<String, Object> status = service.getSecurityStatus(user);

        assertActiveTenantStatus(status, 9L);
        assertThat(status.get("totpBound")).isEqualTo(true);
        assertThat(status.get("requireTotp")).isEqualTo(true);
    }

    @Test
    void should_fallback_to_authentication_active_tenant_when_context_missing() {
        UserAuthenticationMethodRepository repository = mock(UserAuthenticationMethodRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TotpService totpService = mock(TotpService.class);
        MfaProperties mfaProperties = new MfaProperties();
        mfaProperties.setMode("OPTIONAL");

        when(repository.existsByUserIdAndTenantIdAndAuthenticationProviderAndAuthenticationType(
                1L, 9L, "LOCAL", "TOTP"
        )).thenReturn(true);

        UserAuthenticationMethod method = new UserAuthenticationMethod();
        method.setAuthenticationConfiguration(Map.of("activated", true));
        when(repository.findByUserIdAndTenantIdAndAuthenticationProviderAndAuthenticationType(
                1L, 9L, "LOCAL", "TOTP"
        )).thenReturn(Optional.of(method));

        TotpVerificationGuard guard = new TotpVerificationGuard(repository, mfaProperties, totpService);
        SecurityServiceImpl service = new SecurityServiceImpl(repository, passwordEncoder, mfaProperties, guard);

        User user = mockUser();
        user.setTenantId(1L);
        SecurityUser securityUser = new SecurityUser(user, "", 9L, Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities())
        );

        Map<String, Object> status = service.getSecurityStatus(user);

        assertActiveTenantStatus(status, 9L);
        assertThat(status.get("totpBound")).isEqualTo(true);
        assertThat(status.get("requireTotp")).isEqualTo(true);
    }

    private SecurityServiceImpl createService(String mode, boolean totpBound, boolean totpActivated) {
        UserAuthenticationMethodRepository repository = mock(UserAuthenticationMethodRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TotpService totpService = mock(TotpService.class);
        MfaProperties mfaProperties = new MfaProperties();
        mfaProperties.setMode(mode);

        when(repository.existsByUserIdAndTenantIdAndAuthenticationProviderAndAuthenticationType(
                1L, 1L, "LOCAL", "TOTP"
        )).thenReturn(totpBound);

        if (totpBound) {
            UserAuthenticationMethod method = new UserAuthenticationMethod();
            method.setAuthenticationConfiguration(Map.of("activated", totpActivated));
            when(repository.findByUserIdAndTenantIdAndAuthenticationProviderAndAuthenticationType(
                    1L, 1L, "LOCAL", "TOTP"
            )).thenReturn(Optional.of(method));
        }

        TotpVerificationGuard guard = new TotpVerificationGuard(repository, mfaProperties, totpService);
        return new SecurityServiceImpl(repository, passwordEncoder, mfaProperties, guard);
    }

    private User mockUser() {
        User user = new User();
        user.setId(1L);
        user.setTenantId(1L);
        user.setUsername("admin");
        return user;
    }
}
