package com.tiny.platform.core.oauth.config;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JWT Token 自定义器
 * 
 * 用于为 access_token、id_token 和 refresh_token 添加自定义 claims（参数），
 * 符合 OAuth 2.1 和 OpenID Connect 1.0 企业级规范。
 * 
 * 使用场景：
 * - access_token: 添加用户ID、权限列表、认证方法等，供资源服务器使用
 * - id_token: 添加用户基本信息（如昵称、邮箱等），符合 OpenID Connect 规范
 * - refresh_token: 添加用户ID、客户端ID等，用于刷新 token 时的验证和追踪
 * 
 * 标准字段（由框架自动添加）：
 * - iss (Issuer): 签发者
 * - sub (Subject): 主题（用户标识）
 * - aud (Audience): 受众
 * - exp (Expiration Time): 过期时间
 * - iat (Issued At): 签发时间
 * - jti (JWT ID): JWT 唯一标识
 * 
 * 企业级扩展字段（本类添加）：
 * - userId: 用户ID（数据库主键）
 * - username: 用户名
 * - authorities: 权限列表
 * - client_id: 客户端ID
 * - scope: 授权范围
 * - auth_time: 用户认证时间（OIDC 标准字段）
 * - amr: 认证方法引用（如 password, totp）
 * - name/email/phone: 用户基本信息（ID Token）
 * 
 * 注意：
 * - Refresh Token 默认是不透明的字符串（opaque token），不是 JWT 格式
 * - 如果要将 Refresh Token 配置为 JWT 格式，需要自定义 OAuth2RefreshTokenGenerator
 * - 本示例展示了如何为 JWT 格式的 Refresh Token 添加自定义字段
 * 
 * @author Auto-generated
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html">OpenID Connect Core 1.0</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749">OAuth 2.0</a>
 */
