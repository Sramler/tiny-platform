package com.tiny.platform.application.controller.audit;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantLifecycleAccessGuard;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditQuery;
import com.tiny.platform.infrastructure.auth.audit.domain.AuthorizationAuditLog;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditService;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditSummary;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 授权审计日志查询控制器。
 *
 * <p>提供租户内审计日志的分页查询、按事件类型过滤、按目标用户查询，
 * 以及审计日志清理（retention）能力。</p>
 */
@RestController
@RequestMapping("/sys/audit/authorization")
public class AuthorizationAuditController {

    private final AuthorizationAuditService auditService;
    private final TenantLifecycleAccessGuard tenantLifecycleAccessGuard;

    public AuthorizationAuditController(AuthorizationAuditService auditService,
                                        TenantLifecycleAccessGuard tenantLifecycleAccessGuard) {
        this.auditService = auditService;
        this.tenantLifecycleAccessGuard = tenantLifecycleAccessGuard;
    }

    @GetMapping
    @PreAuthorize("@authorizationAuditAccessGuard.canView(authentication)")
    public ResponseEntity<Page<AuthorizationAuditLog>> list(
        @RequestParam(required = false) Long tenantId,
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false) Long actorUserId,
        @RequestParam(required = false) Long targetUserId,
        @RequestParam(required = false) String result,
        @RequestParam(required = false) String resourcePermission,
        @RequestParam(required = false) String detailReason,
        @RequestParam(required = false) String carrierType,
        @RequestParam(required = false) Integer requirementGroup,
        @RequestParam(required = false) String decision,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        validateTimeRange(startTime, endTime);
        Long resolvedTenantId = resolveTenantId(tenantId);
        tenantLifecycleAccessGuard.assertPlatformTargetTenantReadable(resolvedTenantId, "system:audit:auth:view");
        AuthorizationAuditQuery query = new AuthorizationAuditQuery(
            resolvedTenantId,
            normalizeEventType(eventType),
            actorUserId,
            targetUserId,
            normalizeResult(result),
            normalizeBlank(resourcePermission),
            normalizeBlank(detailReason),
            normalizeBlank(carrierType),
            requirementGroup,
            normalizeBlank(decision),
            startTime,
            endTime
        );
        return ResponseEntity.ok(auditService.search(query, pageable));
    }

    @GetMapping("/export")
    @PreAuthorize("@authorizationAuditAccessGuard.canExport(authentication)")
    public ResponseEntity<StreamingResponseBody> export(
        @RequestParam(required = false) Long tenantId,
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false) Long actorUserId,
        @RequestParam(required = false) Long targetUserId,
        @RequestParam(required = false) String result,
        @RequestParam(required = false) String resourcePermission,
        @RequestParam(required = false) String detailReason,
        @RequestParam(required = false) String carrierType,
        @RequestParam(required = false) Integer requirementGroup,
        @RequestParam(required = false) String decision,
        @RequestParam(required = false) String reason,
        @RequestParam(required = false) String ticketId,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime
    ) {
        validateTimeRange(startTime, endTime);
        Long resolvedTenantId = resolveTenantId(tenantId);
        tenantLifecycleAccessGuard.assertPlatformTargetTenantReadable(
            resolvedTenantId,
            "system:audit:auth:export",
            true,
            reason,
            ticketId
        );
        AuthorizationAuditQuery query = new AuthorizationAuditQuery(
            resolvedTenantId,
            normalizeEventType(eventType),
            actorUserId,
            targetUserId,
            normalizeResult(result),
            normalizeBlank(resourcePermission),
            normalizeBlank(detailReason),
            normalizeBlank(carrierType),
            requirementGroup,
            normalizeBlank(decision),
            startTime,
            endTime
        );
        Page<AuthorizationAuditLog> page = auditService.search(
            query,
            PageRequest.of(0, AuditCsvExportSupport.EXPORT_MAX_ROWS + 1, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        if (page.getTotalElements() > AuditCsvExportSupport.EXPORT_MAX_ROWS) {
            throw new BusinessException(
                ErrorCode.INVALID_PARAMETER,
                "导出记录数超过 10000 条上限，请缩小筛选范围后重试"
            );
        }
        List<AuthorizationAuditLog> rows = page.getContent();
        StreamingResponseBody body = outputStream -> {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
                AuditCsvExportSupport.writeBom(writer);
                AuditCsvExportSupport.writeRow(
                    writer,
                    "ID",
                    "TenantId",
                    "EventType",
                    "ActorUserId",
                    "TargetUserId",
                    "ScopeType",
                    "ScopeId",
                    "RoleId",
                    "Module",
                    "ResourcePermission",
                    "Result",
                    "ResultReason",
                    "IpAddress",
                    "CreatedAt",
                    "EventDetail"
                );
                for (AuthorizationAuditLog row : rows) {
                    AuditCsvExportSupport.writeRow(
                        writer,
                        row.getId(),
                        row.getTenantId(),
                        row.getEventType(),
                        row.getActorUserId(),
                        row.getTargetUserId(),
                        row.getScopeType(),
                        row.getScopeId(),
                        row.getRoleId(),
                        row.getModule(),
                        row.getResourcePermission(),
                        row.getResult(),
                        row.getResultReason(),
                        row.getIpAddress(),
                        row.getCreatedAt(),
                        row.getEventDetail()
                    );
                }
                writer.flush();
            }
        };
        return ResponseEntity.ok()
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                AuditCsvExportSupport.buildAttachmentHeader("authorization-audit"))
            .contentType(AuditCsvExportSupport.CSV_MEDIA_TYPE)
            .body(body);
    }

    @GetMapping("/summary")
    @PreAuthorize("@authorizationAuditAccessGuard.canView(authentication)")
    public ResponseEntity<AuthorizationAuditSummary> summary(
        @RequestParam(required = false) Long tenantId,
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false) Long actorUserId,
        @RequestParam(required = false) Long targetUserId,
        @RequestParam(required = false) String result,
        @RequestParam(required = false) String resourcePermission,
        @RequestParam(required = false) String detailReason,
        @RequestParam(required = false) String carrierType,
        @RequestParam(required = false) Integer requirementGroup,
        @RequestParam(required = false) String decision,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime
    ) {
        validateTimeRange(startTime, endTime);
        Long resolvedTenantId = resolveTenantId(tenantId);
        tenantLifecycleAccessGuard.assertPlatformTargetTenantReadable(resolvedTenantId, "system:audit:auth:view");
        AuthorizationAuditQuery query = new AuthorizationAuditQuery(
            resolvedTenantId,
            normalizeEventType(eventType),
            actorUserId,
            targetUserId,
            normalizeResult(result),
            normalizeBlank(resourcePermission),
            normalizeBlank(detailReason),
            normalizeBlank(carrierType),
            requirementGroup,
            normalizeBlank(decision),
            startTime,
            endTime
        );
        return ResponseEntity.ok(auditService.summarize(query));
    }

    @GetMapping("/by-event-type")
    @PreAuthorize("@authorizationAuditAccessGuard.canView(authentication)")
    public ResponseEntity<Page<AuthorizationAuditLog>> listByEventType(
        @RequestParam String eventType,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        Long tenantId = requireTenantId();
        return ResponseEntity.ok(auditService.listByTenantAndEventType(tenantId, eventType,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @GetMapping("/by-user/{userId}")
    @PreAuthorize("@authorizationAuditAccessGuard.canView(authentication)")
    public ResponseEntity<List<AuthorizationAuditLog>> listByTargetUser(@PathVariable Long userId) {
        Long tenantId = requireTenantId();
        return ResponseEntity.ok(auditService.listByTargetUser(userId, tenantId));
    }

    @DeleteMapping("/purge")
    @PreAuthorize("@authorizationAuditAccessGuard.canPurge(authentication)")
    public ResponseEntity<Map<String, Object>> purge(
        @RequestParam(defaultValue = "90") int retentionDays) {
        int deleted = auditService.purge(retentionDays);
        return ResponseEntity.ok(Map.of("deleted", deleted, "retentionDays", retentionDays));
    }

    private Long resolveTenantId(Long requestedTenantId) {
        if (TenantContext.isPlatformScope()) {
            return requestedTenantId;
        }
        Long activeTenantId = TenantContext.getActiveTenantId();
        if (activeTenantId == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "缺少租户上下文");
        }
        if (requestedTenantId != null && !requestedTenantId.equals(activeTenantId)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "当前作用域不允许查询其他租户的授权审计");
        }
        return activeTenantId;
    }

    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "开始时间不能晚于结束时间");
        }
    }

    private String normalizeEventType(String eventType) {
        String normalized = normalizeBlank(eventType);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeResult(String result) {
        String normalized = normalizeBlank(result);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getActiveTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "缺少租户上下文");
        }
        return tenantId;
    }
}
