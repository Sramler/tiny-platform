package com.tiny.platform.application.controller.idempotent.controller;

import com.tiny.platform.infrastructure.core.exception.base.BaseExceptionHandler;
import com.tiny.platform.infrastructure.idempotent.metrics.IdempotentMetricsService;
import com.tiny.platform.infrastructure.idempotent.metrics.IdempotentMetricsSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 幂等性监控/统计接口
 * 
 * <p>提供统计指标、热点 Key 等监控接口</p>
 * 
 * @author Auto Generated
 * @since 1.0.0
 */
@RestController
@RequestMapping("/metrics/idempotent")
@ConditionalOnWebApplication
@PreAuthorize("@idempotentMetricsAccessGuard.canAccess(authentication)")
public class IdempotentMetricsController extends BaseExceptionHandler {

    private final IdempotentMetricsService metricsService;

    public IdempotentMetricsController(IdempotentMetricsService metricsService) {
        this.metricsService = metricsService;
    }
    
    /**
     * 获取幂等执行统计
     * GET /metrics/idempotent
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getMetrics(
            @RequestParam(name = "activeTenantId", required = false) Long activeTenantId) {
        IdempotentMetricsSnapshot snapshot = metricsService.snapshot(activeTenantId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "OK");
        response.put("activeTenantId", activeTenantId);
        response.put("windowMinutes", snapshot.windowMinutes());
        response.put("windowStartEpochMillis", snapshot.windowStartEpochMillis());
        response.put("windowEndEpochMillis", snapshot.windowEndEpochMillis());
        response.put("passCount", snapshot.passCount());
        response.put("hitCount", snapshot.hitCount());
        response.put("successCount", snapshot.successCount());
        response.put("failureCount", snapshot.failureCount());
        response.put("storeErrorCount", snapshot.storeErrorCount());
        response.put("validationRejectCount", snapshot.validationRejectCount());
        response.put("rejectCount", snapshot.rejectCount());
        response.put("totalCheckCount", snapshot.totalCheckCount());
        response.put("conflictRate", snapshot.conflictRate());
        response.put("storageErrorRate", snapshot.storageErrorRate());
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取热点 Key 统计
     * GET /metrics/idempotent/top-keys
     */
    @GetMapping("/top-keys")
    public ResponseEntity<Map<String, Object>> getTopKeys(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(name = "activeTenantId", required = false) Long activeTenantId) {
        IdempotentMetricsSnapshot snapshot = metricsService.snapshot(activeTenantId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "OK");
        response.put("activeTenantId", activeTenantId);
        response.put("windowMinutes", snapshot.windowMinutes());
        response.put("windowStartEpochMillis", snapshot.windowStartEpochMillis());
        response.put("windowEndEpochMillis", snapshot.windowEndEpochMillis());
        response.put("limit", Math.max(1, Math.min(limit, 100)));
        response.put("topKeys", metricsService.topScopes(limit, activeTenantId));
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取 MQ 幂等消息处理统计
     * GET /metrics/idempotent/mq
     */
    @GetMapping("/mq")
    public ResponseEntity<Map<String, Object>> getMqMetrics(
            @RequestParam(name = "activeTenantId", required = false) Long activeTenantId) {
        IdempotentMetricsSnapshot snapshot = metricsService.snapshot(activeTenantId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "OK");
        response.put("activeTenantId", activeTenantId);
        response.put("windowMinutes", snapshot.windowMinutes());
        response.put("windowStartEpochMillis", snapshot.windowStartEpochMillis());
        response.put("windowEndEpochMillis", snapshot.windowEndEpochMillis());
        response.put("successCount", snapshot.successCount());
        response.put("failureCount", snapshot.failureCount());
        response.put("duplicateRate", snapshot.conflictRate());
        return ResponseEntity.ok(response);
    }
}
