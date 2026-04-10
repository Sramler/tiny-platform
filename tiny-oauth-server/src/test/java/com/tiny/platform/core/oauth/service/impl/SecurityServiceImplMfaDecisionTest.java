package com.tiny.platform.core.oauth.service.impl;

import com.tiny.platform.core.oauth.config.MfaProperties;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.security.TotpService;
import com.tiny.platform.core.oauth.security.TotpVerificationGuard;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthCredential;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthScopePolicy;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthScopePolicyRepository;
import com.tiny.platform.infrastructure.auth.user.service.UserAuthenticationBridgeWriter;
import com.tiny.platform.infrastructure.auth.user.service.UserAuthenticationMethodProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
        UserAuthScopePolicyRepository scopeRepo = mock(UserAuthScopePolicyRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TotpService totpService = mock(TotpService.class);
        MfaProperties mfaProperties = new MfaProperties();
        mfaProperties.setMode("OPTIONAL");
        UserAuthenticationMethodProfileService profileService = profileService(scopeRepo);

        UserAuthScopePolicy policy = totpScopePolicy(9L, true);
        when(scopeRepo.findByUserIdAndAuthenticationProviderAndAuthenticationTypeAndScopeKey(
                1L, "LOCAL", "TOTP", "TENANT:9"
        )).thenReturn(Optional.of(policy));

        TotpVerificationGuard guard = new TotpVerificationGuard(mock(UserAuthenticationBridgeWriter.class), mfaProperties, totpService);
        SecurityServiceImpl service = new SecurityServiceImpl(profileService, passwordEncoder, mfaProperties, guard,
                mock(UserAuthenticationBridgeWriter.class));

        User user = mockUser();
        TenantContext.setActiveTenantId(9L);

        Map<String, Object> status = service.getSecurityStatus(user);

        assertActiveTenantStatus(status, 9L);
        assertThat(status.get("totpBound")).isEqualTo(true);
        assertThat(status.get("requireTotp")).isEqualTo(true);
    }

    @Test
    void should_fallback_to_authentication_active_tenant_when_context_missing() {
        UserAuthScopePolicyRepository scopeRepo = mock(UserAuthScopePolicyRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TotpService totpService = mock(TotpService.class);
        MfaProperties mfaProperties = new MfaProperties();
        mfaProperties.setMode("OPTIONAL");
        UserAuthenticationMethodProfileService profileService = profileService(scopeRepo);

        UserAuthScopePolicy policy = totpScopePolicy(9L, true);
        when(scopeRepo.findByUserIdAndAuthenticationProviderAndAuthenticationTypeAndScopeKey(
                1L, "LOCAL", "TOTP", "TENANT:9"
        )).thenReturn(Optional.of(policy));

        TotpVerificationGuard guard = new TotpVerificationGuard(mock(UserAuthenticationBridgeWriter.class), mfaProperties, totpService);
        SecurityServiceImpl service = new SecurityServiceImpl(profileService, passwordEncoder, mfaProperties, guard,
                mock(UserAuthenticationBridgeWriter.class));

        User user = mockUser();
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
        UserAuthScopePolicyRepository scopeRepo = mock(UserAuthScopePolicyRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TotpService totpService = mock(TotpService.class);
        MfaProperties mfaProperties = new MfaProperties();
        mfaProperties.setMode(mode);

        UserAuthenticationMethodProfileService profileService = profileService(scopeRepo);
        when(scopeRepo.findByUserIdAndAuthenticationProviderAndAuthenticationTypeAndScopeKey(
                eq(1L), eq("LOCAL"), eq("TOTP"), eq("TENANT:1")
        )).thenReturn(totpBound ? Optional.of(totpScopePolicy(1L, totpActivated)) : Optional.empty());

        TotpVerificationGuard guard = new TotpVerificationGuard(mock(UserAuthenticationBridgeWriter.class), mfaProperties, totpService);
        return new SecurityServiceImpl(profileService, passwordEncoder, mfaProperties, guard,
                mock(UserAuthenticationBridgeWriter.class));
    }

    private UserAuthenticationMethodProfileService profileService(UserAuthScopePolicyRepository scopeRepo) {
        lenient().when(scopeRepo.findByUserIdAndAuthenticationProviderAndAuthenticationTypeAndScopeKey(
                anyLong(), anyString(), anyString(), anyString()
        )).thenReturn(Optional.empty());
        lenient().when(scopeRepo.findByUserIdAndScopeKey(anyLong(), anyString())).thenReturn(java.util.List.of());
        return new UserAuthenticationMethodProfileService(scopeRepo);
    }

    private static UserAuthScopePolicy totpScopePolicy(long tenantId, boolean activated) {
        UserAuthCredential credential = new UserAuthCredential();
        credential.setUserId(1L);
        credential.setAuthenticationProvider("LOCAL");
        credential.setAuthenticationType("TOTP");
        credential.setAuthenticationConfiguration(Map.of("activated", activated));

        UserAuthScopePolicy scopePolicy = new UserAuthScopePolicy();
        scopePolicy.setCredential(credential);
        scopePolicy.setScopeType("TENANT");
        scopePolicy.setScopeId(tenantId);
        scopePolicy.setScopeKey("TENANT:" + tenantId);
        scopePolicy.setIsMethodEnabled(true);
        scopePolicy.setIsPrimaryMethod(false);
        scopePolicy.setAuthenticationPriority(1);
        return scopePolicy;
    }

    private User mockUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        return user;
    }
}
