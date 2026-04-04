package com.tiny.platform.core.oauth.config;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.AuthUserResolutionService;
import com.tiny.platform.core.oauth.security.PermissionVersionService;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtTokenCustomizerTest {

    private static void assertActiveTenantClaims(Map<String, Object> claims, long expectedTenantId) {
        assertThat(claims).containsEntry("activeTenantId", expectedTenantId);
    }

    @Test
    void accessToken_should_publish_active_tenant_claims() {
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);

        Role role = new Role();
        role.setCode("ROLE_ADMIN");
        role.setName("系统管理员");

        SecurityUser securityUser = new SecurityUser(user, "", 9L, Set.of(role));
        UsernamePasswordAuthenticationToken principal = UsernamePasswordAuthenticationToken.authenticated(
                "alice",
                "n/a",
                List.copyOf(securityUser.getAuthorities())
        );
        principal.setDetails(securityUser);

        RegisteredClient client = RegisteredClient.withId("rc-id")
                .clientId("vue-client")
                .clientSecret("{noop}secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:5173/callback")
                .scope("openid")
                .scope("profile")
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder().build())
                .build();

        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .id("auth-id")
                .principalName("alice")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of("openid", "profile"))
                .attribute("auth_time", Instant.ofEpochSecond(1_770_000_000L))
                .build();

        PermissionVersionService permissionVersionService = mock(PermissionVersionService.class);
        when(permissionVersionService.resolvePermissionsVersion(
            eq(7L), eq(9L), eq(TenantContextContract.SCOPE_TYPE_TENANT), eq(9L))).thenReturn("perm-v1");

        JwtEncodingContext context = JwtEncodingContext.with(
                        JwsHeader.with(SignatureAlgorithm.RS256),
                        JwtClaimsSet.builder()
                )
                .registeredClient(client)
                .principal(principal)
                .authorization(authorization)
                .authorizedScopes(Set.of("openid", "profile"))
                .tokenType(OAuth2TokenType.ACCESS_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .build();

        new JwtTokenCustomizer(mock(UserRepository.class), permissionVersionService).customize(context);

        Map<String, Object> claims = context.getClaims().build().getClaims();
        assertActiveTenantClaims(claims, 9L);
        assertThat(claims).containsEntry("permissionsVersion", "perm-v1");
        assertThat(claims).containsKey("authorities");
        assertThat(claims).containsKey("permissions");
        assertThat(claims.get("authorities")).asInstanceOf(collection(String.class))
                .containsExactly("ROLE_ADMIN");
        assertThat(claims.get("permissions")).asInstanceOf(collection(String.class)).isEmpty();
    }

    @Test
    void accessToken_should_use_security_user_authorities_when_outer_authentication_has_none() {
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);

        SecurityUser securityUser = new SecurityUser(
            user,
            "",
            9L,
            List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"), new SimpleGrantedAuthority("scheduling:*")),
            "perm-v1");

        UsernamePasswordAuthenticationToken principal = UsernamePasswordAuthenticationToken.authenticated(
            "alice",
            "n/a",
            List.of());
        principal.setDetails(securityUser);

        RegisteredClient client = RegisteredClient.withId("rc-id")
            .clientId("vue-client")
            .clientSecret("{noop}secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost:5173/callback")
            .scope("openid")
            .scope("profile")
            .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
            .tokenSettings(TokenSettings.builder().build())
            .build();

        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
            .id("auth-id")
            .principalName("alice")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizedScopes(Set.of("openid", "profile"))
            .attribute("auth_time", Instant.ofEpochSecond(1_770_000_000L))
            .build();

        PermissionVersionService permissionVersionService = mock(PermissionVersionService.class);
        when(permissionVersionService.resolvePermissionsVersion(
            eq(7L), eq(9L), eq(TenantContextContract.SCOPE_TYPE_TENANT), eq(9L))).thenReturn("perm-v1");

        JwtEncodingContext context = JwtEncodingContext.with(
                JwsHeader.with(SignatureAlgorithm.RS256),
                JwtClaimsSet.builder()
            )
            .registeredClient(client)
            .principal(principal)
            .authorization(authorization)
            .authorizedScopes(Set.of("openid", "profile"))
            .tokenType(OAuth2TokenType.ACCESS_TOKEN)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .build();

        new JwtTokenCustomizer(mock(UserRepository.class), permissionVersionService).customize(context);

        Map<String, Object> claims = context.getClaims().build().getClaims();
        assertThat(claims.get("authorities")).asInstanceOf(collection(String.class))
            .containsExactlyInAnyOrder("ROLE_TENANT_ADMIN", "scheduling:*");
        assertThat(claims.get("permissions")).asInstanceOf(collection(String.class)).containsExactly("scheduling:*");
    }

    @Test
    void accessToken_should_prefer_tenant_reload_over_partial_permission_style_snapshot() {
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);

        // 模拟「快照里已有带冒号的平台/组织码但缺 scheduling」——旧逻辑会提前返回导致 JWT 缺调度权限。
        SecurityUser securityUser = new SecurityUser(
            user,
            "",
            9L,
            List.of(
                new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"),
                new SimpleGrantedAuthority("system:org:list")),
            "perm-v1");

        UsernamePasswordAuthenticationToken principal = UsernamePasswordAuthenticationToken.authenticated(
            "alice",
            "n/a",
            List.of());
        principal.setDetails(securityUser);

        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(
            org.springframework.security.core.userdetails.User.withUsername("alice")
                .password("")
                .authorities(
                    new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"),
                    new SimpleGrantedAuthority("system:org:list"),
                    new SimpleGrantedAuthority("scheduling:*"))
                .build());

        RegisteredClient client = RegisteredClient.withId("rc-id")
            .clientId("vue-client")
            .clientSecret("{noop}secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost:5173/callback")
            .scope("openid")
            .scope("profile")
            .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
            .tokenSettings(TokenSettings.builder().build())
            .build();

        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
            .id("auth-id")
            .principalName("alice")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizedScopes(Set.of("openid", "profile"))
            .attribute("auth_time", Instant.ofEpochSecond(1_770_000_000L))
            .build();

        PermissionVersionService permissionVersionService = mock(PermissionVersionService.class);
        when(permissionVersionService.resolvePermissionsVersion(
            eq(7L), eq(9L), eq(TenantContextContract.SCOPE_TYPE_TENANT), eq(9L))).thenReturn("perm-v1");

        JwtEncodingContext context = JwtEncodingContext.with(
                JwsHeader.with(SignatureAlgorithm.RS256),
                JwtClaimsSet.builder()
            )
            .registeredClient(client)
            .principal(principal)
            .authorization(authorization)
            .authorizedScopes(Set.of("openid", "profile"))
            .tokenType(OAuth2TokenType.ACCESS_TOKEN)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .build();

        new JwtTokenCustomizer(mock(UserRepository.class), null, permissionVersionService, userDetailsService).customize(context);

        Map<String, Object> claims = context.getClaims().build().getClaims();
        assertThat(claims.get("authorities")).asInstanceOf(collection(String.class))
            .containsExactlyInAnyOrder("ROLE_TENANT_ADMIN", "system:org:list", "scheduling:*");
        assertThat(claims.get("permissions")).asInstanceOf(collection(String.class))
            .containsExactlyInAnyOrder("system:org:list", "scheduling:*");
        verify(userDetailsService).loadUserByUsername("alice");
    }

    /**
     * 平台态（无 activeTenantId）：不得走「租户优先重载」首段；快照已含规范权限码时不必触碰 UserDetailsService。
     * Cross-tenant / Nightly 全链路仍建议由 Playwright real-link 覆盖。
     */
    @Test
    void accessToken_platform_scoped_complete_snapshot_does_not_invoke_user_details_service() {
        User user = new User();
        user.setId(7L);
        user.setUsername("platform_alice");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);

        SecurityUser securityUser = new SecurityUser(
            user,
            "",
            null,
            List.of(
                new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"),
                new SimpleGrantedAuthority("system:tenant:list")),
            "perm-plat");

        UsernamePasswordAuthenticationToken principal = UsernamePasswordAuthenticationToken.authenticated(
            "platform_alice",
            "n/a",
            List.of());
        principal.setDetails(securityUser);

        UserDetailsService userDetailsService = mock(UserDetailsService.class);

        RegisteredClient client = RegisteredClient.withId("rc-id")
            .clientId("vue-client")
            .clientSecret("{noop}secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost:5173/callback")
            .scope("openid")
            .scope("profile")
            .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
            .tokenSettings(TokenSettings.builder().build())
            .build();

        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
            .id("auth-id")
            .principalName("platform_alice")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizedScopes(Set.of("openid", "profile"))
            .attribute("auth_time", Instant.ofEpochSecond(1_770_000_000L))
            .build();

        PermissionVersionService permissionVersionService = mock(PermissionVersionService.class);
        when(permissionVersionService.resolvePermissionsVersion(
            eq(7L),
            eq(null),
            eq(TenantContextContract.SCOPE_TYPE_PLATFORM),
            eq(null))).thenReturn("perm-plat-v1");

        JwtEncodingContext context = JwtEncodingContext.with(
                JwsHeader.with(SignatureAlgorithm.RS256),
                JwtClaimsSet.builder()
            )
            .registeredClient(client)
            .principal(principal)
            .authorization(authorization)
            .authorizedScopes(Set.of("openid", "profile"))
            .tokenType(OAuth2TokenType.ACCESS_TOKEN)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .build();

        new JwtTokenCustomizer(mock(UserRepository.class), null, permissionVersionService, userDetailsService).customize(context);

        Map<String, Object> claims = context.getClaims().build().getClaims();
        assertThat(claims.get("authorities")).asInstanceOf(collection(String.class))
            .containsExactlyInAnyOrder("ROLE_PLATFORM_ADMIN", "system:tenant:list");
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    void accessToken_tenant_reload_failure_falls_back_to_partial_permission_snapshot() {
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);

        SecurityUser securityUser = new SecurityUser(
            user,
            "",
            9L,
            List.of(
                new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"),
                new SimpleGrantedAuthority("system:org:list")),
            "perm-v1");

        UsernamePasswordAuthenticationToken principal = UsernamePasswordAuthenticationToken.authenticated(
            "alice",
            "n/a",
            List.of());
        principal.setDetails(securityUser);

        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(userDetailsService.loadUserByUsername("alice")).thenThrow(new UsernameNotFoundException("reload failed"));

        RegisteredClient client = RegisteredClient.withId("rc-id")
            .clientId("vue-client")
            .clientSecret("{noop}secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost:5173/callback")
            .scope("openid")
            .scope("profile")
            .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
            .tokenSettings(TokenSettings.builder().build())
            .build();

        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
            .id("auth-id")
            .principalName("alice")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizedScopes(Set.of("openid", "profile"))
            .attribute("auth_time", Instant.ofEpochSecond(1_770_000_000L))
            .build();

        PermissionVersionService permissionVersionService = mock(PermissionVersionService.class);
        when(permissionVersionService.resolvePermissionsVersion(
            eq(7L), eq(9L), eq(TenantContextContract.SCOPE_TYPE_TENANT), eq(9L))).thenReturn("perm-v1");

        JwtEncodingContext context = JwtEncodingContext.with(
                JwsHeader.with(SignatureAlgorithm.RS256),
                JwtClaimsSet.builder()
            )
            .registeredClient(client)
            .principal(principal)
            .authorization(authorization)
            .authorizedScopes(Set.of("openid", "profile"))
            .tokenType(OAuth2TokenType.ACCESS_TOKEN)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .build();

        new JwtTokenCustomizer(mock(UserRepository.class), null, permissionVersionService, userDetailsService).customize(context);

        Map<String, Object> claims = context.getClaims().build().getClaims();
        assertThat(claims.get("authorities")).asInstanceOf(collection(String.class))
            .containsExactlyInAnyOrder("ROLE_TENANT_ADMIN", "system:org:list");
        verify(userDetailsService).loadUserByUsername("alice");
    }

    @Test
    void accessToken_tenant_priority_reload_sets_tenant_context_from_security_user_active_tenant() {
        User user = new User();
        user.setId(7L);
        user.setUsername("bob");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);

        long tenantB = 77L;
        SecurityUser securityUser = new SecurityUser(
            user,
            "",
            tenantB,
            List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")),
            "perm-b");

        UsernamePasswordAuthenticationToken principal = UsernamePasswordAuthenticationToken.authenticated(
            "bob",
            "n/a",
            List.of());
        principal.setDetails(securityUser);

        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        doAnswer(invocation -> {
            assertThat(TenantContext.getActiveTenantId()).isEqualTo(tenantB);
            assertThat(TenantContext.getActiveScopeType()).isEqualToIgnoringCase(TenantContextContract.SCOPE_TYPE_TENANT);
            assertThat(TenantContext.getActiveScopeId()).isEqualTo(tenantB);
            return org.springframework.security.core.userdetails.User.withUsername("bob")
                .password("")
                .authorities(
                    new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"),
                    new SimpleGrantedAuthority("scheduling:*"))
                .build();
        }).when(userDetailsService).loadUserByUsername("bob");

        RegisteredClient client = RegisteredClient.withId("rc-id")
            .clientId("vue-client")
            .clientSecret("{noop}secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost:5173/callback")
            .scope("openid")
            .scope("profile")
            .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
            .tokenSettings(TokenSettings.builder().build())
            .build();

        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
            .id("auth-id")
            .principalName("bob")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizedScopes(Set.of("openid", "profile"))
            .attribute("auth_time", Instant.ofEpochSecond(1_770_000_000L))
            .build();

        PermissionVersionService permissionVersionService = mock(PermissionVersionService.class);
        when(permissionVersionService.resolvePermissionsVersion(
            eq(7L), eq(tenantB), eq(TenantContextContract.SCOPE_TYPE_TENANT), eq(tenantB))).thenReturn("perm-b-v1");

        JwtEncodingContext context = JwtEncodingContext.with(
                JwsHeader.with(SignatureAlgorithm.RS256),
                JwtClaimsSet.builder()
            )
            .registeredClient(client)
            .principal(principal)
            .authorization(authorization)
            .authorizedScopes(Set.of("openid", "profile"))
            .tokenType(OAuth2TokenType.ACCESS_TOKEN)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .build();

        new JwtTokenCustomizer(mock(UserRepository.class), null, permissionVersionService, userDetailsService).customize(context);

        Map<String, Object> claims = context.getClaims().build().getClaims();
        assertThat(claims).containsEntry("activeTenantId", tenantB);
        assertThat(claims.get("authorities")).asInstanceOf(collection(String.class)).contains("scheduling:*");
        verify(userDetailsService).loadUserByUsername("bob");
    }

    @Test
    void accessToken_should_reload_when_security_user_has_only_role_codes_without_permission_snapshot() {
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);

        SecurityUser securityUser = new SecurityUser(
            user,
            "",
            9L,
            List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")),
            "perm-v1");

        UsernamePasswordAuthenticationToken principal = UsernamePasswordAuthenticationToken.authenticated(
            "alice",
            "n/a",
            List.of());
        principal.setDetails(securityUser);

        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(
            org.springframework.security.core.userdetails.User.withUsername("alice")
                .password("")
                .authorities(
                    new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"),
                    new SimpleGrantedAuthority("scheduling:console:config"))
                .build());

        RegisteredClient client = RegisteredClient.withId("rc-id")
            .clientId("vue-client")
            .clientSecret("{noop}secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost:5173/callback")
            .scope("openid")
            .scope("profile")
            .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
            .tokenSettings(TokenSettings.builder().build())
            .build();

        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
            .id("auth-id")
            .principalName("alice")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizedScopes(Set.of("openid", "profile"))
            .attribute("auth_time", Instant.ofEpochSecond(1_770_000_000L))
            .build();

        PermissionVersionService permissionVersionService = mock(PermissionVersionService.class);
        when(permissionVersionService.resolvePermissionsVersion(
            eq(7L), eq(9L), eq(TenantContextContract.SCOPE_TYPE_TENANT), eq(9L))).thenReturn("perm-v1");

        JwtEncodingContext context = JwtEncodingContext.with(
                JwsHeader.with(SignatureAlgorithm.RS256),
                JwtClaimsSet.builder()
            )
            .registeredClient(client)
            .principal(principal)
            .authorization(authorization)
            .authorizedScopes(Set.of("openid", "profile"))
            .tokenType(OAuth2TokenType.ACCESS_TOKEN)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .build();

        new JwtTokenCustomizer(mock(UserRepository.class), null, permissionVersionService, userDetailsService).customize(context);

        Map<String, Object> claims = context.getClaims().build().getClaims();
        assertThat(claims.get("authorities")).asInstanceOf(collection(String.class))
            .containsExactlyInAnyOrder("ROLE_TENANT_ADMIN", "scheduling:console:config");
        assertThat(claims.get("permissions")).asInstanceOf(collection(String.class)).containsExactly("scheduling:console:config");
        verify(userDetailsService).loadUserByUsername("alice");
    }

    @Test
    void accessToken_should_reload_authorities_via_user_details_when_security_user_has_none() {
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);

        SecurityUser securityUser = new SecurityUser(user, "", 9L, List.of(), "perm-v1");

        UsernamePasswordAuthenticationToken principal = UsernamePasswordAuthenticationToken.authenticated(
            "alice",
            "n/a",
            List.of());
        principal.setDetails(securityUser);

        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(
            org.springframework.security.core.userdetails.User.withUsername("alice")
                .password("")
                .authorities(List.of(new SimpleGrantedAuthority("scheduling:*")))
                .build());

        RegisteredClient client = RegisteredClient.withId("rc-id")
            .clientId("vue-client")
            .clientSecret("{noop}secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost:5173/callback")
            .scope("openid")
            .scope("profile")
            .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
            .tokenSettings(TokenSettings.builder().build())
            .build();

        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
            .id("auth-id")
            .principalName("alice")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizedScopes(Set.of("openid", "profile"))
            .attribute("auth_time", Instant.ofEpochSecond(1_770_000_000L))
            .build();

        PermissionVersionService permissionVersionService = mock(PermissionVersionService.class);
        when(permissionVersionService.resolvePermissionsVersion(
            eq(7L), eq(9L), eq(TenantContextContract.SCOPE_TYPE_TENANT), eq(9L))).thenReturn("perm-v1");

        JwtEncodingContext context = JwtEncodingContext.with(
                JwsHeader.with(SignatureAlgorithm.RS256),
                JwtClaimsSet.builder()
            )
            .registeredClient(client)
            .principal(principal)
            .authorization(authorization)
            .authorizedScopes(Set.of("openid", "profile"))
            .tokenType(OAuth2TokenType.ACCESS_TOKEN)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .build();

        new JwtTokenCustomizer(mock(UserRepository.class), null, permissionVersionService, userDetailsService).customize(context);

        Map<String, Object> claims = context.getClaims().build().getClaims();
        assertThat(claims.get("authorities")).asInstanceOf(collection(String.class)).containsExactly("scheduling:*");
        assertThat(claims.get("permissions")).asInstanceOf(collection(String.class)).containsExactly("scheduling:*");
        verify(userDetailsService).loadUserByUsername("alice");
    }

    @Test
    void accessToken_userDetails_reload_populates_tenant_context_for_default_issuer_token_endpoint() {
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);

        SecurityUser securityUser = new SecurityUser(user, "", 42L, List.of(), "perm-v1");

        UsernamePasswordAuthenticationToken principal = UsernamePasswordAuthenticationToken.authenticated(
            "alice",
            "n/a",
            List.of());
        principal.setDetails(securityUser);

        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        doAnswer(invocation -> {
            assertThat(TenantContext.getActiveTenantId()).isEqualTo(42L);
            assertThat(TenantContext.getActiveScopeType()).isEqualToIgnoringCase(TenantContextContract.SCOPE_TYPE_TENANT);
            assertThat(TenantContext.getActiveScopeId()).isEqualTo(42L);
            return org.springframework.security.core.userdetails.User.withUsername("alice")
                .password("")
                .authorities(List.of(new SimpleGrantedAuthority("scheduling:*")))
                .build();
        }).when(userDetailsService).loadUserByUsername("alice");

        RegisteredClient client = RegisteredClient.withId("rc-id")
            .clientId("vue-client")
            .clientSecret("{noop}secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost:5173/callback")
            .scope("openid")
            .scope("profile")
            .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
            .tokenSettings(TokenSettings.builder().build())
            .build();

        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
            .id("auth-id")
            .principalName("alice")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizedScopes(Set.of("openid", "profile"))
            .attribute("auth_time", Instant.ofEpochSecond(1_770_000_000L))
            .build();

        PermissionVersionService permissionVersionService = mock(PermissionVersionService.class);
        when(permissionVersionService.resolvePermissionsVersion(
            eq(7L), eq(42L), eq(TenantContextContract.SCOPE_TYPE_TENANT), eq(42L))).thenReturn("perm-v1");

        JwtEncodingContext context = JwtEncodingContext.with(
                JwsHeader.with(SignatureAlgorithm.RS256),
                JwtClaimsSet.builder()
            )
            .registeredClient(client)
            .principal(principal)
            .authorization(authorization)
            .authorizedScopes(Set.of("openid", "profile"))
            .tokenType(OAuth2TokenType.ACCESS_TOKEN)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .build();

        new JwtTokenCustomizer(mock(UserRepository.class), null, permissionVersionService, userDetailsService).customize(context);

        assertThat(TenantContext.getActiveTenantId()).isNull();
        assertThat(TenantContext.getActiveScopeType()).isNull();
        assertThat(TenantContext.getActiveScopeId()).isNull();
    }

    @Test
    void idToken_should_prefer_membership_aware_user_resolution_for_profile_claims() {
        User user = new User();
        user.setId(7L);
        user.setUsername("shared.alice");
        user.setNickname("Alice Shared");
        user.setEmail("alice@example.com");
        user.setPhone("13800000000");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);

        SecurityUser securityUser = new SecurityUser(user, "", 9L, Set.of());
        UsernamePasswordAuthenticationToken principal = UsernamePasswordAuthenticationToken.authenticated(
                "shared.alice",
                "n/a",
                List.copyOf(securityUser.getAuthorities())
        );
        principal.setDetails(securityUser);

        RegisteredClient client = RegisteredClient.withId("rc-id")
                .clientId("vue-client")
                .clientSecret("{noop}secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:5173/callback")
                .scope("openid")
                .scope("profile")
                .scope("email")
                .scope("phone")
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder().build())
                .build();

        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .id("auth-id")
                .principalName("shared.alice")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of("openid", "profile", "email", "phone"))
                .attribute("auth_time", Instant.ofEpochSecond(1_770_000_000L))
                .build();

        JwtEncodingContext context = JwtEncodingContext.with(
                        JwsHeader.with(SignatureAlgorithm.RS256),
                        JwtClaimsSet.builder()
                )
                .registeredClient(client)
                .principal(principal)
                .authorization(authorization)
                .authorizedScopes(Set.of("openid", "profile", "email", "phone"))
                .tokenType(new OAuth2TokenType("id_token"))
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .build();

        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        AuthUserResolutionService authUserResolutionService = mock(AuthUserResolutionService.class);
        when(authUserResolutionService.resolveUserRecordInActiveTenant("shared.alice", 9L)).thenReturn(Optional.of(user));

        new JwtTokenCustomizer(userRepository, authUserResolutionService, mock(PermissionVersionService.class))
                .customize(context);

        Map<String, Object> claims = context.getClaims().build().getClaims();
        assertActiveTenantClaims(claims, 9L);
        assertThat(claims).containsEntry("userId", 7L);
        assertThat(claims).containsEntry("username", "shared.alice");
        assertThat(claims).containsEntry("name", "Alice Shared");
        assertThat(claims).containsEntry("nickname", "Alice Shared");
        assertThat(claims).containsEntry("email", "alice@example.com");
        assertThat(claims).containsEntry("phone_number", "13800000000");
        verify(authUserResolutionService).resolveUserRecordInActiveTenant("shared.alice", 9L);
    }
}
