package com.tiny.platform.core.oauth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.tiny.platform.infrastructure.core.util.PemUtils;
import com.tiny.platform.core.oauth.security.AuthUserResolutionService;
import com.tiny.platform.core.oauth.security.AuthorizationEndpointMfaAuthorizationManager;
import com.tiny.platform.core.oauth.security.AuthorizationEndpointMfaEntryPoint;
import com.tiny.platform.core.oauth.security.AuthenticationFactorAuthorities;
import com.tiny.platform.core.oauth.tenant.IssuerTenantSupport;
import com.tiny.platform.core.oauth.tenant.TenantContextFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
@EnableWebSecurity
public class AuthorizationServerConfig {

    private final ClientProperties clientProperties;


    private final CorsConfigurationSource corsConfigurationSource;

    public AuthorizationServerConfig(ClientProperties authProperties,@Qualifier("corsConfigurationSource")CorsConfigurationSource corsConfigurationSource) {
        this.clientProperties = authProperties;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    /**
     * Spring Authorization Server 相关配置
     * 此处方法与下面defaultSecurityFilterChain都是SecurityFilterChain配置，配置的内容有点区别，
     * 因为Spring Authorization Server是建立在Spring Security 基础上的，defaultSecurityFilterChain方法主要
     * 配置Spring Security相关的东西，而此处authorizationServerSecurityFilterChain方法主要配置OAuth 2.1和OpenID Connect 1.0相关的东西
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
                                                                     @Qualifier("registeredClientRepository") RegisteredClientRepository registeredClientRepository,
                                                                     @Qualifier("oauth2AuthorizationService") OAuth2AuthorizationService oauth2AuthorizationService,
                                                                     @Qualifier("customOAuth2AuthorizationConsentService") OAuth2AuthorizationConsentService oauth2AuthorizationConsentService,
                                                                     AuthorizationServerSettings authorizationServerSettings,
                                                                     OAuth2TokenGenerator<? extends org.springframework.security.oauth2.core.OAuth2Token> tokenGenerator,
                                                                     AuthorizationEndpointMfaAuthorizationManager authorizationEndpointMfaAuthorizationManager,
                                                                     AuthorizationEndpointMfaEntryPoint authorizationEndpointMfaEntryPoint,
                                                                     TenantContextFilter tenantContextFilter,
                                                                     JwtAuthenticationConverter tinyPlatformJwtAuthenticationConverter)
            throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                new OAuth2AuthorizationServerConfigurer();
        RequestMatcher authorizationEndpointMatcher =
                request -> IssuerTenantSupport.isAuthorizationEndpointPath(request.getRequestURI());

        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, authorizationServer -> authorizationServer
                        .registeredClientRepository(registeredClientRepository)
                        .authorizationService(oauth2AuthorizationService)
                        .authorizationConsentService(oauth2AuthorizationConsentService)
                        .authorizationServerSettings(authorizationServerSettings)
                        .tokenGenerator(tokenGenerator)
                        // 开启 OpenID Connect 1.0（其中 oidc 为 OpenID Connect 的缩写）。
                        .oidc(Customizer.withDefaults()));
        http
                .addFilterBefore(tenantContextFilter,
                        org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(authorizationEndpointMatcher).access(authorizationEndpointMfaAuthorizationManager)
                        .anyRequest().permitAll()
                )
                //将需要认证的请求，重定向到login页面行登录认证。
                // 注意：只对 HTML 请求重定向到登录页，API 请求（如 /oauth2/token）返回 JSON 错误
                .exceptionHandling((exceptions) -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                        )
                        // 对于非 HTML 请求（如 JSON、表单、OAuth2 端点等），返回 401 JSON 错误而不是重定向
                        .defaultAuthenticationEntryPointFor(
                                (request, response, authException) -> {
                                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                                    response.setCharacterEncoding("UTF-8");
                                    String errorMessage = authException.getMessage() != null 
                                        ? authException.getMessage().replace("\"", "\"") 
                                        : "Unauthorized";
                                    response.getWriter().write("{\"error\":\"unauthorized\",\"error_description\":\"" + errorMessage + "\"}");
                                },
                                // 匹配所有非 HTML 请求，包括 OAuth2 端点
                                request -> {
                                    String uri = request.getRequestURI();
                                    // OAuth2 和 OIDC 端点总是返回 JSON 或重定向，不是 JSON 响应
                                    if (IssuerTenantSupport.isAuthorizationServerEndpointPath(uri)) {
                                        // 注销端点可能需要 HTML 重定向，所以特殊处理
                                        if (uri.equals("/connect/logout") || uri.matches("^/[a-z0-9][a-z0-9-]{1,31}/connect/logout$")) {
                                            return false; // 注销端点可能需要 HTML 重定向
                                        }
                                        return true;
                                    }
                                    String acceptHeader = request.getHeader("Accept");
                                    String contentType = request.getContentType();
                                    // 如果 Accept 头包含 JSON，或者 Content-Type 是表单，返回 JSON
                                    return (acceptHeader != null && acceptHeader.contains(MediaType.APPLICATION_JSON_VALUE)) ||
                                           (contentType != null && contentType.contains(MediaType.APPLICATION_FORM_URLENCODED_VALUE));
                                }
                        )
                        .defaultDeniedHandlerForMissingAuthority(
                                (entryPoints) -> entryPoints.addEntryPointFor(
                                        authorizationEndpointMfaEntryPoint,
                                        authorizationEndpointMatcher
                                ),
                                AuthenticationFactorAuthorities.toAuthority(
                                        com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP
                                )
                        )
                )
                //.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())) // 👈 添加这行

                .headers(headers -> headers.frameOptions(frame -> frame.disable()))
                .cors(cors -> cors.configurationSource(corsConfigurationSource)) // 启用并设置 CORS
                .csrf(csrf -> csrf.disable()) // 前后端分离建议关闭 CSRF，或使用 Token 保护
                // 使用 JWT 处理 Bearer token，与 DefaultSecurityConfig 共用同一 converter，使 authorities/permissions 正确映射
                .oauth2ResourceServer((resourceServer) -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(tinyPlatformJwtAuthenticationConverter)));


        return http.build();
    }





    /**
     *设置用户信息，校验用户名、密码
     * 这里或许有人会有疑问，不是说OAuth 2.1已经移除了密码模式了码？怎么这里还有用户名、密码登录？
     * 例如：某平台app支持微信登录，用户想使用微信账号登录登录该平台app，则用户需先登录微信app，
     * 此处代码的操作就类似于某平台app跳到微信登录界面让用户先登录微信，然后微信校验用户提交的用户名、密码，
     * 登录了微信才对某平台app进行授权，对于微信平台来说，某平台的app就是OAuth 2.1中的客户端。
     * 其实，这一步是Spring Security的操作，纯碎是认证平台的操作，是脱离客户端（第三方平台）的。
     */
//    @Bean
//    public UserDetailsService userDetailsService() {
//        UserDetails userDetails = User.withDefaultPasswordEncoder()
//                .username("user")
//                .password("password")
//                .roles("USER")
//                .build();
//        //基于内存的用户数据校验
//        return new InMemoryUserDetailsManager(userDetails);
//    }



//    /**
//     * 注册客户端信息
//     */
//    @Bean
//    public RegisteredClientRepository registeredClientRepository() {
//        RegisteredClient oidcClient = RegisteredClient.withId(UUID.randomUUID().toString())
//                .clientId("oidc-client")
//                //{noop}开头，表示"secret"以明文存储
//                .clientSecret("{noop}secret")
//                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
//                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
//                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
//                //.redirectUri("http://127.0.0.1:8080/login/oauth2/code/oidc-client")
//                //将上面的redirectUri地址注释掉，改成下面的地址，是因为我们暂时还没有客户端服务，以免重定向跳转错误导致接收不到授权码
//                //.redirectUri("http://www.baidu.com")
//                .redirectUri("http://localhost:9000/")
//                //退出操作，重定向地址，暂时也没遇到
//                .postLogoutRedirectUri("http://127.0.0.1:8080/")
//                //设置客户端权限范围
//                .scope(OidcScopes.OPENID)
//                .scope(OidcScopes.PROFILE)
//                //客户端设置用户需要确认授权
//                .clientSettings(ClientSettings.builder()
//                        //.requireAuthorizationConsent(true)
//                        .requireAuthorizationConsent(false) // 👈 自动授权，跳过 consent 页面
//                        .build()
//                )
//                .build();
//        //配置基于内存的客户端信息
//        return new InMemoryRegisteredClientRepository(oidcClient);
//    }


    @Bean
    public JWKSource<SecurityContext> jwkSource() throws Exception {
        RSAPublicKey publicKey = PemUtils.readPublicKey(clientProperties.getJwt().getPublicKeyPath());
        RSAPrivateKey privateKey = PemUtils.readPrivateKey(clientProperties.getJwt().getPrivateKeyPath());

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID("auth-server-key")
                .build();

        return (selector, context) -> selector.select(new JWKSet(rsaKey));
    }

    /**
     * 配置jwt解析器
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /**
     *配置认证服务器请求地址
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        // 严格按 SAS 多 issuer 指南启用 path-based issuer
        return AuthorizationServerSettings.builder()
                .multipleIssuersAllowed(true)
                .build();
    }

    /**
     * JWT Token 自定义器 Bean
     * 
     * 用于为 access_token、id_token 和 refresh_token 添加自定义 claims（参数），
     * 符合 OAuth 2.1 和 OpenID Connect 1.0 企业级规范。
     * 
     * 添加的字段包括：
     * - 用户ID、用户名、权限列表
     * - 客户端ID、授权范围
     * - 认证时间（auth_time）
     * - 认证方法引用（amr）
     * - 用户基本信息（ID Token：name, email, phone 等）
     * 
     * @param userRepository 用户仓库，用于查询完整的用户信息（email, phone, nickname 等）
     * @return JwtTokenCustomizer 实例
     */
    @Bean
    public JwtTokenCustomizer jwtTokenCustomizer(
            com.tiny.platform.infrastructure.auth.user.repository.UserRepository userRepository,
            AuthUserResolutionService authUserResolutionService,
            com.tiny.platform.core.oauth.security.PermissionVersionService permissionVersionService,
            UserDetailsService userDetailsService) {
        return new JwtTokenCustomizer(userRepository, authUserResolutionService, permissionVersionService, userDetailsService);
    }

    /**
     * OAuth2 Token 生成器配置
     * 
     * 配置了三种 Token 生成器：
     * 1. OAuth2AccessTokenGenerator: 生成标准的 OAuth2 Access Token（如果配置为 reference token）
     * 2. OAuth2RefreshTokenGenerator: 生成 Refresh Token
     * 3. JwtGenerator: 生成 JWT 格式的 Access Token 和 ID Token，并应用自定义 claims
     * 
     * 注意：JwtGenerator 会根据 TokenSettings 中的 accessTokenFormat 决定生成 JWT 还是 reference token。
     * 如果配置为 SELF_CONTAINED，则 JwtGenerator 会生成 JWT 格式的 access_token 和 id_token。
     * 
     * @param jwtEncoder JWT 编码器，用于签名 JWT
     * @param jwtTokenCustomizer JWT Token 自定义器，用于添加自定义 claims
     * @return OAuth2TokenGenerator 组合器
     */
    @Bean
    public OAuth2TokenGenerator<?> tokenGenerator(JwtEncoder jwtEncoder, JwtTokenCustomizer jwtTokenCustomizer) {
        // 创建 JWT 生成器并设置自定义器
        JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);
        jwtGenerator.setJwtCustomizer(jwtTokenCustomizer);
        
        // 创建标准的 OAuth2 Access Token 生成器（用于 reference token 模式）
        OAuth2AccessTokenGenerator accessTokenGenerator = new OAuth2AccessTokenGenerator();
        
        // 创建 Refresh Token 生成器
        OAuth2RefreshTokenGenerator refreshTokenGenerator = new OAuth2RefreshTokenGenerator();

        // 组合多个 Token 生成器
        // DelegatingOAuth2TokenGenerator 会按顺序尝试每个生成器，直到有一个成功生成 Token
        return new DelegatingOAuth2TokenGenerator(
                accessTokenGenerator,
                refreshTokenGenerator,
                jwtGenerator // JwtGenerator 会负责处理 JWT 格式的 access_token 和 id_token
        );
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public AuthorizationEndpointMfaAuthorizationManager authorizationEndpointMfaAuthorizationManager(
            com.tiny.platform.core.oauth.service.SecurityService securityService,
            com.tiny.platform.core.oauth.security.AuthUserResolutionService authUserResolutionService) {
        return new AuthorizationEndpointMfaAuthorizationManager(
                securityService,
                authUserResolutionService
        );
    }

    @Bean
    public AuthorizationEndpointMfaEntryPoint authorizationEndpointMfaEntryPoint(
            FrontendProperties frontendProperties) {
        return new AuthorizationEndpointMfaEntryPoint(frontendProperties);
    }

}
