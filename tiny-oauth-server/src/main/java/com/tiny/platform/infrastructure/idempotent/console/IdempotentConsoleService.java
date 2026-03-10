package com.tiny.platform.infrastructure.idempotent.console;

import com.tiny.platform.infrastructure.core.dto.PageResponse;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.idempotent.console.repository.IdempotentConsoleRepository;
import com.tiny.platform.infrastructure.idempotent.console.dto.IdempotentBlacklistDto;
import com.tiny.platform.infrastructure.idempotent.console.dto.IdempotentRecordDto;
import com.tiny.platform.infrastructure.idempotent.console.dto.IdempotentRuleDto;
import com.tiny.platform.infrastructure.idempotent.metrics.IdempotentMetricsService;
import com.tiny.platform.infrastructure.idempotent.metrics.IdempotentMetricsSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 幂等治理控制台服务
 *
 * @author tiny-platform
 * @since 1.0.0
 */
@Service
@ConditionalOnBean(IdempotentConsoleRepository.class)
public class IdempotentConsoleService {

    private final IdempotentConsoleRepository consoleRepository;
    private final IdempotentMetricsService metricsService;

    public IdempotentConsoleService(IdempotentConsoleRepository consoleRepository,
                                   IdempotentMetricsService metricsService) {
        this.consoleRepository = consoleRepository;
        this.metricsService = metricsService;
    }

    // ---------- Rules ----------

    public PageResponse<IdempotentRuleDto> getRules(String scene, String bizCode, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        return new PageResponse<>(consoleRepository.findRules(scene, bizCode, pageable));
    }

    public IdempotentRuleDto createRule(IdempotentRuleDto dto) {
        if (!StringUtils.hasText(dto.getScope())) {
            throw BusinessException.validationError("scope 不能为空");
        }
        return consoleRepository.createRule(dto);
    }

    public IdempotentRuleDto updateRule(Long id, IdempotentRuleDto dto) {
        if (!StringUtils.hasText(dto.getScope())) {
            throw BusinessException.validationError("scope 不能为空");
        }
        int updated = consoleRepository.updateRule(id, dto);
        if (updated == 0) {
            throw BusinessException.notFound("规则不存在: " + id);
        }
        dto.setId(id);
        return dto;
    }

    public void deleteRule(Long id) {
        int deleted = consoleRepository.deleteRule(id);
        if (deleted == 0) {
            throw BusinessException.notFound("规则不存在: " + id);
        }
    }

    public void enableRules(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw BusinessException.validationError("ids 不能为空");
        }
        consoleRepository.updateRulesEnabled(ids, true);
    }

    public void disableRules(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw BusinessException.validationError("ids 不能为空");
        }
        consoleRepository.updateRulesEnabled(ids, false);
    }

    // ---------- Records ----------

    public PageResponse<IdempotentRecordDto> getRecords(String key, String status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        return new PageResponse<>(consoleRepository.findRecords(key, status, pageable));
    }

    /**
     * 重试失败记录：删除 FAILED 状态的 token，使客户端可重新提交
     */
    public void retryRecord(String key) {
        if (!StringUtils.hasText(key)) {
            throw BusinessException.validationError("key 不能为空");
        }
        int deleted = consoleRepository.deleteRecordByKey(key);
        if (deleted == 0) {
            throw BusinessException.notFound("记录不存在或已过期: " + key);
        }
    }

    // ---------- Metrics ----------

    public IdempotentMetricsSnapshot getMetrics(String date, String scene, Long tenantId) {
        return metricsService.snapshot(tenantId);
    }

    public Map<String, Object> getMetricsMap(String date, String scene, Long tenantId) {
        IdempotentMetricsSnapshot s = metricsService.snapshot(tenantId);
        Map<String, Object> map = new HashMap<>();
        map.put("success", true);
        map.put("message", "OK");
        map.put("tenantId", tenantId);
        map.put("windowMinutes", s.windowMinutes());
        map.put("windowStartEpochMillis", s.windowStartEpochMillis());
        map.put("windowEndEpochMillis", s.windowEndEpochMillis());
        map.put("passCount", s.passCount());
        map.put("hitCount", s.hitCount());
        map.put("successCount", s.successCount());
        map.put("failureCount", s.failureCount());
        map.put("storeErrorCount", s.storeErrorCount());
        map.put("validationRejectCount", s.validationRejectCount());
        map.put("rejectCount", s.rejectCount());
        map.put("totalCheckCount", s.totalCheckCount());
        map.put("conflictRate", s.conflictRate());
        map.put("storageErrorRate", s.storageErrorRate());
        return map;
    }

    // ---------- Blacklist ----------

    public PageResponse<IdempotentBlacklistDto> getBlacklist(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        return new PageResponse<>(consoleRepository.findBlacklist(pageable));
    }

    public IdempotentBlacklistDto addBlacklist(IdempotentBlacklistDto dto) {
        if (!StringUtils.hasText(dto.getKeyPattern())) {
            throw BusinessException.validationError("key_pattern 不能为空");
        }
        return consoleRepository.createBlacklist(dto);
    }

    public void deleteBlacklist(Long id) {
        int deleted = consoleRepository.deleteBlacklist(id);
        if (deleted == 0) {
            throw BusinessException.notFound("黑名单记录不存在: " + id);
        }
    }
}
