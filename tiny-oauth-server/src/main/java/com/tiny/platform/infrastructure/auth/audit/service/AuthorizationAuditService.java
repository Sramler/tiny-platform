package com.tiny.platform.infrastructure.auth.audit.service;

import com.tiny.platform.infrastructure.auth.audit.domain.AuthorizationAuditLog;
import com.tiny.platform.infrastructure.auth.audit.domain.RequirementAwareAuditDetail;
import com.tiny.platform.infrastructure.auth.audit.repository.AuthorizationAuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 授权审计服务。
 *
 * <p>提供授权变更事件的记录和查询能力。审计日志写入使用独立事务
 * （{@link Propagation#REQUIRES_NEW}），确保即使业务事务回滚也能持久化审计记录。</p>
 *
 * <p>审计入口方法 {@link #log} 和便捷的 {@link #logAsync} 支持同步和异步两种写入模式。
 * 推荐在关键授权路径使用同步写入，在批量操作或非关键路径使用异步写入。</p>
 */
@Service
public class AuthorizationAuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationAuditService.class);

    private final AuthorizationAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuthorizationAuditService(AuthorizationAuditLogRepository auditLogRepository,
                                     ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 同步记录授权审计事件（使用独立事务）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuthorizationAuditLog log(String eventType, Long tenantId, Long targetUserId,
                                     Long roleId, String scopeType, Long scopeId,
                                     String module, String resourcePermission,
                                     String eventDetail, String result, String resultReason) {
        AuthorizationAuditLog entry = new AuthorizationAuditLog();
        entry.setTenantId(tenantId);
        entry.setEventType(eventType);
        entry.setActorUserId(extractCurrentUserId());
        entry.setTargetUserId(targetUserId);
        entry.setScopeType(scopeType);
        entry.setScopeId(scopeId);
        entry.setRoleId(roleId);
        entry.setModule(module);
        entry.setResourcePermission(resourcePermission);
        entry.setEventDetail(eventDetail);
        entry.setResult(result != null ? result : "SUCCESS");
        entry.setResultReason(resultReason);
        entry.setIpAddress(extractIpAddress());

        entry = auditLogRepository.save(entry);
        logger.debug("Authorization audit: type={}, tenant={}, actor={}, target={}, role={}, result={}",
            eventType, tenantId, entry.getActorUserId(), targetUserId, roleId, entry.getResult());
        return entry;
    }

    /**
     * 便捷方法：记录成功事件。
     */
    public AuthorizationAuditLog logSuccess(String eventType, Long tenantId, Long targetUserId,
                                            Long roleId, String scopeType, Long scopeId,
                                            String eventDetail) {
        return log(eventType, tenantId, targetUserId, roleId, scopeType, scopeId,
            null, null, eventDetail, "SUCCESS", null);
    }

    /**
     * 便捷方法：记录拒绝事件。
     */
    public AuthorizationAuditLog logDenied(String eventType, Long tenantId, Long targetUserId,
                                           Long roleId, String scopeType, Long scopeId,
                                           String eventDetail, String reason) {
        return log(eventType, tenantId, targetUserId, roleId, scopeType, scopeId,
            null, null, eventDetail, "DENIED", reason);
    }

    /**
     * 结构化 requirement-aware 授权审计写入。
     *
     * <p>审计失败应由调用方自行捕获，不能影响主授权结果。</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuthorizationAuditLog logRequirementAware(String eventType,
                                                      Long tenantId,
                                                      String module,
                                                      RequirementAwareAuditDetail detail) {
        String eventDetail;
        try {
            eventDetail = objectMapper.writeValueAsString(detail);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to serialize requirement-aware audit detail", ex);
        }
        String result = "ALLOW".equalsIgnoreCase(detail.decision()) ? "SUCCESS" : "DENIED";
        String topLevelPermission = extractTopLevelPermission(detail);
        return log(
            eventType,
            tenantId,
            null,
            null,
            null,
            null,
            module,
            topLevelPermission,
            eventDetail,
            result,
            detail.reason()
        );
    }

    private String extractTopLevelPermission(RequirementAwareAuditDetail detail) {
        if (detail == null) {
            return null;
        }
        if (detail.matchedPermissionCodes() != null && !detail.matchedPermissionCodes().isEmpty()) {
            return detail.matchedPermissionCodes().getFirst();
        }
        if (detail.missingPermissionCodes() != null && !detail.missingPermissionCodes().isEmpty()) {
            return detail.missingPermissionCodes().getFirst();
        }
        if (detail.negatedPermissionCodes() != null && !detail.negatedPermissionCodes().isEmpty()) {
            return detail.negatedPermissionCodes().getFirst();
        }
        return null;
    }

    /**
     * 异步记录审计事件（适用于批量操作或非关键路径）。
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAsync(String eventType, Long tenantId, Long targetUserId,
                         Long roleId, String scopeType, Long scopeId,
                         String module, String resourcePermission,
                         String eventDetail, String result, String resultReason) {
        try {
            log(eventType, tenantId, targetUserId, roleId, scopeType, scopeId,
                module, resourcePermission, eventDetail, result, resultReason);
        } catch (Exception e) {
            logger.warn("Failed to write async audit log: type={}, tenant={}, error={}",
                eventType, tenantId, e.getMessage());
        }
    }

    // ==================== 查询 ====================

    public Page<AuthorizationAuditLog> search(AuthorizationAuditQuery query, Pageable pageable) {
        return auditLogRepository.findAll(buildSpecification(query), pageable);
    }

    @Transactional(readOnly = true)
    public AuthorizationAuditSummary summarize(AuthorizationAuditQuery query) {
        List<AuthorizationAuditLog> matched = auditLogRepository.findAll(
            buildSpecification(query),
            Sort.by(Sort.Direction.DESC, "createdAt")
        );
        long totalCount = matched.size();
        long successCount = matched.stream().filter(log -> "SUCCESS".equalsIgnoreCase(log.getResult())).count();
        long deniedCount = matched.stream().filter(log -> "DENIED".equalsIgnoreCase(log.getResult())).count();
        List<AuthorizationAuditSummary.EventTypeCount> eventTypeCounts = matched.stream()
            .collect(Collectors.groupingBy(
                log -> log.getEventType() == null ? "UNKNOWN" : log.getEventType(),
                Collectors.counting()
            ))
            .entrySet()
            .stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .map(entry -> new AuthorizationAuditSummary.EventTypeCount(entry.getKey(), entry.getValue()))
            .toList();
        return new AuthorizationAuditSummary(totalCount, successCount, deniedCount, eventTypeCounts);
    }

    public Page<AuthorizationAuditLog> listByTenant(Long tenantId, Pageable pageable) {
        return auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
    }

    public Page<AuthorizationAuditLog> listByTenantAndEventType(Long tenantId, String eventType, Pageable pageable) {
        return auditLogRepository.findByTenantIdAndEventTypeOrderByCreatedAtDesc(tenantId, eventType, pageable);
    }

    public List<AuthorizationAuditLog> listByTargetUser(Long targetUserId, Long tenantId) {
        return auditLogRepository.findByTargetUserIdAndTenantIdOrderByCreatedAtDesc(targetUserId, tenantId);
    }

    /**
     * 清理指定天数之前的审计日志。
     */
    @Transactional
    public int purge(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int deleted = auditLogRepository.deleteByCreatedAtBefore(cutoff);
        logger.info("Purged {} authorization audit log entries older than {} days", deleted, retentionDays);
        return deleted;
    }

    private Specification<AuthorizationAuditLog> buildSpecification(AuthorizationAuditQuery query) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (query == null) {
                return criteriaBuilder.conjunction();
            }
            if (query.tenantId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("tenantId"), query.tenantId()));
            }
            if (query.eventType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("eventType"), query.eventType()));
            }
            if (query.actorUserId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("actorUserId"), query.actorUserId()));
            }
            if (query.targetUserId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("targetUserId"), query.targetUserId()));
            }
            if (query.result() != null) {
                predicates.add(criteriaBuilder.equal(root.get("result"), query.result()));
            }
            if (query.resourcePermission() != null) {
                predicates.add(criteriaBuilder.equal(root.get("resourcePermission"), query.resourcePermission()));
            }
            if (query.detailReason() != null) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("eventDetail")),
                    "%\"reason\":\"" + escapeLike(query.detailReason().toLowerCase()) + "\"%"
                ));
            }
            if (query.carrierType() != null) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("eventDetail")),
                    "%\"carrierType\":\"" + escapeLike(query.carrierType().toLowerCase()) + "\"%"
                ));
            }
            if (query.requirementGroup() != null) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("eventDetail")),
                    "%\"requirementGroup\":" + query.requirementGroup() + "%"
                ));
            }
            if (query.decision() != null) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("eventDetail")),
                    "%\"decision\":\"" + escapeLike(query.decision().toLowerCase()) + "\"%"
                ));
            }
            if (query.startTime() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), query.startTime()));
            }
            if (query.endTime() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), query.endTime()));
            }
            return predicates.isEmpty()
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private String escapeLike(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }

    private Long extractCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
            && auth.getPrincipal() instanceof com.tiny.platform.core.oauth.model.SecurityUser securityUser) {
            return securityUser.getUserId();
        }
        return null;
    }

    private String extractIpAddress() {
        try {
            ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isEmpty()) {
                    return forwarded.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
