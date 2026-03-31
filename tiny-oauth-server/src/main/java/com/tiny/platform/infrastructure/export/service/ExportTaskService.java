package com.tiny.platform.infrastructure.export.service;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.datascope.framework.DataScope;
import com.tiny.platform.infrastructure.auth.datascope.framework.DataScopeContext;
import com.tiny.platform.infrastructure.auth.datascope.framework.ResolvedDataScope;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.export.persistence.ExportTaskEntity;
import com.tiny.platform.infrastructure.export.persistence.ExportTaskRepository;
import com.tiny.platform.infrastructure.tenant.service.TenantQuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * ExportTaskService —— 任务持久化服务
 *
 * 提供任务创建、状态更新、进度上报、结果保存与过期清理能力。
 */
@Service
public class ExportTaskService {

    private static final Logger log = LoggerFactory.getLogger(ExportTaskService.class);
    private static final String ACTIVE = "ACTIVE";

    private final ExportTaskRepository repository;
    private final TenantUserRepository tenantUserRepository;
    private final UserUnitRepository userUnitRepository;
    private final UserRepository userRepository;
    private final TenantQuotaService tenantQuotaService;

    public ExportTaskService(ExportTaskRepository repository,
                             TenantUserRepository tenantUserRepository,
                             UserUnitRepository userUnitRepository,
                             UserRepository userRepository,
                             TenantQuotaService tenantQuotaService) {
        this.repository = repository;
        this.tenantUserRepository = tenantUserRepository;
        this.userUnitRepository = userUnitRepository;
        this.userRepository = userRepository;
        this.tenantQuotaService = tenantQuotaService;
    }

    /**
     * 创建初始任务（PENDING）
     */
    @Transactional
    public ExportTaskEntity createPendingTask(String taskId,
                                              String userId,
                                              String username,
                                              Integer sheetCount,
                                              String queryParamsJson,
                                              LocalDateTime expireAt) {
        ExportTaskEntity entity = new ExportTaskEntity();
        entity.setTaskId(taskId);
        entity.setTenantId(TenantContext.getActiveTenantId());
        entity.setUserId(userId);
        entity.setUsername(username);
        entity.setSheetCount(sheetCount == null ? 1 : sheetCount);
        entity.setQueryParams(queryParamsJson);
        entity.setExpireAt(expireAt);
        entity.setStatus(ExportTaskStatus.PENDING);
        entity.setProgress(0);
        entity.setProcessedRows(0L);
        entity.setAttempt(0);
        return repository.save(entity);
    }

    @Transactional
    public ExportTaskEntity markRunning(String taskId, String workerId) {
        return updateTask(taskId, entity -> {
            entity.setStatus(ExportTaskStatus.RUNNING);
            entity.setWorkerId(workerId);
            entity.setAttempt((entity.getAttempt() == null ? 0 : entity.getAttempt()) + 1);
            entity.setLastHeartbeat(LocalDateTime.now());
        });
    }

    @Transactional
    public ExportTaskEntity markProgress(String taskId, Integer progress, Long processedRows, Long totalRows) {
        return updateTask(taskId, entity -> {
            if (progress != null) {
                entity.setProgress(Math.max(0, Math.min(100, progress)));
            }
            if (processedRows != null) {
                entity.setProcessedRows(processedRows);
            }
            if (totalRows != null) {
                entity.setTotalRows(totalRows);
            }
            entity.setLastHeartbeat(LocalDateTime.now());
        });
    }

    @Transactional
    public ExportTaskEntity markSuccess(String taskId,
                                        String filePath,
                                        String downloadUrl,
                                        Long totalRows) {
        return updateTask(taskId, entity -> {
            long fileSizeBytes = resolveFileSizeBytes(filePath);
            long existingFileSizeBytes = entity.getFileSizeBytes() == null ? 0L : entity.getFileSizeBytes();
            if (tenantQuotaService != null) {
                tenantQuotaService.assertStorageQuotaAvailable(
                    entity.getTenantId(),
                    Math.max(0L, fileSizeBytes - existingFileSizeBytes),
                    "导出文件落盘"
                );
            }
            entity.setStatus(ExportTaskStatus.SUCCESS);
            entity.setProgress(100);
            if (totalRows != null) {
                entity.setTotalRows(totalRows);
                entity.setProcessedRows(totalRows);
            }
            entity.setFilePath(filePath);
            entity.setDownloadUrl(downloadUrl);
            entity.setFileSizeBytes(fileSizeBytes);
            entity.setLastHeartbeat(LocalDateTime.now());
        });
    }

    @Transactional
    public ExportTaskEntity markFailed(String taskId, String errorMsg, String errorCode) {
        return updateTask(taskId, entity -> {
            entity.setStatus(ExportTaskStatus.FAILED);
            entity.setErrorMsg(errorMsg);
            entity.setErrorCode(errorCode);
            entity.setLastHeartbeat(LocalDateTime.now());
        });
    }

    @Transactional
    public ExportTaskEntity markCanceled(String taskId) {
        return updateTask(taskId, entity -> {
            entity.setStatus(ExportTaskStatus.CANCELED);
            entity.setLastHeartbeat(LocalDateTime.now());
        });
    }

    /**
     * 查询任务
     */
    @Transactional(readOnly = true)
    public Optional<ExportTaskEntity> findByTaskId(String taskId) {
        return repository.findByTaskId(taskId);
    }

