package com.tiny.web.oauth2.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.tiny.web.oauth2.password.OAuth2PasswordAuthenticationConverter;
import com.tiny.web.oauth2.password.OAuth2PasswordAuthenticationProvider;
import com.tiny.web.util.PemUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
public class OAuth2AuthorizationServerConfig {

    @Autowired
    private ClientProperties authProperties;
    /**
     * 授权信息（如 access_token、refresh_token）存储到数据库
     */
    @Bean
    public OAuth2AuthorizationService authorizationService(
            JdbcTemplate jdbcTemplate,
            RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public OAuth2TokenGenerator<?> tokenGenerator(JwtEncoder jwtEncoder) {
        JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);
        // 设置 jwtCustomizer ...
        JwtToOAuth2AccessTokenGenerator accessTokenWrapper = new JwtToOAuth2AccessTokenGenerator(jwtGenerator);
        OAuth2RefreshTokenGenerator refreshTokenGenerator = new OAuth2RefreshTokenGenerator();

        return new DelegatingOAuth2TokenGenerator(
                accessTokenWrapper,
                refreshTokenGenerator
        );


    }


    /**
     * 配置授权服务器的安全过滤器链
     * 包括端点如：/oauth2/token、/.well-known/jwks.json
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
                                                                      AuthenticationManager authenticationManager,
                                                                      OAuth2AuthorizationService authorizationService,
                                                                      OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();
        // 手动扩展 password 模式支持并关闭授权同意页
        authorizationServerConfigurer
                .tokenEndpoint(tokenEndpoint -> tokenEndpoint
                        .accessTokenRequestConverter(new OAuth2PasswordAuthenticationConverter())
                        .authenticationProvider(
                                new OAuth2PasswordAuthenticationProvider(
                                        authenticationManager,
                                        authorizationService,
                                        tokenGenerator
                                )
                        ))
                .authorizationEndpoint(authorizationEndpoint -> authorizationEndpoint
                        .consentPage(null));

        RequestMatcher endpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();

        http
                // 仅对授权服务器端点应用此过滤器链
                .securityMatcher(endpointsMatcher)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher))
                .with(authorizationServerConfigurer, Customizer.withDefaults());

        // 资源服务器支持（与 applyDefaultSecurity 原行为保持一致）
        // jwt() 无参方法在 Spring Security 6.1 起已弃用，改为使用 Customizer 形式
        http.oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));

        return http.build();
    }
//    @Bean
//    @Order(1)
//    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
//                                                                      OAuth2AuthorizationService authorizationService,
//                                                                      OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator) throws Exception {
//
//        // ✅ 应用默认安全配置，注册标准端点
//        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
//        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();
//
//        authorizationServerConfigurer
//                .tokenEndpoint(tokenEndpoint ->
//                        tokenEndpoint
//                                .accessTokenRequestConverter(new OAuth2PasswordAuthenticationConverter())
//                                .authenticationProvider(
//                                        new OAuth2PasswordAuthenticationProvider(
//                                                authenticationManager,
//                                                authorizationService,
//                                                tokenGenerator
//                                        )
//                                )
//                );
//        http
//                .apply(authorizationServerConfigurer)
//                .and()
//                .csrf(csrf -> csrf.disable());
//
//        return http.build();
//    }

    /**
     * 注册客户端信息（如 client_id、client_secret、授权模式等），并从数据库中加载
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }



    /**
     * 授权同意信息（authorization_code 授权场景中才用），持久化到数据库
     */
    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcTemplate jdbcTemplate,
            RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }

    /**
     * JWK 密钥对（用于签发 JWT），加载本地 PEM 格式密钥
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() throws Exception {
        RSAPublicKey publicKey = PemUtils.readPublicKey(authProperties.getJwt().getPublicKeyPath());
        RSAPrivateKey privateKey = PemUtils.readPrivateKey(authProperties.getJwt().getPrivateKeyPath());

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID("auth-server-key")
                .build();

        return (selector, context) -> selector.select(new JWKSet(rsaKey));
    }

    /**
     * 设置授权服务器的公开地址（issuer），用于 JWT 校验
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer("http://localhost:8080") // 替代 ProviderSettings
                .build();
    }
}
