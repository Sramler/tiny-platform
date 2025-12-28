package com.tiny.platform.core.oauth.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Instant;
import java.util.UUID;

/**
 * OAuth2 RegisteredClient 注册配置类
 *
 * 本配置类在 Spring Boot 应用启动时，
 * 自动读取 YAML 中定义的客户端（client）信息，
 * 并注册到 Spring Authorization Server 的 RegisteredClientRepository 中。
 *
 * 支持的注册项包括：
 * - 客户端 ID 与密钥
 * - 授权类型（authorization_code、refresh_token 等）
 * - 客户端认证方式（如 client_secret_basic、none 等）
 * - 重定向 URI、注销 URI
 * - scope 权限声明
 * - token 配置（有效期、格式、是否重用等）
 * - 客户端行为设置（是否需要授权确认页面、是否使用 PKCE 等）
 *
 * 依赖：
 * - ClientProperties（需通过 @EnableConfigurationProperties 启用）
 * - RegisteredClientRepository（可为内存、数据库等实现）
 */
@Configuration
@EnableConfigurationProperties(ClientProperties.class)
public class RegisteredClientConfig {

    /**
     * 应用启动后，自动注册 YAML 配置中的客户端到 RegisteredClientRepository
     */
    @Bean
    public CommandLineRunner registerClients(ClientProperties properties, RegisteredClientRepository repository) {
        return args -> {
            for (ClientProperties.Client config : properties.getClients()) {

                RegisteredClient existing = repository.findByClientId(config.getClientId());

                // === 构建 Token 设置 ===
                // 令牌有效期、类型（JWT / Reference）、是否重用 refresh token 等
                ClientProperties.Client.TokenSetting t = config.getTokenSetting();
                TokenSettings tokenSettings = TokenSettings.builder()
                        .accessTokenTimeToLive(t.getAccessTokenTimeToLive())             // Access Token 有效期
                        .refreshTokenTimeToLive(t.getRefreshTokenTimeToLive())           // Refresh Token 有效期
                        .reuseRefreshTokens(t.isReuseRefreshTokens())                    // 是否重用 refresh token
                        .accessTokenFormat("reference".equalsIgnoreCase(t.getAccessTokenFormat())
                                ? OAuth2TokenFormat.REFERENCE                            // Reference Token：需服务器存储
                                : OAuth2TokenFormat.SELF_CONTAINED)                      // Self-contained：JWT，默认
                        .build();

                // === 构建 Client 设置 ===
                // 客户端行为设置，如是否需要授权确认页面、是否启用 PKCE
                ClientProperties.Client.ClientSetting c = config.getClientSetting();
                ClientSettings clientSettings = ClientSettings.builder()
                        .requireAuthorizationConsent(c.isRequireAuthorizationConsent())   // 是否显示授权确认页（consent）
                        .requireProofKey(c.isRequireProofKey())                          // 是否启用 PKCE（常用于 SPA）
                        .build();

                // === 构建 RegisteredClient 对象 ===
                String registeredId = existing != null ? existing.getId() : UUID.randomUUID().toString();
                Instant clientIdIssuedAt = existing != null && existing.getClientIdIssuedAt() != null
                        ? existing.getClientIdIssuedAt()
                        : Instant.now();

                RegisteredClient.Builder builder = RegisteredClient.withId(registeredId)
                        .clientId(config.getClientId())                                   // 客户端 ID
                        .clientIdIssuedAt(clientIdIssuedAt)
                        .clientSecret(config.getClientSecret() != null
                                ? "{noop}" + config.getClientSecret()                     // {noop} 表示明文，开发环境用
                                : null)
                        .clientName(config.getClientId())                                  // 可选：客户端名称
                        .clientSettings(clientSettings)                                    // 客户端行为设置
                        .tokenSettings(tokenSettings);                                     // Token 设置

                // === 配置认证方式 ===
                config.getAuthenticationMethods().forEach(method ->
                        builder.clientAuthenticationMethod(new ClientAuthenticationMethod(method))
                );

                // === 配置授权模式 ===
                config.getGrantTypes().forEach(grant ->
                        builder.authorizationGrantType(new AuthorizationGrantType(grant))
                );

                // === 配置 scope 范围（权限）===
                if (config.getScopes() != null) {
                    config.getScopes().forEach(builder::scope);
                }

                // === 配置重定向 URI（授权成功跳转地址）===
                if (config.getRedirectUris() != null) {
                    config.getRedirectUris().forEach(builder::redirectUri);
                }

                // === 配置注销后的跳转 URI（可选）===
                if (config.getPostLogoutRedirectUris() != null) {
                    config.getPostLogoutRedirectUris().forEach(builder::postLogoutRedirectUri);
                }

                // === 最终保存注册的客户端信息到存储库 ===
                repository.save(builder.build());
            }
        };
    }
}
