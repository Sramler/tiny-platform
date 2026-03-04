package com.tiny.platform.core.oauth.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisteredClientConfigTest {

    @Test
    void shouldRegisterClientsFromPropertiesAndPreserveExistingIdAndIssuedAt() throws Exception {
        RegisteredClientConfig config = new RegisteredClientConfig();
        RegisteredClientRepository repository = mock(RegisteredClientRepository.class);
        ClientProperties properties = new ClientProperties();

        ClientProperties.Client existingClientCfg = new ClientProperties.Client();
        existingClientCfg.setClientId("existing-client");
        existingClientCfg.setClientSecret("s1");
        existingClientCfg.setAuthenticationMethods(List.of(
                ClientAuthenticationMethod.CLIENT_SECRET_BASIC.getValue(),
                ClientAuthenticationMethod.NONE.getValue()));
        existingClientCfg.setGrantTypes(List.of(
                AuthorizationGrantType.AUTHORIZATION_CODE.getValue(),
                AuthorizationGrantType.REFRESH_TOKEN.getValue()));
        existingClientCfg.setScopes(List.of("openid", "profile"));
        existingClientCfg.setRedirectUris(List.of("https://app.example.com/callback"));
        existingClientCfg.setPostLogoutRedirectUris(List.of("https://app.example.com/logout"));
        existingClientCfg.getClientSetting().setRequireAuthorizationConsent(false);
        existingClientCfg.getClientSetting().setRequireProofKey(true);
        existingClientCfg.getTokenSetting().setAccessTokenTimeToLive(Duration.ofMinutes(30));
        existingClientCfg.getTokenSetting().setRefreshTokenTimeToLive(Duration.ofHours(12));
        existingClientCfg.getTokenSetting().setReuseRefreshTokens(false);
        existingClientCfg.getTokenSetting().setAccessTokenFormat("reference");

        ClientProperties.Client newClientCfg = new ClientProperties.Client();
        newClientCfg.setClientId("new-client");
        newClientCfg.setClientSecret(null);
        newClientCfg.setAuthenticationMethods(List.of(ClientAuthenticationMethod.NONE.getValue()));
        newClientCfg.setGrantTypes(List.of(AuthorizationGrantType.CLIENT_CREDENTIALS.getValue()));
        // scope/redirect/post-logout 均留空，覆盖 null 分支

        properties.setClients(List.of(existingClientCfg, newClientCfg));

        Instant existingIssuedAt = Instant.parse("2026-01-01T00:00:00Z");
        RegisteredClient existing = RegisteredClient.withId("fixed-id")
                .clientId("existing-client")
                .clientIdIssuedAt(existingIssuedAt)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://app.example.com/callback")
                .build();

        when(repository.findByClientId("existing-client")).thenReturn(existing);
        when(repository.findByClientId("new-client")).thenReturn(null);

        CommandLineRunner runner = config.registerClients(properties, repository);
        runner.run();

        ArgumentCaptor<RegisteredClient> captor = ArgumentCaptor.forClass(RegisteredClient.class);
        verify(repository, times(2)).save(captor.capture());
        List<RegisteredClient> saved = captor.getAllValues();

        RegisteredClient savedExisting = saved.get(0);
        assertThat(savedExisting.getId()).isEqualTo("fixed-id");
        assertThat(savedExisting.getClientIdIssuedAt()).isEqualTo(existingIssuedAt);
        assertThat(savedExisting.getClientSecret()).isEqualTo("{noop}s1");
        assertThat(savedExisting.getClientAuthenticationMethods())
                .extracting(ClientAuthenticationMethod::getValue)
                .containsExactlyInAnyOrder("client_secret_basic", "none");
        assertThat(savedExisting.getAuthorizationGrantTypes())
                .extracting(AuthorizationGrantType::getValue)
                .containsExactlyInAnyOrder("authorization_code", "refresh_token");
        assertThat(savedExisting.getScopes()).containsExactlyInAnyOrder("openid", "profile");
        assertThat(savedExisting.getRedirectUris()).containsExactly("https://app.example.com/callback");
        assertThat(savedExisting.getPostLogoutRedirectUris()).containsExactly("https://app.example.com/logout");
        assertThat(savedExisting.getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(Duration.ofMinutes(30));
        assertThat(savedExisting.getTokenSettings().getRefreshTokenTimeToLive()).isEqualTo(Duration.ofHours(12));
        assertThat(savedExisting.getTokenSettings().isReuseRefreshTokens()).isFalse();
        assertThat(savedExisting.getTokenSettings().getAccessTokenFormat()).isEqualTo(OAuth2TokenFormat.REFERENCE);
        assertThat(savedExisting.getClientSettings().isRequireAuthorizationConsent()).isFalse();
        assertThat(savedExisting.getClientSettings().isRequireProofKey()).isTrue();

        RegisteredClient savedNew = saved.get(1);
        assertThat(savedNew.getClientId()).isEqualTo("new-client");
        assertThat(savedNew.getClientSecret()).isNull();
        assertThat(savedNew.getClientIdIssuedAt()).isNotNull();
        assertThat(savedNew.getClientAuthenticationMethods())
                .extracting(ClientAuthenticationMethod::getValue)
                .containsExactly("none");
        assertThat(savedNew.getAuthorizationGrantTypes())
                .extracting(AuthorizationGrantType::getValue)
                .containsExactly("client_credentials");
        assertThat(savedNew.getTokenSettings().getAccessTokenFormat()).isEqualTo(OAuth2TokenFormat.SELF_CONTAINED);
    }
}
