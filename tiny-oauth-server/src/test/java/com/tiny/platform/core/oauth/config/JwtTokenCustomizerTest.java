package com.tiny.platform.core.oauth.config;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.AuthUserResolutionService;
import com.tiny.platform.core.oauth.security.PermissionVersionService;
import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
import static org.mockito.Mockito.mock;
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
        user.setTenantId(9L);
        user.setUsername("alice");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);

        Resource dictResource = new Resource();
        dictResource.setPermission("dict:type:view");

        Resource schedulingResource = new Resource();
        schedulingResource.setPermission("scheduling:console:view");

        Role role = new Role();
        role.setCode("ROLE_ADMIN");
        role.setName("系统管理员");
        role.setResources(Set.of(dictResource, schedulingResource));

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
        when(permissionVersionService.resolvePermissionsVersion(7L, 9L)).thenReturn("perm-v1");

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
        assertThat(claims.get("authorities")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.set(String.class))
                .contains("ROLE_ADMIN", "dict:type:view", "scheduling:console:view");
        assertThat(claims.get("permissions")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.set(String.class))
                .contains("dict:type:view", "scheduling:console:view")
                .doesNotContain("ROLE_ADMIN");
    }

    @Test
    void idToken_should_prefer_membership_aware_user_resolution_for_profile_claims() {
        User user = new User();
        user.setId(7L);
        user.setTenantId(9L);
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