    @Transactional(readOnly = true)
    public List<ExportTaskEntity> findUserTasks(String userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    @DataScope(module = "export")
    public List<ExportTaskEntity> findReadableTasks() {
        Long tenantId = TenantContext.getActiveTenantId();
        if (tenantId == null) {
            return List.of();
        }
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        Specification<ExportTaskEntity> tenantSpec = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
        if (!requiresDataScopeFilter()) {
            return repository.findAll(tenantSpec, sort);
        }
        LinkedHashSet<String> visibleOwnerKeys = resolveVisibleExportOwnerKeysForRead(tenantId);
        if (visibleOwnerKeys.isEmpty()) {
            return List.of();
        }
        Specification<ExportTaskEntity> scopedSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            // owner 键与历史一致：user_id 与 username 任一侧命中可见集合即可见（双字段 OR，库侧谓词）
            Predicate byUserId = root.get("userId").in(visibleOwnerKeys);
            Predicate byUsername = cb.and(
                cb.isNotNull(root.get("username")),
                root.get("username").in(visibleOwnerKeys)
            );
            predicates.add(cb.or(byUserId, byUsername));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(scopedSpec, sort);
    }

    @Transactional(readOnly = true)
    public List<ExportTaskEntity> findPendingTasks() {
        return repository.findByStatusOrderByCreatedAtAsc(ExportTaskStatus.PENDING);
    }

    @Transactional
    public long deleteByTaskId(String taskId) {
        return repository.deleteByTaskId(taskId);
    }

    @Transactional
    public int cleanupExpired(LocalDateTime now) {
        List<ExportTaskEntity> expiredTasks = repository.findByExpireAtBefore(now);
        if (expiredTasks.isEmpty()) {
            return 0;
        }

        for (ExportTaskEntity task : expiredTasks) {
            String filePath = task.getFilePath();
            if (filePath == null || filePath.isBlank()) {
                continue;
            }
            try {
                Files.deleteIfExists(Path.of(filePath));
            } catch (Exception ex) {
                log.warn("Failed to delete expired export file taskId={} filePath={}", task.getTaskId(), filePath, ex);
            }
        }
        repository.deleteAllInBatch(expiredTasks);
        return expiredTasks.size();
    }

    @Transactional
    public List<ExportTaskEntity> recoverStuckTasks(LocalDateTime heartbeatThreshold) {
        List<ExportTaskEntity> stuck = repository.findStuckTasks(ExportTaskStatus.RUNNING, heartbeatThreshold);
        for (ExportTaskEntity entity : stuck) {
            entity.setStatus(ExportTaskStatus.PENDING);
            entity.setWorkerId(null);
            entity.setLastHeartbeat(null);
            repository.save(entity);
        }
        return stuck;
    }

    private ExportTaskEntity updateTask(String taskId,
                                        java.util.function.Consumer<ExportTaskEntity> mutator) {
        ExportTaskEntity entity = repository.findByTaskId(taskId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        mutator.accept(entity);
        return repository.save(entity);
    }

    private long resolveFileSizeBytes(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return 0L;
        }
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                return 0L;
            }
            return Files.size(path);
        } catch (Exception ex) {
            log.warn("Failed to resolve export file size: filePath={}", filePath, ex);
            return 0L;
        }
    }

    private LinkedHashSet<String> resolveVisibleExportOwnerKeysForRead(Long tenantId) {
        ResolvedDataScope scope = DataScopeContext.get();
        if (scope == null) {
            return new LinkedHashSet<>();
        }

        Long currentUserId = extractCurrentUserIdAsLong();
        Set<Long> activeTenantUserIds = new LinkedHashSet<>(tenantUserRepository.findUserIdsByTenantIdAndStatus(tenantId, ACTIVE));
        LinkedHashSet<Long> visibleUserIds = new LinkedHashSet<>();

        if (!scope.getVisibleUserIds().isEmpty()) {
            visibleUserIds.addAll(
                tenantUserRepository.findUserIdsByTenantIdAndUserIdInAndStatus(
                    tenantId,
                    scope.getVisibleUserIds(),
                    ACTIVE
                )
            );
        }

        if (!scope.getVisibleUnitIds().isEmpty()) {
            visibleUserIds.addAll(
                userUnitRepository.findUserIdsByTenantIdAndUnitIdInAndStatus(
                    tenantId,
                    scope.getVisibleUnitIds(),
                    ACTIVE
                )
            );
        }

        if (scope.isSelfOnly() && currentUserId != null && activeTenantUserIds.contains(currentUserId)) {
            visibleUserIds.add(currentUserId);
        }

        visibleUserIds.retainAll(activeTenantUserIds);
        if (visibleUserIds.isEmpty()) {
            return new LinkedHashSet<>();
        }

        LinkedHashSet<String> ownerKeys = visibleUserIds.stream()
            .map(String::valueOf)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        ownerKeys.addAll(userRepository.findUsernamesByIdIn(visibleUserIds));
        return ownerKeys;
    }

    private boolean requiresDataScopeFilter() {
        ResolvedDataScope scope = DataScopeContext.get();
        return scope != null && !scope.isUnrestricted();
    }

    private Long extractCurrentUserIdAsLong() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return securityUser.getUserId();
        }
        String name = authentication.getName();
        if (!StringUtils.hasText(name) || "anonymousUser".equalsIgnoreCase(name)) {
            return null;
        }
        try {
            return Long.valueOf(name);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
