package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.config.CustomWebAuthenticationDetailsSource;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MultiFactorAuthenticationSessionManagerTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void fallbackPromotionShouldPreserveExistingFactorAuthoritiesAndAppendTotp() {
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        MultiFactorAuthenticationSessionManager sessionManager = new MultiFactorAuthenticationSessionManager(
                userDetailsService,
                new HttpSessionSecurityContextRepository()
        );

        SecurityUser securityUser = new SecurityUser(
                1L,
                1L,
                "admin",
                "",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
                true,
                true,
                true,
                true
        );
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(securityUser);

        UsernamePasswordAuthenticationToken currentAuth = UsernamePasswordAuthenticationToken.authenticated(
                "admin",
                "n/a",
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority(AuthenticationFactorAuthorities.FACTOR_AUTHORITY_PREFIX + "PASSWORD")
                )
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("authenticationProvider", "LOCAL");
        currentAuth.setDetails(new CustomWebAuthenticationDetailsSource().buildDetails(request));

        MockHttpServletResponse response = new MockHttpServletResponse();
        User domainUser = new User();
        domainUser.setId(1L);
        domainUser.setTenantId(1L);
        domainUser.setUsername("admin");

        sessionManager.promoteToFullyAuthenticated(
                domainUser,
                currentAuth,
                request,
                response,
                MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP
        );

        Authentication promoted = SecurityContextHolder.getContext().getAuthentication();
        assertThat(promoted).isInstanceOf(MultiFactorAuthenticationToken.class);
        MultiFactorAuthenticationToken token = (MultiFactorAuthenticationToken) promoted;
        assertThat(token.isAuthenticated()).isTrue();
        assertThat(AuthenticationFactorAuthorities.extractFactors(token)).containsExactlyInAnyOrder(
                MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD,
                MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP
        );
        assertThat(token.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .contains(
                        "ROLE_ADMIN",
                        AuthenticationFactorAuthorities.FACTOR_AUTHORITY_PREFIX + "PASSWORD",
                        AuthenticationFactorAuthorities.FACTOR_AUTHORITY_PREFIX + "TOTP"
                );
    }

    @Test
    void shouldReturnFalseAndClearContextWhenPersistingSecurityContextFails() {
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        SecurityContextRepository securityContextRepository = mock(SecurityContextRepository.class);
        MultiFactorAuthenticationSessionManager sessionManager = new MultiFactorAuthenticationSessionManager(
                userDetailsService,
                securityContextRepository
        );

        SecurityUser securityUser = new SecurityUser(
                1L,
                1L,
                "admin",
                "",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
                true,
                true,
                true,
                true
        );
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(securityUser);

        MultiFactorAuthenticationToken currentAuth = MultiFactorAuthenticationToken.partiallyAuthenticated(
                "admin",
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                java.util.Set.of(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        doThrow(new IllegalStateException("save failed"))
                .when(securityContextRepository)
                .saveContext(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(request), org.mockito.ArgumentMatchers.eq(response));

        User domainUser = new User();
        domainUser.setId(1L);
        domainUser.setTenantId(1L);
        domainUser.setUsername("admin");

        boolean promoted = sessionManager.tryPromoteToFullyAuthenticated(
                domainUser,
                currentAuth,
                request,
                response,
                MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP
        );

        assertThat(promoted).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
