package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.config.CustomWebAuthenticationDetailsSource;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * 多因素认证（MFA）会话升级工具类
 * <p>
 * 支持将当前认证升级为完全认证状态，并持久化 SecurityContext。
 * 提供防止 session fixation 攻击的保护机制。
 */
@Component
public class MultiFactorAuthenticationSessionManager {

    private static final Logger logger = LoggerFactory.getLogger(MultiFactorAuthenticationSessionManager.class);

    private final UserDetailsService userDetailsService;
    private final SecurityContextRepository securityContextRepository;

    public MultiFactorAuthenticationSessionManager(UserDetailsService userDetailsService,
                                                    SecurityContextRepository securityContextRepository) {
        this.userDetailsService = userDetailsService;
        // Fallback：如果 securityContextRepository 为 null，使用默认的 HttpSessionSecurityContextRepository
        // 虽然 Spring 依赖注入通常不会注入 null，但作为防御性编程，提供 fallback
        this.securityContextRepository = securityContextRepository != null
                ? securityContextRepository
                : new HttpSessionSecurityContextRepository();
    }

    /**
     * 升级当前会话为完全认证（完成所有 MFA 因子）
     * 不依赖 HttpServletRequest，仅更新 SecurityContextHolder
     *
     * @param user 当前用户对象
     */
    public void promoteToFullyAuthenticated(User user) {
        promoteToFullyAuthenticated(user, null, null);
    }

    /**
     * 升级当前会话为完全认证（完成所有 MFA 因子）
     * 可选提供 HttpServletRequest，将 SecurityContext 持久化到 HttpSession
     * <p>
     * 兼容重载：如果没有 response，会把 SecurityContext 写入 session attribute。
     * 不如使用 {@link #promoteToFullyAuthenticated(User, HttpServletRequest, HttpServletResponse)} 正规
     * （无法让 SecurityContextRepository 做附加操作），但保持兼容。
     *
     * @param user 当前用户对象
     * @param request HttpServletRequest，可为空
     */
    public void promoteToFullyAuthenticated(User user, HttpServletRequest request) {
        promoteToFullyAuthenticated(user, request, null);
    }

    /**
     * 将当前会话升级为完全认证（例如完成 PASSWORD + TOTP）。
     * <p>
     * 推荐在 Controller 中使用此方法（传入 request + response）。
     * <p>
     * 功能：
     * <ul>
     *   <li>创建已认证的 {@link MultiFactorAuthenticationToken}（包含 PASSWORD + TOTP 因子）</li>
     *   <li>防止 session fixation：优先 changeSessionId（容错）</li>
     *   <li>将 auth 放入新的 SecurityContext，并写入 SecurityContextHolder</li>
     *   <li>持久化到 session（使用 SecurityContextRepository，更规范）</li>
     * </ul>
     *
     * @param user 当前用户对象
     * @param request HttpServletRequest，可为空
     * @param response HttpServletResponse，可为空（如果为空，使用 fallback 方式）
     */
    public void promoteToFullyAuthenticated(User user,
                                            HttpServletRequest request,
                                            HttpServletResponse response) {
        // 从 SecurityContextHolder 获取当前认证
        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
        promoteToFullyAuthenticated(user, currentAuth, request, response);
    }

