package com.tiny.platform.core.oauth.config;

import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationSessionManager;
import com.tiny.platform.core.oauth.security.LoginFailurePolicy;
import com.tiny.platform.core.oauth.service.AuthenticationAuditService;
import com.tiny.platform.core.oauth.service.SecurityService;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfigurationSource;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CoreOauthConfigPojoAndFactoryCoverageTest {

    @Test
    void shouldCoverClientPropertiesGettersSettersAndDefaults() {
        ClientProperties properties = new ClientProperties();

        ClientProperties.Jwt jwt = new ClientProperties.Jwt();
        jwt.setPublicKeyPath("classpath:pub.pem");
        jwt.setPrivateKeyPath("classpath:pri.pem");
        properties.setJwt(jwt);

        ClientProperties.Client client = new ClientProperties.Client();
        client.setClientId("web");
        client.setClientSecret("secret");
        client.setAuthenticationMethods(List.of("client_secret_basic"));
        client.setGrantTypes(List.of("authorization_code", "refresh_token"));
        client.setRedirectUris(List.of("http://localhost/cb"));
        client.setPostLogoutRedirectUris(List.of("http://localhost/logout-cb"));
        client.setScopes(List.of("openid", "profile"));

        ClientProperties.Client.ClientSetting clientSetting = new ClientProperties.Client.ClientSetting();
        assertThat(clientSetting.isRequireAuthorizationConsent()).isTrue();
        assertThat(clientSetting.isRequireProofKey()).isFalse();
        clientSetting.setRequireAuthorizationConsent(false);
        clientSetting.setRequireProofKey(true);
        client.setClientSetting(clientSetting);

        ClientProperties.Client.TokenSetting tokenSetting = new ClientProperties.Client.TokenSetting();
        assertThat(tokenSetting.getAccessTokenTimeToLive()).isEqualTo(Duration.ofMinutes(3));
        assertThat(tokenSetting.getRefreshTokenTimeToLive()).isEqualTo(Duration.ofHours(8));
        assertThat(tokenSetting.isReuseRefreshTokens()).isTrue();
        assertThat(tokenSetting.getAccessTokenFormat()).isEqualTo("self-contained");
        tokenSetting.setAccessTokenTimeToLive(Duration.ofMinutes(10));
        tokenSetting.setRefreshTokenTimeToLive(Duration.ofDays(1));
        tokenSetting.setReuseRefreshTokens(false);
        tokenSetting.setAccessTokenFormat("reference");
        client.setTokenSetting(tokenSetting);

        properties.setClients(List.of(client));

        assertThat(properties.getJwt().getPublicKeyPath()).isEqualTo("classpath:pub.pem");
        assertThat(properties.getJwt().getPrivateKeyPath()).isEqualTo("classpath:pri.pem");
        assertThat(properties.getClients()).hasSize(1);
        assertThat(properties.getClients().getFirst().getClientId()).isEqualTo("web");
        assertThat(properties.getClients().getFirst().getClientSecret()).isEqualTo("secret");
        assertThat(properties.getClients().getFirst().getAuthenticationMethods()).containsExactly("client_secret_basic");
        assertThat(properties.getClients().getFirst().getGrantTypes()).containsExactly("authorization_code", "refresh_token");
        assertThat(properties.getClients().getFirst().getRedirectUris()).containsExactly("http://localhost/cb");
        assertThat(properties.getClients().getFirst().getPostLogoutRedirectUris()).containsExactly("http://localhost/logout-cb");
        assertThat(properties.getClients().getFirst().getScopes()).containsExactly("openid", "profile");
        assertThat(properties.getClients().getFirst().getClientSetting().isRequireAuthorizationConsent()).isFalse();
        assertThat(properties.getClients().getFirst().getClientSetting().isRequireProofKey()).isTrue();
        assertThat(properties.getClients().getFirst().getTokenSetting().getAccessTokenTimeToLive()).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.getClients().getFirst().getTokenSetting().getRefreshTokenTimeToLive()).isEqualTo(Duration.ofDays(1));
        assertThat(properties.getClients().getFirst().getTokenSetting().isReuseRefreshTokens()).isFalse();
        assertThat(properties.getClients().getFirst().getTokenSetting().getAccessTokenFormat()).isEqualTo("reference");
    }

    @Test
    void shouldInstantiateCamundaConfigAndDefaultSecurityBeanFactories() {
        CamundaConfig camundaConfig = new CamundaConfig();
        assertThat(camundaConfig).isNotNull();

        CorsConfigurationSource corsSource = mock(CorsConfigurationSource.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        DefaultSecurityConfig securityConfig = new DefaultSecurityConfig(corsSource, userDetailsService);

        PasswordEncoder passwordEncoder = securityConfig.passwordEncoder();
        String encoded = passwordEncoder.encode("p@ss");
        assertThat(passwordEncoder.matches("p@ss", encoded)).isTrue();

        assertThat(securityConfig.customAuthenticationDetailsSource())
                .isInstanceOf(CustomWebAuthenticationDetailsSource.class)
                .isInstanceOf(org.springframework.security.authentication.AuthenticationDetailsSource.class);

        SecurityContextRepository repository = securityConfig.securityContextRepository();
        assertThat(repository).isInstanceOf(HttpSessionSecurityContextRepository.class);

        TenantRepository tenantRepository = mock(TenantRepository.class);
        assertThat(securityConfig.tenantContextFilter(tenantRepository)).isNotNull();

        UserRepository userRepository = mock(UserRepository.class);
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        LoginFailurePolicy loginFailurePolicy = new LoginFailurePolicy(new LoginProtectionProperties());
        assertThat(securityConfig.customLoginFailureHandler(userRepository, auditService, loginFailurePolicy))
                .isInstanceOf(CustomLoginFailureHandler.class);

        SecurityService securityService = mock(SecurityService.class);
        FrontendProperties frontendProperties = new FrontendProperties();
        frontendProperties.setTotpBindUrl("/totp-bind");
        frontendProperties.setTotpVerifyUrl("/totp-verify");
        MultiFactorAuthenticationSessionManager sessionManager = mock(MultiFactorAuthenticationSessionManager.class);
        assertThat(securityConfig.customLoginSuccessHandler(
                securityService, userRepository, frontendProperties, sessionManager, auditService))
                .isInstanceOf(CustomLoginSuccessHandler.class);
    }
}
