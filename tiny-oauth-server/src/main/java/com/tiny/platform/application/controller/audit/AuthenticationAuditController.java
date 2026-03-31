package com.tiny.platform.application.controller.audit;

import com.tiny.platform.core.oauth.service.AuthenticationAuditQuery;
import com.tiny.platform.core.oauth.service.AuthenticationAuditService;
import com.tiny.platform.core.oauth.service.AuthenticationAuditSummary;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantLifecycleAccessGuard;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationAudit;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 认证审计日志查询控制器。
 */
@RestController
@RequestMapping("/sys/audit/authentication")
public class AuthenticationAuditController {

    private final AuthenticationAuditService authenticationAuditService;
    private final TenantLifecycleAccessGuard tenantLifecycleAccessGuard;

    public AuthenticationAuditController(AuthenticationAuditService authenticationAuditService,
                                         TenantLifecycleAccessGuard tenantLifecycleAccessGuard) {
        this.authenticationAuditService = authenticationAuditService;
        this.tenantLifecycleAccessGuard = tenantLifecycleAccessGuard;
    }

    @GetMapping
    @PreAuthorize("@authenticationAuditAccessGuard.canView(authentication)")
    public ResponseEntity<Page<AuthenticationAuditRecord>> list(
        @RequestParam(required = false) Long tenantId,
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) String username,
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false) Boolean success,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        validateTimeRange(startTime, endTime);
        Long resolvedTenantId = resolveTenantId(tenantId);
        tenantLifecycleAccessGuard.assertPlatformTargetTenantReadable(
            resolvedTenantId,
            "system:audit:authentication:view"
        );
        AuthenticationAuditQuery query = new AuthenticationAuditQuery(
            resolvedTenantId,
            userId,
            normalizeBlank(username),
            normalizeEventType(eventType),
            success,
            startTime,
            endTime
        );
        return ResponseEntity.ok(
            authenticationAuditService.search(query, pageable).map(AuthenticationAuditRecord::from)
        );
    }

    @GetMapping("/export")
    @PreAuthorize("@authenticationAuditAccessGuard.canExport(authentication)")
    public ResponseEntity<StreamingResponseBody> export(
        @RequestParam(required = false) Long tenantId,
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) String username,
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false) Boolean success,
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
            "system:audit:authentication:export",
            true,
            reason,
            ticketId
        );
        AuthenticationAuditQuery query = new AuthenticationAuditQuery(
            resolvedTenantId,
            userId,
            normalizeBlank(username),
            normalizeEventType(eventType),
            success,
            startTime,
            endTime
        );
        Page<UserAuthenticationAudit> page = authenticationAuditService.search(
            query,
            PageRequest.of(0, AuditCsvExportSupport.EXPORT_MAX_ROWS + 1, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        if (page.getTotalElements() > AuditCsvExportSupport.EXPORT_MAX_ROWS) {
            throw new BusinessException(
                ErrorCode.INVALID_PARAMETER,
                "导出记录数超过 10000 条上限，请缩小筛选范围后重试"
            );
        }
        List<UserAuthenticationAudit> rows = page.getContent();
        StreamingResponseBody body = outputStream -> {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
                AuditCsvExportSupport.writeBom(writer);
                AuditCsvExportSupport.writeRow(
                    writer,
                    "ID",
                    "TenantId",
                    "UserId",
                    "Username",
                    "EventType",
                    "Success",
                    "AuthenticationProvider",
                    "AuthenticationFactor",
                    "IpAddress",
                    "UserAgent",
                    "SessionId",
                    "TokenId",
                    "TenantResolutionCode",
                    "TenantResolutionSource",
                    "CreatedAt"
                );
                for (UserAuthenticationAudit row : rows) {
                    AuditCsvExportSupport.writeRow(
                        writer,
                        row.getId(),
                        row.getTenantId(),
                        row.getUserId(),
                        row.getUsername(),
                        row.getEventType(),
                        row.getSuccess(),
                        row.getAuthenticationProvider(),
                        row.getAuthenticationFactor(),
                        row.getIpAddress(),
                        row.getUserAgent(),
                        row.getSessionId(),
                        row.getTokenId(),
                        row.getTenantResolutionCode(),
                        row.getTenantResolutionSource(),
                        row.getCreatedAt()
                    );
                }
                writer.flush();
            }
        };
        return ResponseEntity.ok()
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                AuditCsvExportSupport.buildAttachmentHeader("authentication-audit"))
            .contentType(AuditCsvExportSupport.CSV_MEDIA_TYPE)
            .body(body);
    }

    @GetMapping("/summary")
    @PreAuthorize("@authenticationAuditAccessGuard.canView(authentication)")
    public ResponseEntity<AuthenticationAuditSummary> summary(
        @RequestParam(required = false) Long tenantId,
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) String username,
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false) Boolean success,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime
    ) {
        validateTimeRange(startTime, endTime);
        Long resolvedTenantId = resolveTenantId(tenantId);
        tenantLifecycleAccessGuard.assertPlatformTargetTenantReadable(
            resolvedTenantId,
            "system:audit:authentication:view"
        );
        AuthenticationAuditQuery query = new AuthenticationAuditQuery(
            resolvedTenantId,
            userId,
            normalizeBlank(username),
            normalizeEventType(eventType),
            success,
            startTime,
            endTime
        );
        return ResponseEntity.ok(authenticationAuditService.summarize(query));
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
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "当前作用域不允许查询其他租户的认证审计");
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

    private String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record AuthenticationAuditRecord(
        Long id,
        Long tenantId,
        Long userId,
        String username,
        String eventType,
        Boolean success,
        String authenticationProvider,
        String authenticationFactor,
        String ipAddress,
        String userAgent,
        String sessionId,
        String tokenId,
        String tenantResolutionCode,
        String tenantResolutionSource,
        LocalDateTime createdAt
    ) {
        static AuthenticationAuditRecord from(UserAuthenticationAudit audit) {
            return new AuthenticationAuditRecord(
                audit.getId(),
                audit.getTenantId(),
                audit.getUserId(),
                audit.getUsername(),
                audit.getEventType(),
                audit.getSuccess(),
                audit.getAuthenticationProvider(),
                audit.getAuthenticationFactor(),
                audit.getIpAddress(),
                audit.getUserAgent(),
                audit.getSessionId(),
                audit.getTokenId(),
                audit.getTenantResolutionCode(),
                audit.getTenantResolutionSource(),
                audit.getCreatedAt()
            );
        }
    }
}