    /**
     * 将当前会话升级为完全认证（例如完成 PASSWORD + TOTP）。
     * <p>
     * ✨ 改进版：接受当前 Authentication 参数，如果当前是 {@link MultiFactorAuthenticationToken}，
     * 则继承其 provider、completedFactors 和 details，确保信息不丢失。
     * <p>
     * 功能：
     * <ul>
     *   <li>如果当前是 {@link MultiFactorAuthenticationToken}，调用其 {@code promoteToFullyAuthenticated} 方法</li>
     *   <li>否则创建新的 Token（fallback 场景）</li>
     *   <li>防止 session fixation：优先 changeSessionId（容错）</li>
     *   <li>将 auth 放入新的 SecurityContext，并写入 SecurityContextHolder</li>
     *   <li>持久化到 session（使用 SecurityContextRepository，更规范）</li>
     * </ul>
     *
     * @param user 当前用户对象
     * @param currentAuth 当前认证对象，可为空（如果为空，从 SecurityContextHolder 获取）
     * @param request HttpServletRequest，可为空
     * @param response HttpServletResponse，可为空（如果为空，使用 fallback 方式）
     */
    public void promoteToFullyAuthenticated(User user,
                                            Authentication currentAuth,
                                            HttpServletRequest request,
                                            HttpServletResponse response) {
        if (user == null) {
            return;
        }

        // 如果 currentAuth 为空，从 SecurityContextHolder 获取
        if (currentAuth == null) {
            currentAuth = SecurityContextHolder.getContext().getAuthentication();
        }

        try {
            // 1) 加载 UserDetails
            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());

            var authorities = userDetails.getAuthorities() == null
                    ? Collections.<org.springframework.security.core.GrantedAuthority>emptyList()
                    : List.copyOf(userDetails.getAuthorities());

            // 2) 创建已认证的 MultiFactorAuthenticationToken
            MultiFactorAuthenticationToken authenticated;
            
            if (currentAuth instanceof MultiFactorAuthenticationToken mfaToken) {
                // ✨ 正确做法：调用 token 的提升方法（期望其继承 provider/completedFactors/details）
                authenticated = mfaToken.promoteToFullyAuthenticated(authorities);
                
                // 防护：如果 promote 方法没有设置 authenticated/清空凭证/拷贝 details，补上
                // 注意：虽然 promoteToFullyAuthenticated 使用已认证构造函数（会自动设置 authenticated=true），
                // 但作为防御性编程，显式检查确保状态正确
                if (!authenticated.isAuthenticated()) {
                    authenticated.setAuthenticated(true);
                }
                
                // 确保 credentials 被清空（虽然构造函数已传入 null，但显式调用 eraseCredentials
                // 可以确保父类的清理逻辑也被执行）
                authenticated.eraseCredentials();
                
                // 如果 promoteToFullyAuthenticated 没有拷贝 details，则从原 auth 补充
                // 注意：promoteToFullyAuthenticated 已经处理了 details 拷贝，但作为防御性检查保留
                if (authenticated.getDetails() == null && mfaToken.getDetails() != null) {
                    authenticated.setDetails(mfaToken.getDetails());
                }
            } else {
                MultiFactorAuthenticationToken.AuthenticationProviderType providerEnum =
                        resolveProvider(currentAuth)
                                .orElse(MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL);

                EnumSet<MultiFactorAuthenticationToken.AuthenticationFactorType> completedFactors =
                        EnumSet.of(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD);

                MultiFactorAuthenticationToken provisional = new MultiFactorAuthenticationToken(
                        user.getUsername(),
                        null,
                        providerEnum,
                        completedFactors,
                        authorities
                );

                authenticated = provisional.promoteToFullyAuthenticated(authorities);
                authenticated.eraseCredentials();

                if (authenticated.getDetails() == null && currentAuth != null && currentAuth.getDetails() != null) {
                    authenticated.setDetails(currentAuth.getDetails());
                }
            }

            // 3) 防止 session fixation：优先确保 session，然后 changeSessionId（容错）
            if (request != null) {
                try {
                    // 确保有 session（这一步会创建 session）
                    request.getSession(true);
                    try {
                        // changeSessionId 通常要求 session 已存在
                        request.changeSessionId();
                    } catch (Throwable t) {
                        logger.debug("changeSessionId not supported/failed: {}", t.getMessage());
                    }
                } catch (Throwable t) {
                    logger.debug("ensure session failed (ignored): {}", t.getMessage());
                }
            }

            // 4) 将 auth 放入新的 SecurityContext，并写入 holder
            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(authenticated);
            SecurityContextHolder.setContext(ctx);

            // 记录认证升级成功（用于审计）
            logger.info("User {} promoted to fully authenticated", user.getUsername());

            // 5) 持久化到 session（使用 SecurityContextRepository，更规范）
            try {
                if (request != null && response != null && securityContextRepository != null) {
                    securityContextRepository.saveContext(ctx, request, response);
                    logger.debug("promoted user={} to fully authenticated (using SecurityContextRepository)", user.getUsername());
                } else if (request != null) {
                    // Fallback：直接把 SecurityContext 写入 session
                    // （与 HttpSessionSecurityContextRepository 的 key 一致）
                    HttpSession session = request.getSession(true);
                    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
                    logger.debug("promoted user={} to fully authenticated (using fallback session attribute)", user.getUsername());
                } else {
                    logger.debug("promoted user={} to fully authenticated (SecurityContextHolder only)", user.getUsername());
                }
            } catch (Exception e) {
                logger.warn("failed to persist security context for user={}, ex: {}", user.getUsername(), e.getMessage());
            }
        } catch (Exception ex) {
            logger.warn("promoteToFullyAuthenticated failed for user={}, ex: {}", 
                    user == null ? "null" : user.getUsername(), ex.getMessage(), ex);
            // 不要抛异常影响用户流程
        }
    }

    private java.util.Optional<MultiFactorAuthenticationToken.AuthenticationProviderType> resolveProvider(Authentication currentAuth) {
        if (currentAuth == null) {
            return java.util.Optional.empty();
        }
        Object details = currentAuth.getDetails();
        if (details instanceof CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails customDetails) {
            String provider = customDetails.getAuthenticationProvider();
            if (provider != null) {
                return java.util.Optional.of(MultiFactorAuthenticationToken.AuthenticationProviderType.from(provider));
            }
        }
        return java.util.Optional.empty();
    }
}
