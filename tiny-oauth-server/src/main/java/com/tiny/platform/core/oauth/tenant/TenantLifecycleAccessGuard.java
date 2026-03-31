package com.tiny.platform.core.oauth.tenant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.AuthenticationFactorAuthorities;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken;
import com.tiny.platform.infrastructure.auth.audit.domain.AuthorizationAuditEventType;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditService;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.core.exception.exception.NotFoundException;
import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 平台作用域下，按“目标租户”维度执行生命周期治理只读裁决。
 *
 * <p>入口过滤器只能感知 activeTenantId；当平台管理员以 platform scope 查询其他租户时，
 * 需要在 controller/service 层补一层 target-tenant 访问守卫。</p>
 */
@Component
public class TenantLifecycleAccessGuard {

    private static final String LIFECYCLE_FROZEN = "FROZEN";
    private static final String LIFECYCLE_DECOMMISSIONED = "DECOMMISSIONED";
    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String RESULT_DENIED = "DENIED";

    private final TenantRepository tenantRepository;
    private final TenantLifecycleReadPolicy tenantLifecycleReadPolicy;
    private final AuthorizationAuditService authorizationAuditService;
    private final ObjectMapper objectMapper;

    public TenantLifecycleAccessGuard(TenantRepository tenantRepository,
                                      TenantLifecycleReadPolicy tenantLifecycleReadPolicy,
                                      AuthorizationAuditService authorizationAuditService,
                                      ObjectMapper objectMapper) {
        this.tenantRepository = tenantRepository;
        this.tenantLifecycleReadPolicy = tenantLifecycleReadPolicy;
        this.authorizationAuditService = authorizationAuditService;
        this.objectMapper = objectMapper;
    }

    public void assertPlatformTargetTenantReadable(Long targetTenantId, String resourcePermission) {
        assertPlatformTargetTenantReadable(targetTenantId, resourcePermission, false, null, null);
    }

    public void assertPlatformTargetTenantReadable(Long targetTenantId,
                                                   String resourcePermission,
                                                   boolean highSensitivity,
                                                   String reason,
                                                   String ticketId) {
        if (targetTenantId == null || !TenantContext.isPlatformScope()) {
            return;
        }
        Tenant tenant = tenantRepository.findById(targetTenantId)
            .orElseThrow(() -> new NotFoundException("租户", targetTenantId));
        String lifecycleStatus = normalizeLifecycleStatus(tenant.getLifecycleStatus());
        if (!LIFECYCLE_FROZEN.equals(lifecycleStatus) && !LIFECYCLE_DECOMMISSIONED.equals(lifecycleStatus)) {
            return;
        }

        HttpServletRequest request = currentRequest()
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "缺少当前请求上下文，无法验证租户生命周期治理访问"));
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Optional<TenantLifecycleReadPolicy.AllowedReadAccess> allowedReadAccess =
            tenantLifecycleReadPolicy.resolve(request, lifecycleStatus);
        if (allowedReadAccess.isEmpty()) {
            audit(targetTenantId, lifecycleStatus, request, resourcePermission, RESULT_DENIED,
                "lifecycle_read_not_allowlisted", reason, ticketId, tenant, null);
            throw new BusinessException(
                ErrorCode.RESOURCE_STATE_INVALID,
                "租户处于 " + lifecycleStatus + " 状态，不允许访问该治理只读资源"
            );
        }
        if (!hasAnyAuthority(authentication, allowedReadAccess.get().requiredAuthorities())) {
            audit(targetTenantId, lifecycleStatus, request, resourcePermission, RESULT_DENIED,
                "missing_required_authority", reason, ticketId, tenant, allowedReadAccess.get().module());
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前权限不满足租户生命周期治理访问要求");
        }
        if (highSensitivity && LIFECYCLE_DECOMMISSIONED.equals(lifecycleStatus)) {
            if (!AuthenticationFactorAuthorities.hasFactor(authentication,
                MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP)) {
                audit(targetTenantId, lifecycleStatus, request, resourcePermission, RESULT_DENIED,
                    "mfa_required", reason, ticketId, tenant, allowedReadAccess.get().module());
                throw new BusinessException(ErrorCode.FORBIDDEN, "已下线租户的高敏感治理访问要求完成 MFA");
            }
            if (isBlank(reason) && isBlank(ticketId)) {
                audit(targetTenantId, lifecycleStatus, request, resourcePermission, RESULT_DENIED,
                    "reason_or_ticket_required", reason, ticketId, tenant, allowedReadAccess.get().module());
                throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    "已下线租户的高敏感治理访问必须提供 reason 或 ticketId"
                );
            }
        }

        audit(targetTenantId, lifecycleStatus, request, resourcePermission, RESULT_SUCCESS,
            null, reason, ticketId, tenant, allowedReadAccess.get().module());
    }

    private Optional<HttpServletRequest> currentRequest() {
        var requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return Optional.of(servletRequestAttributes.getRequest());
        }
        return Optional.empty();
    }

    private boolean hasAnyAuthority(Authentication authentication, java.util.Set<String> requiredAuthorities) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
            .map(grantedAuthority -> grantedAuthority.getAuthority())
            .anyMatch(requiredAuthorities::contains);
    }

    private void audit(Long targetTenantId,
                       String lifecycleStatus,
                       HttpServletRequest request,
                       String resourcePermission,
                       String result,
                       String resultReason,
                       String reason,
                       String ticketId,
                       Tenant tenant,
                       String module) {
        authorizationAuditService.log(
            AuthorizationAuditEventType.TENANT_LIFECYCLE_ALLOWLIST_ACCESS,
            targetTenantId,
            null,
            null,
            TenantContextContract.SCOPE_TYPE_PLATFORM,
            targetTenantId,
            module,
            resourcePermission,
            buildEventDetail(targetTenantId, lifecycleStatus, request, resourcePermission, reason, ticketId, tenant),
            result,
            resultReason
        );
    }

    private String buildEventDetail(Long targetTenantId,
                                    String lifecycleStatus,
                                    HttpServletRequest request,
                                    String resourcePermission,
                                    String reason,
                                    String ticketId,
                                    Tenant tenant) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("action", "TARGET_TENANT_LIFECYCLE_ALLOWLIST_READ");
        detail.put("tenant", Map.of(
            "id", targetTenantId,
            "code", tenant.getCode(),
            "name", tenant.getName(),
            "lifecycleStatus", lifecycleStatus
        ));
        detail.put("operator", buildOperatorDetail());
        detail.put("request", Map.of(
            "method", request.getMethod(),
            "path", request.getRequestURI()
        ));
        detail.put("resourcePermission", resourcePermission);
        if (!isBlank(reason)) {
            detail.put("reason", reason.trim());
        }
        if (!isBlank(ticketId)) {
            detail.put("ticketId", ticketId.trim());
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            return "{\"action\":\"TARGET_TENANT_LIFECYCLE_ALLOWLIST_READ\",\"tenantId\":" + targetTenantId
                + ",\"tenantLifecycleStatus\":\"" + lifecycleStatus + "\"}";
        }
    }

    private Map<String, Object> buildOperatorDetail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> operator = new LinkedHashMap<>();
        operator.put("scopeType", TenantContext.getActiveScopeType());
        if (authentication == null) {
            return operator;
        }
        operator.put("username", authentication.getName());
        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            operator.put("userId", securityUser.getUserId());
            operator.put("username", securityUser.getUsername());
        }
        return operator;
    }

    private String normalizeLifecycleStatus(String lifecycleStatus) {
        if (lifecycleStatus == null) {
            return "ACTIVE";
        }
        return lifecycleStatus.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
