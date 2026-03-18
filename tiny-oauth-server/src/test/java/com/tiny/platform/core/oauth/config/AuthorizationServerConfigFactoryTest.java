package com.tiny.platform.core.oauth.config;

import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.tiny.platform.core.oauth.security.AuthUserResolutionService;
import com.tiny.platform.core.oauth.security.MfaAuthorizationEndpointFilter;
import com.tiny.platform.core.oauth.security.PermissionVersionService;
import com.tiny.platform.core.oauth.service.SecurityService;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.web.cors.CorsConfigurationSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuthorizationServerConfigFactoryTest {

    @Test
    void shouldCreateLightweightAuthorizationServerBeans() {
        ClientProperties clientProperties = new ClientProperties();
        clientProperties.setJwt(new ClientProperties.Jwt());
        CorsConfigurationSource corsConfigurationSource = mock(CorsConfigurationSource.class);
        AuthorizationServerConfig config = new AuthorizationServerConfig(clientProperties, corsConfigurationSource);

        @SuppressWarnings("unchecked")
        JWKSource<SecurityContext> jwkSource = (JWKSource<SecurityContext>) mock(JWKSource.class);

        JwtDecoder jwtDecoder = config.jwtDecoder(jwkSource);
        assertThat(jwtDecoder).isNotNull();

        AuthorizationServerSettings settings = config.authorizationServerSettings();
        assertThat(settings).isNotNull();
        assertThat(settings.isMultipleIssuersAllowed()).isTrue();

        UserRepository userRepository = mock(UserRepository.class);
        AuthUserResolutionService authUserResolutionService = mock(AuthUserResolutionService.class);
        PermissionVersionService permissionVersionService = mock(PermissionVersionService.class);
        JwtTokenCustomizer customizer = config.jwtTokenCustomizer(
                userRepository,
                authUserResolutionService,
                permissionVersionService
        );
        assertThat(customizer).isNotNull();

        JwtEncoder jwtEncoder = config.jwtEncoder(jwkSource);
        assertThat(jwtEncoder).isNotNull();
        assertThat(jwtEncoder.getClass().getName()).contains("NimbusJwtEncoder");

        OAuth2TokenGenerator<?> tokenGenerator = config.tokenGenerator(jwtEncoder, customizer);
        assertThat(tokenGenerator).isNotNull();
        assertThat(tokenGenerator.getClass().getName()).contains("DelegatingOAuth2TokenGenerator");

        SecurityService securityService = mock(SecurityService.class);
        FrontendProperties frontendProperties = new FrontendProperties();
        frontendProperties.setTotpBindUrl("/totp-bind");
        frontendProperties.setTotpVerifyUrl("/totp-verify");
        MfaAuthorizationEndpointFilter filter =
                config.mfaAuthorizationEndpointFilter(
                        securityService,
                        authUserResolutionService,
                        frontendProperties
                );
        assertThat(filter).isNotNull();
    }

    @Test
    void shouldCreateJwkSourceFromPemFiles() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        Path publicPem = Files.createTempFile("auth-public-", ".pem");
        Path privatePem = Files.createTempFile("auth-private-", ".pem");
        Files.writeString(publicPem, toPem("PUBLIC KEY", ((RSAPublicKey) keyPair.getPublic()).getEncoded()));
        Files.writeString(privatePem, toPem("PRIVATE KEY", ((RSAPrivateKey) keyPair.getPrivate()).getEncoded()));

        ClientProperties clientProperties = new ClientProperties();
        ClientProperties.Jwt jwt = new ClientProperties.Jwt();
        jwt.setPublicKeyPath(publicPem.toString());
        jwt.setPrivateKeyPath(privatePem.toString());
        clientProperties.setJwt(jwt);

        AuthorizationServerConfig config =
                new AuthorizationServerConfig(clientProperties, mock(CorsConfigurationSource.class));

        JWKSource<SecurityContext> source = config.jwkSource();
        assertThat(source.get(new JWKSelector(new JWKMatcher.Builder().build()), null)).hasSize(1);
    }

    private static String toPem(String type, byte[] encoded) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
    }
}
