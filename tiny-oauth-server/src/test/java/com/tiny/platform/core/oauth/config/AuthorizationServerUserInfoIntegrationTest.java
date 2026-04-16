package com.tiny.platform.core.oauth.config;

import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tiny.platform.core.oauth.config.jackson.JacksonConfig;
import com.tiny.platform.core.oauth.security.AuthUserResolutionService;
import com.tiny.platform.core.oauth.security.PermissionVersionService;
import com.tiny.platform.core.oauth.service.SecurityService;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.core.oauth.tenant.TenantContextFilter;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@SpringBootTest(
    webEnvironment = WebEnvironment.MOCK,
    classes = AuthorizationServerUserInfoIntegrationTest.AuthorizationServerUserInfoTestApp.class
)
@AutoConfigureMockMvc
class AuthorizationServerUserInfoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    @Qualifier("oauth2AuthorizationService")
    private OAuth2AuthorizationService oauth2AuthorizationService;

    @Autowired
    @Qualifier("registeredClientRepository")
    private RegisteredClientRepository registeredClientRepository;

    @Test
    void platformUserInfo_should_accept_role_codes_after_authorization_mapper_round_trip() throws Exception {
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
        Mockito.when(registeredClientRepository.findById("rc-id")).thenReturn(client);
        Mockito.when(registeredClientRepository.findByClientId("vue-client")).thenReturn(client);

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(300);
        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).build(),
                JwtClaimsSet.builder()
                    .issuer("http://localhost:9000/platform")
                    .subject("platform_admin")
                    .audience(List.of("vue-client"))
                    .issuedAt(issuedAt)
                    .expiresAt(expiresAt)
                    .claim("scope", "openid profile")
                    .claim("userId", 1481L)
                    .claim("username", "platform_admin")
                    .claim("activeScopeType", TenantContextContract.SCOPE_TYPE_PLATFORM)
                    .claim("roleCodes", List.of("ROLE_PLATFORM_ADMIN"))
                    .claim("authorities", List.of("system:tenant:list"))
                    .claim("permissions", List.of("system:tenant:list"))
                    .claim("permissionsVersion", "perm-plat-v1")
                    .build()
            ))
            .getTokenValue();

        Map<String, Object> accessTokenClaims = new LinkedHashMap<>();
        accessTokenClaims.put("sub", "platform_admin");
        accessTokenClaims.put("userId", 1481L);
        accessTokenClaims.put("username", "platform_admin");
        accessTokenClaims.put("activeScopeType", TenantContextContract.SCOPE_TYPE_PLATFORM);
        accessTokenClaims.put("roleCodes", new ArrayList<>(List.of("ROLE_PLATFORM_ADMIN")));
        accessTokenClaims.put("authorities", new ArrayList<>(List.of("system:tenant:list")));
        accessTokenClaims.put("permissions", new ArrayList<>(List.of("system:tenant:list")));
        accessTokenClaims.put("permissionsVersion", "perm-plat-v1");

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            tokenValue,
            issuedAt,
            expiresAt,
            Set.of("openid", "profile"));
        OidcIdToken idToken = new OidcIdToken(
            "id-token-value",
            issuedAt,
            expiresAt,
            Map.of("sub", "platform_admin"));

        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
            .id("auth-id")
            .principalName("platform_admin")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizedScopes(Set.of("openid", "profile"))
            .token(accessToken, metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, accessTokenClaims))
            .token(idToken, metadata -> metadata.put(
                OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                new LinkedHashMap<>(Map.of("sub", "platform_admin"))))
            .build();

        Mockito.when(oauth2AuthorizationService.findByToken(eq(tokenValue), eq(OAuth2TokenType.ACCESS_TOKEN)))
            .thenReturn(authorization);

        mockMvc.perform(get("/platform/userinfo")
                .header("Authorization", "Bearer " + tokenValue)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.sub").value("platform_admin"));
    }

    @SpringBootConfiguration
    @Import({
        AuthorizationServerConfig.class,
        JacksonConfig.class,
        HttpMessageConvertersAutoConfiguration.class,
        WebMvcAutoConfiguration.class
    })
    static class AuthorizationServerUserInfoTestApp {

        @Bean("corsConfigurationSource")
        CorsConfigurationSource corsConfigurationSource() {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.addAllowedOriginPattern("*");
            configuration.addAllowedHeader("*");
            configuration.addAllowedMethod("*");
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", configuration);
            return source;
        }

        @Bean
        ClientProperties clientProperties() throws Exception {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
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
            return clientProperties;
        }

        @Bean
        SecurityService securityService() {
            return Mockito.mock(SecurityService.class);
        }

        @Bean
        AuthUserResolutionService authUserResolutionService() {
            return Mockito.mock(AuthUserResolutionService.class);
        }

        @Bean
        FrontendProperties frontendProperties() {
            FrontendProperties frontendProperties = new FrontendProperties();
            frontendProperties.setTotpBindUrl("/totp-bind");
            frontendProperties.setTotpVerifyUrl("/totp-verify");
            return frontendProperties;
        }

        @Bean
        UserRepository userRepository() {
            return Mockito.mock(UserRepository.class);
        }

        @Bean
        PermissionVersionService permissionVersionService() {
            return Mockito.mock(PermissionVersionService.class);
        }

        @Bean
        UserDetailsService userDetailsService() {
            return Mockito.mock(UserDetailsService.class);
        }

        @Bean
        TenantRepository tenantRepository() {
            return Mockito.mock(TenantRepository.class);
        }

        @Bean
        TenantContextFilter tenantContextFilter(TenantRepository tenantRepository) {
            return new TenantContextFilter(tenantRepository);
        }

        @Bean
        JwtAuthenticationConverter tinyPlatformJwtAuthenticationConverter() {
            JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
            converter.setJwtGrantedAuthoritiesConverter(new TinyPlatformJwtGrantedAuthoritiesConverter());
            return converter;
        }

        @Bean(name = "registeredClientRepository")
        RegisteredClientRepository registeredClientRepository() {
            return Mockito.mock(RegisteredClientRepository.class);
        }

        @Bean(name = "oauth2AuthorizationService")
        OAuth2AuthorizationService oauth2AuthorizationService() {
            return Mockito.mock(OAuth2AuthorizationService.class);
        }

        @Bean(name = "customOAuth2AuthorizationConsentService")
        OAuth2AuthorizationConsentService oauth2AuthorizationConsentService() {
            return Mockito.mock(OAuth2AuthorizationConsentService.class);
        }

        private static String toPem(String type, byte[] encoded) {
            String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded);
            return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
        }
    }
}