public class JwtTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenCustomizer.class);
    private final UserRepository userRepository;

    /**
     * 构造函数
     * 
     * @param userRepository 用户仓库，用于查询完整的用户信息（email, phone, nickname 等）
     */
    public JwtTokenCustomizer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        // 获取认证主体（用户信息）
        Authentication principal = context.getPrincipal();
        
        // 判断 token 类型
        OAuth2TokenType tokenType = context.getTokenType();
        
        if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)) {
            // ========== Access Token 自定义参数 ==========
            customizeAccessToken(context, principal);
        } else if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
            // ========== Refresh Token 自定义参数 ==========
            customizeRefreshToken(context, principal);
        } else {
            // ========== ID Token 或其他 Token 类型自定义参数 ==========
            // 注意：Spring Authorization Server 中，ID Token 可能通过不同的机制生成
            // 这里统一处理非 ACCESS_TOKEN 和 REFRESH_TOKEN 的情况
            customizeIdToken(context, principal);
        }
    }

    /**
     * 为 Access Token 添加自定义 claims
     * 
     * Access Token 通常用于资源服务器验证和授权，包含以下字段：
     * 
     * 标准字段（框架自动添加）：
     * - iss, sub, aud, exp, iat, jti
     * 
     * 企业级扩展字段（本方法添加）：
     * - userId: 用户ID（数据库主键）
     * - username: 用户名
     * - authorities: 权限列表（角色和资源权限）
     * - client_id: 客户端ID
     * - scope: 授权范围
     * - auth_time: 用户认证时间（Unix 时间戳，OIDC 标准字段）
     * - amr: 认证方法引用（如 ["password", "totp"]）
     * 
     * 注意：
     * - principal.getPrincipal() 返回的是 username（字符串）
     * - SecurityUser 对象存储在 principal.getDetails() 中
     */
    private void customizeAccessToken(JwtEncodingContext context, Authentication principal) {
        var claims = context.getClaims();
        
            if (principal == null) {
                log.warn("[JwtTokenCustomizer] Access Token - principal 为 null，无法计算自定义 claims");
                return;
            }
        
        // ========== 详细日志分析 Authentication 结构 ==========
        log.debug("[JwtTokenCustomizer] Access Token - 开始分析 Authentication 结构");
        log.debug("[JwtTokenCustomizer]   - principal 类型: {}", principal != null ? principal.getClass().getName() : "null");
        log.debug("[JwtTokenCustomizer]   - principal.getName(): {}", principal != null ? principal.getName() : "null");
        
        // 分析 principal.getPrincipal()
        Object principalObj = principal != null ? principal.getPrincipal() : null;
        log.debug("[JwtTokenCustomizer]   - principal.getPrincipal() 类型: {}", 
                principalObj != null ? principalObj.getClass().getName() : "null");
        log.debug("[JwtTokenCustomizer]   - principal.getPrincipal() 值: {}", principalObj);
        if (principalObj instanceof SecurityUser) {
            SecurityUser principalSecurityUser = (SecurityUser) principalObj;
            log.debug("[JwtTokenCustomizer]   - principal.getPrincipal() 是 SecurityUser: userId={}, username={}", 
                    principalSecurityUser.getUserId(), principalSecurityUser.getUsername());
        }
        
        // 分析 principal.getDetails()
        Object details = principal != null ? principal.getDetails() : null;
        log.debug("[JwtTokenCustomizer]   - principal.getDetails() 类型: {}", 
                details != null ? details.getClass().getName() : "null");
        log.debug("[JwtTokenCustomizer]   - principal.getDetails() 值: {}", details);
        if (details != null) {
            log.debug("[JwtTokenCustomizer]   - principal.getDetails() toString: {}", details.toString());
        }
        
        // ========== 尝试从多个位置获取 SecurityUser ==========
        SecurityUser securityUser = null;
        String source = null;
        
        // 1. 首先尝试从 details 中获取
        if (principal != null && principal.getDetails() instanceof SecurityUser) {
            securityUser = (SecurityUser) principal.getDetails();
            source = "principal.getDetails()";
            log.debug("[JwtTokenCustomizer]   ✓ 从 principal.getDetails() 获取到 SecurityUser");
        }
        // 2. 如果 details 中没有，尝试从 principal 中获取
        else if (principalObj instanceof SecurityUser) {
            securityUser = (SecurityUser) principalObj;
            source = "principal.getPrincipal()";
            log.debug("[JwtTokenCustomizer]   ✓ 从 principal.getPrincipal() 获取到 SecurityUser");
        }
        // 3. 如果都没有，记录警告
        else {
            log.warn("[JwtTokenCustomizer]   ✗ 未找到 SecurityUser: details={}, principal={}", 
                    details != null ? details.getClass().getName() : "null",
                    principalObj != null ? principalObj.getClass().getName() : "null");
        }
        
        // ========== 分析 SecurityUser 内容 ==========
        if (securityUser != null) {
            Long userId = securityUser.getUserId();
            String username = securityUser.getUsername();
            log.info("[JwtTokenCustomizer] Access Token - SecurityUser 来源: {}, userId: {}, username: {}", 
                    source, userId, username);
            
            if (userId == null) {
                log.error("[JwtTokenCustomizer] Access Token - ⚠ SecurityUser.userId 为 null! username: {}", username);
            }
            
            // 添加用户ID和用户名
            claims.claim("userId", userId);
            claims.claim("username", username);
            
            // 添加权限列表（角色和资源权限）
            Set<String> authorities = principal.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());
            claims.claim("authorities", authorities);
            log.debug("[JwtTokenCustomizer] Access Token - 添加 authorities: {}", authorities);
        } else if (principal != null && principal.getName() != null) {
            // 如果 details 中没有 SecurityUser，至少添加用户名（从 principal.getName() 获取）
            String username = principal.getName();
            claims.claim("username", username);
            log.warn("[JwtTokenCustomizer] Access Token - SecurityUser 为空，仅添加 username: {}", username);
            log.warn("[JwtTokenCustomizer] Access Token - 无法添加 userId，因为未找到 SecurityUser");
        }
        
        // 添加客户端ID
        claims.claim("client_id", context.getRegisteredClient().getClientId());
        
        // 添加授权范围（scope）
        if (context.getAuthorizedScopes() != null && !context.getAuthorizedScopes().isEmpty()) {
            claims.claim("scope", String.join(" ", context.getAuthorizedScopes()));
        }
        
        // 添加认证时间（auth_time）- OIDC 标准字段
        Instant authTime = getAuthTime(context, principal);
        if (authTime != null) {
            claims.claim("auth_time", authTime.getEpochSecond());
        }
        
        // 添加认证方法引用（amr）- 企业级字段
        List<String> amr = getAuthenticationMethods(principal);
        if (!amr.isEmpty()) {
            claims.claim("amr", amr);
                log.debug("[JwtTokenCustomizer] Access Token - 计算 amr: {}", amr);
        }
    }

    /**
     * 为 Refresh Token 添加自定义 claims
     * 
     * Refresh Token 用于刷新 Access Token，包含以下字段：
     * 
     * 标准字段（框架自动添加，如果配置为 JWT 格式）：
     * - iss, sub, aud, exp, iat, jti
     * 
     * 企业级扩展字段（本方法添加）：
     * - userId: 用户ID（用于验证刷新请求的合法性）
     * - username: 用户名（用于日志和审计）
     * - client_id: 客户端ID（用于验证刷新请求是否来自正确的客户端）
     * - grant_type: 授权类型（如 authorization_code、password 等）
     * - scope: 授权范围（用于刷新时保持相同的权限范围）
     * - auth_time: 用户认证时间（Unix 时间戳）
     * 
     * 注意：
     * - Refresh Token 默认是不透明的字符串，不是 JWT 格式
     * - 如果要将 Refresh Token 配置为 JWT 格式，需要自定义 OAuth2RefreshTokenGenerator
     * - 本示例展示了如何为 JWT 格式的 Refresh Token 添加自定义字段
     * - 在生产环境中，建议将 Refresh Token 保持为不透明格式以提高安全性
     * - principal.getPrincipal() 返回的是 username（字符串）
     * - SecurityUser 对象存储在 principal.getDetails() 中
     */
    private void customizeRefreshToken(JwtEncodingContext context, Authentication principal) {
        var claims = context.getClaims();
        
        // 从 details 中获取 SecurityUser（principal.getPrincipal() 返回的是 username 字符串）
        SecurityUser securityUser = null;
        if (principal.getDetails() instanceof SecurityUser) {
            securityUser = (SecurityUser) principal.getDetails();
        }
        
        // 添加用户ID和用户名（用于刷新时的验证和审计）
        if (securityUser != null) {
            Long userId = securityUser.getUserId();
            String username = securityUser.getUsername();
            claims.claim("userId", userId);
            claims.claim("username", username);
            log.info("[JwtTokenCustomizer] Refresh Token - 添加 userId: {}, username: {}", userId, username);
        } else if (principal.getName() != null) {
            claims.claim("username", principal.getName());
            log.warn("[JwtTokenCustomizer] Refresh Token - SecurityUser 为空，仅添加 username: {}", principal.getName());
        }
        
        // 添加客户端ID（用于验证刷新请求是否来自正确的客户端）
        claims.claim("client_id", context.getRegisteredClient().getClientId());
        
        // 添加授权类型（用于追踪 token 的来源）
        if (context.getAuthorizationGrantType() != null) {
            claims.claim("grant_type", context.getAuthorizationGrantType().getValue());
        }
        
        // 添加授权范围（用于刷新时保持相同的权限范围）
        if (context.getAuthorizedScopes() != null && !context.getAuthorizedScopes().isEmpty()) {
            claims.claim("scope", String.join(" ", context.getAuthorizedScopes()));
        }
        
        // 添加认证时间（auth_time）
        Instant authTime = getAuthTime(context, principal);
        if (authTime != null) {
            claims.claim("auth_time", authTime.getEpochSecond());
        }
        
        // 添加自定义字段示例：设备信息、IP 地址等（需要从 context 或其他来源获取）
        // claims.claim("device_id", getDeviceId(context));
        // claims.claim("ip_address", getIpAddress(context));
    }

    /**
     * 为 ID Token 添加自定义 claims
     * 
     * ID Token 遵循 OpenID Connect 1.0 规范，包含以下字段：
     * 
     * 标准必需字段（框架自动添加）：
     * - iss, sub, aud, exp, iat, nonce (如果请求中包含)
     * 
     * 标准可选字段（根据 scope 请求，本方法添加）：
     * - auth_time: 用户认证时间（OIDC 标准必需字段）
     * - name: 用户显示名称（nickname，scope: profile）
     * - email: 用户邮箱（scope: email）
     * - email_verified: 邮箱是否已验证（scope: email）
     * - phone_number: 用户手机号（scope: phone）
     * - phone_number_verified: 手机号是否已验证（scope: phone）
     * 
     * 企业级自定义字段（本方法添加）：
     * - userId: 用户ID（数据库主键）
     * - username: 用户名
     * - amr: 认证方法引用（如 ["password", "totp"]）
     * 
     * 注意：
     * - principal.getPrincipal() 返回的是 username（字符串）
     * - SecurityUser 对象存储在 principal.getDetails() 中
     * - email、phone、nickname 等字段通过 UserRepository 查询完整的 User 实体获取
     */
    private void customizeIdToken(JwtEncodingContext context, Authentication principal) {
        var claims = context.getClaims();
        
        // 从 details 中获取 SecurityUser（principal.getPrincipal() 返回的是 username 字符串）
        SecurityUser securityUser = null;
        if (principal.getDetails() instanceof SecurityUser) {
            securityUser = (SecurityUser) principal.getDetails();
        }
        
        String username = null;
        Long userId = null;
        
        // 添加用户ID和用户名
        if (securityUser != null) {
            userId = securityUser.getUserId();
            username = securityUser.getUsername();
            claims.claim("userId", userId);
            claims.claim("username", username);
            log.info("[JwtTokenCustomizer] ID Token - 添加 userId: {}, username: {}", userId, username);
            
            // 标准 OIDC claims: sub (subject) 通常由框架自动设置
            // 这里确保设置 subject（如果框架未设置，则使用用户名）
            // 注意：如果框架已设置 subject，此调用不会覆盖
            claims.subject(username);
        } else if (principal.getName() != null) {
            username = principal.getName();
            claims.subject(username);
            claims.claim("username", username);
            log.warn("[JwtTokenCustomizer] ID Token - SecurityUser 为空，仅添加 username: {}", username);
        }
        
        // 添加认证时间（auth_time）- OIDC 标准必需字段
        Instant authTime = getAuthTime(context, principal);
        if (authTime != null) {
            claims.claim("auth_time", authTime.getEpochSecond());
        }
        
        // 添加认证方法引用（amr）
        List<String> amr = getAuthenticationMethods(principal);
        if (!amr.isEmpty()) {
            claims.claim("amr", amr);
                log.debug("[JwtTokenCustomizer] ID Token - 计算 amr: {}", amr);
        }
        
        // 查询完整的 User 信息以获取 email、phone、nickname 等字段
        if (username != null && userRepository != null) {
            userRepository.findUserByUsername(username).ifPresent(user -> {
                // 根据请求的 scope 添加相应的字段
                Set<String> scopes = context.getAuthorizedScopes();
                
                // profile scope: 添加用户基本信息
                if (scopes != null && scopes.contains("profile")) {
                    if (user.getNickname() != null && !user.getNickname().isEmpty()) {
                        claims.claim("name", user.getNickname());
                        claims.claim("nickname", user.getNickname());
                    }
                }
                
                // email scope: 添加邮箱信息
                if (scopes != null && scopes.contains("email")) {
                    if (user.getEmail() != null && !user.getEmail().isEmpty()) {
                        claims.claim("email", user.getEmail());
                        // 注意：email_verified 需要根据实际业务逻辑判断
                        // 这里假设如果邮箱存在则认为已验证，实际应该从数据库字段判断
                        claims.claim("email_verified", true);
                    }
                }
                
                // phone scope: 添加手机号信息
                if (scopes != null && scopes.contains("phone")) {
                    if (user.getPhone() != null && !user.getPhone().isEmpty()) {
                        claims.claim("phone_number", user.getPhone());
                        // 注意：phone_number_verified 需要根据实际业务逻辑判断
                        claims.claim("phone_number_verified", true);
                    }
                }
            });
        }
    }
    
    /**
     * 获取用户认证时间
     * 
     * 当前实现使用当前时间作为 auth_time。
     * 
     * 改进方案：
     * 1. 在认证成功时（如 CustomLoginSuccessHandler），将 auth_time 存储到 
     *    OAuth2Authorization 的 attributes 中：
     *    ```java
     *    authorization.attribute("auth_time", Instant.now());
     *    ```
     * 2. 然后从 attributes 中读取：
     *    ```java
     *    Object authTime = authorization.getAttribute("auth_time");
     *    if (authTime instanceof Instant) {
     *        return (Instant) authTime;
     *    }
     *    ```
     * 
     * @param context JWT 编码上下文
     * @param principal 认证主体
     * @return 认证时间（当前使用当前时间）
     */
    private Instant getAuthTime(JwtEncodingContext context, Authentication principal) {
        // 尝试从 OAuth2Authorization 的 attributes 获取 auth_time
        OAuth2Authorization authorization = context.getAuthorization();
        if (authorization != null) {
            Object authTime = authorization.getAttribute("auth_time");
            if (authTime instanceof Instant) {
                return (Instant) authTime;
            }
        }
        
        // 如果无法获取，使用当前时间（作为后备方案）
        // TODO: 改进 - 在认证成功时将 auth_time 存储到 OAuth2Authorization.attributes 中
        return Instant.now();
    }
    
    /**
     * 获取认证方法引用（Authentication Method References）
     * 
     * 从 Authentication 对象中提取认证方法，如 password、totp 等
     * 
     * @param principal 认证主体
     * @return 认证方法列表，如 ["password", "totp"]
     */
    private List<String> getAuthenticationMethods(Authentication principal) {
        List<String> amr = new ArrayList<>();
        if (principal == null) {
            log.debug("[JwtTokenCustomizer] getAuthenticationMethods - principal 为 null, 返回空 amr");
            return amr;
        }
        log.debug("[JwtTokenCustomizer] getAuthenticationMethods - principalClass={}, authenticated={}",
                principal.getClass().getName(), principal.isAuthenticated());
        
        // 如果是 MultiFactorAuthenticationToken，从 completedFactors 获取
        if (principal instanceof MultiFactorAuthenticationToken mfaToken) {
            try {
                log.debug("[JwtTokenCustomizer] getAuthenticationMethods - principal 是 MultiFactorAuthenticationToken, completedFactors={}",
                        mfaToken.getCompletedFactors());
            } catch (Exception ignored) {
                // 日志失败不影响后续逻辑
            }
            mfaToken.getCompletedFactors().forEach(factor -> {
                String method = mapFactorToAmr(factor.name());
                if (method != null && !amr.contains(method)) {
                    amr.add(method);
                }
            });
        }
        
        // 如果 principal 已认证，至少添加一个认证方法
        if (principal.isAuthenticated() && amr.isEmpty()) {
            // 默认假设使用了密码认证
            amr.add("password");
        }
        
        return amr;
    }
    
    /**
     * 将认证因子类型映射到 AMR 值
     * 
     * @param factorName 认证因子名称（如 PASSWORD, TOTP）
     * @return AMR 值（如 password, totp），如果无法映射则返回 null
     */
    private String mapFactorToAmr(String factorName) {
        if (factorName == null) {
            return null;
        }
        
        return switch (factorName.toUpperCase()) {
            case "PASSWORD" -> "password";
            case "TOTP" -> "totp";
            case "OAUTH2" -> "oauth2";
            case "EMAIL" -> "email";
            case "MFA" -> "mfa";
            default -> null;
        };
    }
}
