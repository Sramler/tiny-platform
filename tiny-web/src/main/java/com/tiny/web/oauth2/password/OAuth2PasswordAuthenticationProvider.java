package com.tiny.web.oauth2.password;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.util.Assert;

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

/**
 * 自定义 OAuth2 Password 模式的认证处理器
 */
public class OAuth2PasswordAuthenticationProvider implements AuthenticationProvider {

    private static final AuthorizationGrantType PASSWORD_GRANT_TYPE = new AuthorizationGrantType("password");

    // Spring Security 提供的用于用户名密码认证的组件
    private final AuthenticationManager authenticationManager;

    // 用于保存授权信息（access token、refresh token 等）
    private final OAuth2AuthorizationService authorizationService;

    // 用于生成 OAuth2 Token 的通用接口（AccessToken、RefreshToken）
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;

    // 构造方法，注入认证管理器、授权服务、token 生成器
    public OAuth2PasswordAuthenticationProvider(AuthenticationManager authenticationManager,
                                                OAuth2AuthorizationService authorizationService,
                                                OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator) {
        Assert.notNull(authenticationManager, "authenticationManager cannot be null"); // 参数不能为空
        Assert.notNull(authorizationService, "authorizationService cannot be null");
        Assert.notNull(tokenGenerator, "tokenGenerator cannot be null");

        this.authenticationManager = authenticationManager;
        this.authorizationService = authorizationService;
        this.tokenGenerator = tokenGenerator;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        // 强转为自定义的 PasswordToken（包含 username、password、scope 等信息）
        OAuth2PasswordAuthenticationToken passwordAuthToken = (OAuth2PasswordAuthenticationToken) authentication;

        // 获取客户端信息（客户端已在认证转换器中校验）
        Authentication clientPrincipal = (Authentication) passwordAuthToken.getPrincipal();
        RegisteredClient registeredClient = null;

        if (clientPrincipal instanceof OAuth2ClientAuthenticationToken clientAuthentication) {
            registeredClient = clientAuthentication.getRegisteredClient();
            // ✅ 此处拿到的是正确类型
        } else {
            throw new OAuth2AuthenticationException("Invalid client authentication");
        }

        // 使用用户名密码进行身份认证（走 Spring Security 流程）
        UsernamePasswordAuthenticationToken usernamePasswordAuth =
                new UsernamePasswordAuthenticationToken(passwordAuthToken.getUsername(), passwordAuthToken.getPassword());
        Authentication userAuthentication = authenticationManager.authenticate(usernamePasswordAuth);

        // 用户认证失败时抛出异常（用户不存在、密码错误等）
        if (userAuthentication == null || !userAuthentication.isAuthenticated()) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }

        // ========== 校验 Scope ==========

        Set<String> authorizedScopes = new HashSet<>(); // 最终授权的 scope 集合
        Set<String> requestedScopes = passwordAuthToken.getScopes(); // 客户端请求的 scope
        Set<String> clientScopes = registeredClient.getScopes();     // 客户端注册的 scope

        // 如果传入了 scope，就校验每一项是否在客户端注册的 scope 中
        if (requestedScopes != null && !requestedScopes.isEmpty()) {
            for (String scope : requestedScopes) {
                if (!clientScopes.contains(scope)) {
                    // 如果请求的 scope 不合法，抛出异常
                    throw new OAuth2AuthenticationException(
                            new OAuth2Error(OAuth2ErrorCodes.INVALID_SCOPE, "Invalid scope: " + scope, null));
                }
                authorizedScopes.add(scope); // 加入授权 scope 列表
            }
        } else {
            // 如果未指定，则默认授权所有已注册的 scope
            authorizedScopes.addAll(clientScopes);
        }

        // ========== 生成 Access Token ==========

        // 构建 Access Token 生成上下文
        OAuth2TokenContext tokenContext = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)                          // 客户端信息
                .principal(userAuthentication)                               // 用户认证信息
                .authorizationGrantType(PASSWORD_GRANT_TYPE)                 // 授权类型为 password
                .authorizationGrant(passwordAuthToken)                       // 授权参数对象
                .authorizedScopes(authorizedScopes)                          // 授权 scope 列表
                .authorizationServerContext(AuthorizationServerContextHolder.getContext()) // 授权服务器上下文
                .tokenType(OAuth2TokenType.ACCESS_TOKEN)                     // 生成类型为 AccessToken
                .build();
        // 调用 tokenGenerator 生成 Access Token
        OAuth2Token token = tokenGenerator.generate(tokenContext);


//        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
//                token.getTokenValue(), token.getIssuedAt(),
//                token.getExpiresAt(), authorizedScopes);

        // 校验生成结果是否为 AccessToken 类型
        if (!(token instanceof OAuth2AccessToken accessToken)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR, "Failed to generate access token", null));
        }

        // ========== 生成 Refresh Token（可选） ==========

        OAuth2RefreshToken refreshToken = null; // 初始化 RefreshToken

        // 构建 RefreshToken 的生成上下文
        OAuth2TokenContext refreshTokenContext = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(userAuthentication)
                .authorizationGrantType(PASSWORD_GRANT_TYPE)
                .authorizationGrant(passwordAuthToken)
                .authorizedScopes(authorizedScopes)
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .tokenType(OAuth2TokenType.REFRESH_TOKEN)
                .build();

        // 生成 Refresh Token
        OAuth2Token refresh = this.tokenGenerator.generate(refreshTokenContext);
        if (refresh instanceof OAuth2RefreshToken rt) {
            refreshToken = rt;
        }

//        refreshToken = new OAuth2RefreshToken(refresh.getTokenValue(), token.getIssuedAt(),
//                refresh.getExpiresAt());

        // ========== 构建并保存 Authorization ==========

        OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.withRegisteredClient(registeredClient)
                .principalName(userAuthentication.getName())                       // 设置授权人
                .authorizationGrantType(PASSWORD_GRANT_TYPE)                      // 设置授权类型
                .authorizedScopes(authorizedScopes)                               // 设置授权 scope
                .attribute(Principal.class.getName(), userAuthentication)         // 保存 Principal 对象
                .accessToken(accessToken);                                        // 设置 AccessToken

        // 可选设置 Refresh Token
        if (refreshToken != null) {
            authorizationBuilder.refreshToken(refreshToken);
        }

        // 构建 Authorization 对象并保存到存储层
        OAuth2Authorization authorization = authorizationBuilder.build();

        // 存储授权信息
        this.authorizationService.save(authorization);

        // ========== 返回认证结果（用于响应 OAuth2AccessTokenResponse） ==========

        // 返回认证结果（包含 accessToken、refreshToken）
        return new OAuth2AccessTokenAuthenticationToken(
                registeredClient,          // 客户端
                clientPrincipal,           // 客户端认证信息
                accessToken,               // access token
                refreshToken
                //,              // refresh token（可选）
                //authorizedScopes           // 返回的 scope
        );
    }

    // 指定支持的认证类型（只处理 OAuth2PasswordAuthenticationToken）
    @Override
    public boolean supports(Class<?> authentication) {
        // 指定支持的 Authentication 类型
        return OAuth2PasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
