package com.tiny.platform.core.oauth.service.impl;

import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationAudit;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationAuditRepository;
import com.tiny.platform.core.oauth.service.AuthenticationAuditService;
import com.tiny.platform.infrastructure.core.util.IpUtils;
import com.tiny.platform.core.oauth.tenant.ActiveTenantResponseSupport;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 认证审计服务实现
 */
@Service
public class AuthenticationAuditServiceImpl implements AuthenticationAuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationAuditServiceImpl.class);
    private static final String TENANT_RESOLUTION_RESOLVED = "resolved";
    private static final String TENANT_RESOLUTION_MISSING = "tenant_context_missing";

    private final UserAuthenticationAuditRepository auditRepository;

    public AuthenticationAuditServiceImpl(UserAuthenticationAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEvent(String username, Long userId, String eventType, boolean success,
                           String authenticationProvider, String authenticationFactor,
                           HttpServletRequest request) {
        try {
            UserAuthenticationAudit audit = new UserAuthenticationAudit();
            audit.setUsername(username != null ? username : "unknown");
            audit.setUserId(userId);
            TenantResolution tenantResolution = resolveActiveTenant(request);
            audit.setTenantId(tenantResolution.activeTenantId());
            audit.setTenantResolutionCode(tenantResolution.code());
            audit.setTenantResolutionSource(tenantResolution.source());
            audit.setEventType(eventType);
            audit.setSuccess(success);
            audit.setAuthenticationProvider(authenticationProvider);
            audit.setAuthenticationFactor(authenticationFactor);

            if (request != null) {
                audit.setIpAddress(IpUtils.getClientIp(request));
                audit.setUserAgent(request.getHeader("User-Agent"));
                
                HttpSession session = request.getSession(false);
                if (session != null) {
                    audit.setSessionId(session.getId());
                }
            }

            auditRepository.save(audit);
            
            logger.debug("认证审计记录已保存: username={}, eventType={}, success={}", 
                    username, eventType, success);
        } catch (Exception e) {
            // 审计记录失败不应该影响主业务流程，只记录日志
            logger.error("记录认证审计失败: username={}, eventType={}, error={}", 
                    username, eventType, e.getMessage(), e);
        }
    }

    @Override
    public void recordLoginSuccess(String username, Long userId, String authenticationProvider,
                                  String authenticationFactor, HttpServletRequest request) {
        recordEvent(username, userId, "LOGIN", true, authenticationProvider, 
                   authenticationFactor, request);
    }

    @Override
    public void recordLoginFailure(String username, Long userId, String authenticationProvider,
                                  String authenticationFactor, HttpServletRequest request) {
        recordEvent(username, userId, "LOGIN", false, authenticationProvider,
                   authenticationFactor, request);
    }

    @Override
    public void recordLogout(String username, Long userId, HttpServletRequest request) {
        recordEvent(username, userId, "LOGOUT", true, null, null, request);
    }

    @Override
    public void recordMfaBind(String username, Long userId, String authenticationFactor,
                              HttpServletRequest request) {
        recordEvent(username, userId, "MFA_BIND", true, "LOCAL", 
                   authenticationFactor, request);
    }

    @Override
    public void recordMfaUnbind(String username, Long userId, String authenticationFactor,
                               HttpServletRequest request) {
        recordEvent(username, userId, "MFA_UNBIND", true, "LOCAL",
                   authenticationFactor, request);
    }

    @Override
    public void recordTokenIssue(String username, Long userId, String tokenId,
                                HttpServletRequest request) {
        UserAuthenticationAudit audit = new UserAuthenticationAudit();
        audit.setUsername(username != null ? username : "unknown");
        audit.setUserId(userId);
        TenantResolution tenantResolution = resolveActiveTenant(request);
        audit.setTenantId(tenantResolution.activeTenantId());
        audit.setTenantResolutionCode(tenantResolution.code());
        audit.setTenantResolutionSource(tenantResolution.source());
        audit.setEventType("TOKEN_ISSUE");
        audit.setSuccess(true);
        audit.setTokenId(tokenId);

        if (request != null) {
            audit.setIpAddress(IpUtils.getClientIp(request));
            audit.setUserAgent(request.getHeader("User-Agent"));
        }

        try {
            auditRepository.save(audit);
        } catch (Exception e) {
            logger.error("记录Token颁发审计失败: username={}, error={}", username, e.getMessage(), e);
        }
    }

    @Override
    public void recordTokenRevoke(String username, Long userId, String tokenId,
                                 HttpServletRequest request) {
        UserAuthenticationAudit audit = new UserAuthenticationAudit();
        audit.setUsername(username != null ? username : "unknown");
        audit.setUserId(userId);
        TenantResolution tenantResolution = resolveActiveTenant(request);
        audit.setTenantId(tenantResolution.activeTenantId());
        audit.setTenantResolutionCode(tenantResolution.code());
        audit.setTenantResolutionSource(tenantResolution.source());
        audit.setEventType("TOKEN_REVOKE");
        audit.setSuccess(true);
        audit.setTokenId(tokenId);

        if (request != null) {
            audit.setIpAddress(IpUtils.getClientIp(request));
            audit.setUserAgent(request.getHeader("User-Agent"));
        }

        try {
            auditRepository.save(audit);
        } catch (Exception e) {
            logger.error("记录Token撤销审计失败: username={}, error={}", username, e.getMessage(), e);
        }
    }

    private TenantResolution resolveActiveTenant(HttpServletRequest request) {
        Long activeTenantId = TenantContext.getActiveTenantId();
        if (activeTenantId == null || activeTenantId <= 0) {
            activeTenantId = ActiveTenantResponseSupport.resolveActiveTenantId(
                    SecurityContextHolder.getContext().getAuthentication()
            );
        }
        String tenantSource = resolveTenantSource(request);
        if (activeTenantId == null || activeTenantId <= 0) {
            return new TenantResolution(null, TENANT_RESOLUTION_MISSING, tenantSource);
        }
        return new TenantResolution(activeTenantId, TENANT_RESOLUTION_RESOLVED, tenantSource);
    }

    private String resolveTenantSource(HttpServletRequest request) {
        if (request != null) {
            Object sourceAttribute = request.getAttribute(TenantContextFilter.TENANT_SOURCE_REQUEST_ATTRIBUTE);
            if (sourceAttribute instanceof String source && !source.isBlank()) {
                return source;
            }
        }
        String sourceFromContext = TenantContext.getTenantSource();
        if (sourceFromContext == null || sourceFromContext.isBlank()) {
            return TenantContext.SOURCE_UNKNOWN;
        }
        return sourceFromContext;
    }

    private record TenantResolution(Long activeTenantId, String code, String source) {}
}
