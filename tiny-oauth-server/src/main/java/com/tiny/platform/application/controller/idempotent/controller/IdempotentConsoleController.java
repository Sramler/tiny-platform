package com.tiny.platform.application.controller.idempotent.controller;

import com.tiny.platform.infrastructure.core.dto.PageResponse;
import com.tiny.platform.infrastructure.core.exception.base.BaseExceptionHandler;
import com.tiny.platform.infrastructure.idempotent.console.IdempotentConsoleService;
import com.tiny.platform.infrastructure.idempotent.console.dto.IdempotentBlacklistDto;
import com.tiny.platform.infrastructure.idempotent.console.dto.IdempotentRecordDto;
import com.tiny.platform.infrastructure.idempotent.console.dto.IdempotentRuleDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 幂等性治理控制台接口
 *
 * <p>提供规则管理、记录查询、统计指标、黑名单等治理能力。</p>
 *
 * @author tiny-platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/console")
@ConditionalOnWebApplication
@ConditionalOnBean(IdempotentConsoleService.class)
@PreAuthorize("@idempotentMetricsAccessGuard.canAccess(authentication)")
public class IdempotentConsoleController extends BaseExceptionHandler {

    private final IdempotentConsoleService consoleService;

    public IdempotentConsoleController(IdempotentConsoleService consoleService) {
        this.consoleService = consoleService;
    }

    /**
     * 查询幂等规则
     * GET /console/rules
     */
    @GetMapping("/rules")
    public ResponseEntity<PageResponse<IdempotentRuleDto>> getRules(
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String bizCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(consoleService.getRules(scene, bizCode, page, size));
    }

    /**
     * 新增幂等规则
     * POST /console/rules
     */
    @PostMapping("/rules")
    public ResponseEntity<IdempotentRuleDto> createRule(@RequestBody IdempotentRuleDto rule) {
        return ResponseEntity.ok(consoleService.createRule(rule));
    }

    /**
     * 更新幂等规则
     * PUT /console/rules/{id}
     */
    @PutMapping("/rules/{id}")
    public ResponseEntity<IdempotentRuleDto> updateRule(@PathVariable Long id, @RequestBody IdempotentRuleDto rule) {
        return ResponseEntity.ok(consoleService.updateRule(id, rule));
    }

    /**
     * 删除幂等规则
     * DELETE /console/rules/{id}
     */
    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        consoleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 批量启用规则
     * POST /console/rules/enable
     */
    @PostMapping("/rules/enable")
    public ResponseEntity<Map<String, Object>> enableRules(@RequestBody Map<String, Object> request) {
        List<Long> ids = parseIds(request);
        consoleService.enableRules(ids);
        return ResponseEntity.ok(Map.of("success", true, "message", "批量启用成功"));
    }

    /**
     * 批量禁用规则
     * POST /console/rules/disable
     */
    @PostMapping("/rules/disable")
    public ResponseEntity<Map<String, Object>> disableRules(@RequestBody Map<String, Object> request) {
        List<Long> ids = parseIds(request);
        consoleService.disableRules(ids);
        return ResponseEntity.ok(Map.of("success", true, "message", "批量禁用成功"));
    }

    /**
     * 查询幂等执行记录
     * GET /console/records
     */
    @GetMapping("/records")
    public ResponseEntity<PageResponse<IdempotentRecordDto>> getRecords(
            @RequestParam(required = false) String key,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(consoleService.getRecords(key, status, page, size));
    }

    /**
     * 手动触发失败记录的补偿/重试（删除 FAILED 状态的 token，允许客户端重新提交）
     * POST /console/records/retry
     */
    @PostMapping("/records/retry")
    public ResponseEntity<Map<String, Object>> retryRecord(@RequestBody Map<String, Object> request) {
        String key = (String) request.get("key");
        if (key == null) {
            key = request.get("id") != null ? request.get("id").toString() : null;
        }
        consoleService.retryRecord(key);
        return ResponseEntity.ok(Map.of("success", true, "message", "重试已就绪，可重新提交"));
    }

    /**
     * 查询统计指标
     * GET /console/metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String scene,
            @RequestParam(name = "activeTenantId", required = false) Long activeTenantId) {
        return ResponseEntity.ok(consoleService.getMetricsMap(date, scene, activeTenantId));
    }

    /**
     * 查询黑名单
     * GET /console/blacklist
     */
    @GetMapping("/blacklist")
    public ResponseEntity<PageResponse<IdempotentBlacklistDto>> getBlacklist(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(consoleService.getBlacklist(page, size));
    }

    /**
     * 添加黑名单
     * POST /console/blacklist
     */
    @PostMapping("/blacklist")
    public ResponseEntity<IdempotentBlacklistDto> addBlacklist(@RequestBody IdempotentBlacklistDto dto) {
        return ResponseEntity.ok(consoleService.addBlacklist(dto));
    }

    /**
     * 删除黑名单
     * DELETE /console/blacklist/{id}
     */
    @DeleteMapping("/blacklist/{id}")
    public ResponseEntity<Void> deleteBlacklist(@PathVariable Long id) {
        consoleService.deleteBlacklist(id);
        return ResponseEntity.noContent().build();
    }

    private static List<Long> parseIds(Map<String, Object> request) {
        Object idsObj = request.get("ids");
        if (idsObj instanceof List<?> list) {
            return list.stream()
                .filter(Number.class::isInstance)
                .map(n -> ((Number) n).longValue())
                .collect(Collectors.toList());
        }
        return List.of();
    }
}
