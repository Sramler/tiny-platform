package com.tiny.platform.infrastructure.scheduling.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.AuthenticationFactorAuthorities;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.scheduling.dto.*;
import com.tiny.platform.infrastructure.scheduling.exception.SchedulingExceptions;
import com.tiny.platform.infrastructure.scheduling.model.*;
import com.tiny.platform.infrastructure.scheduling.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.quartz.CronExpression;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 企业级 DAG 调度服务（基于新表结构）
 */
@Service
public class SchedulingService {

    private static final Logger logger = LoggerFactory.getLogger(SchedulingService.class);

    private final SchedulingTaskTypeRepository taskTypeRepository;
    private final SchedulingTaskRepository taskRepository;
    private final SchedulingDagRepository dagRepository;
    private final SchedulingDagVersionRepository dagVersionRepository;
    private final SchedulingDagTaskRepository dagTaskRepository;
    private final SchedulingDagEdgeRepository dagEdgeRepository;
    private final SchedulingDagRunRepository dagRunRepository;
    private final SchedulingTaskInstanceRepository taskInstanceRepository;
    private final SchedulingTaskHistoryRepository taskHistoryRepository;
    private final SchedulingAuditRepository auditRepository;
    private final QuartzSchedulerService quartzSchedulerService;
    private final TaskExecutorRegistry taskExecutorRegistry;
    private final JsonSchemaValidationService jsonSchemaValidationService;
    private final ObjectMapper objectMapper;

    public SchedulingService(
            SchedulingTaskTypeRepository taskTypeRepository,
            SchedulingTaskRepository taskRepository,
            SchedulingDagRepository dagRepository,
            SchedulingDagVersionRepository dagVersionRepository,
            SchedulingDagTaskRepository dagTaskRepository,
            SchedulingDagEdgeRepository dagEdgeRepository,
            SchedulingDagRunRepository dagRunRepository,
            SchedulingTaskInstanceRepository taskInstanceRepository,
            SchedulingTaskHistoryRepository taskHistoryRepository,
            SchedulingAuditRepository auditRepository,
            QuartzSchedulerService quartzSchedulerService,
            TaskExecutorRegistry taskExecutorRegistry,
            JsonSchemaValidationService jsonSchemaValidationService,
            ObjectMapper objectMapper) {
        this.taskTypeRepository = taskTypeRepository;
        this.taskRepository = taskRepository;
        this.dagRepository = dagRepository;
        this.dagVersionRepository = dagVersionRepository;
        this.dagTaskRepository = dagTaskRepository;
        this.dagEdgeRepository = dagEdgeRepository;
        this.dagRunRepository = dagRunRepository;
        this.taskInstanceRepository = taskInstanceRepository;
        this.taskHistoryRepository = taskHistoryRepository;
        this.auditRepository = auditRepository;
        this.quartzSchedulerService = quartzSchedulerService;
        this.taskExecutorRegistry = taskExecutorRegistry;
        this.jsonSchemaValidationService = jsonSchemaValidationService;
        this.objectMapper = objectMapper;
    }

    /**
     * 返回当前已注册的执行器标识列表，供前端下拉选择（避免手工输入拼写错误）。
     */
    public List<String> listExecutors() {
        return taskExecutorRegistry.getExecutorIdentifiers();
    }

    /**
     * MySQL JSON 列不接受空字符串，只接受合法 JSON 或 NULL。将空/空白字符串规范为 null，避免 Data truncation: Invalid JSON text.
     */
    private static String normalizeJsonColumn(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private Long requireCurrentTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId <= 0) {
            throw SchedulingExceptions.operationNotAllowed("当前请求未解析到有效租户上下文");
        }
        return tenantId;
    }

    private Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private boolean hasAuthenticatedPrincipal(Authentication authentication) {
        return authentication != null
                && (authentication.isAuthenticated() || AuthenticationFactorAuthorities.hasAnyFactor(authentication));
    }

    private String resolveCurrentUserId() {
        Authentication authentication = currentAuthentication();
        if (!hasAuthenticatedPrincipal(authentication)) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            if (securityUser.getUserId() != null) {
                return securityUser.getUserId().toString();
            }
            return StringUtils.hasText(securityUser.getUsername()) ? securityUser.getUsername() : null;
        }
        if (principal instanceof UserDetails userDetails) {
            return StringUtils.hasText(userDetails.getUsername()) ? userDetails.getUsername() : null;
        }
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return extractUserId(jwtAuthenticationToken.getToken());
        }
        if (principal instanceof String principalName && !"anonymousUser".equalsIgnoreCase(principalName)
                && StringUtils.hasText(principalName)) {
            return principalName;
        }
        return null;
    }

    private String resolveCurrentUsername() {
        Authentication authentication = currentAuthentication();
        if (!hasAuthenticatedPrincipal(authentication)) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return StringUtils.hasText(securityUser.getUsername()) ? securityUser.getUsername() : null;
        }
        if (principal instanceof UserDetails userDetails) {
            return StringUtils.hasText(userDetails.getUsername()) ? userDetails.getUsername() : null;
        }
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return extractUsername(jwtAuthenticationToken.getToken());
        }
        if (principal instanceof String principalName && !"anonymousUser".equalsIgnoreCase(principalName)
                && StringUtils.hasText(principalName)) {
            return principalName;
        }
        return null;
    }

    private String resolveCurrentActor() {
        String username = resolveCurrentUsername();
        return StringUtils.hasText(username) ? username : resolveCurrentUserId();
    }

    private String extractUserId(Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        String userId = jwt.getClaimAsString("user_id");
        if (!StringUtils.hasText(userId)) {
            userId = jwt.getClaimAsString("uid");
        }
        if (!StringUtils.hasText(userId)) {
            userId = jwt.getClaimAsString("username");
        }
        if (!StringUtils.hasText(userId)) {
            userId = jwt.getSubject();
        }
        return userId;
    }

    private String extractUsername(Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        String username = jwt.getClaimAsString("username");
        if (!StringUtils.hasText(username)) {
            username = jwt.getClaimAsString("preferred_username");
        }
        if (!StringUtils.hasText(username)) {
            username = jwt.getClaimAsString("user_name");
        }
        if (!StringUtils.hasText(username)) {
            username = jwt.getClaimAsString("sub");
        }
        return username;
    }

    private SchedulingTaskType requireTaskTypeInTenant(Long taskTypeId, Long tenantId) {
        return taskTypeRepository.findByIdAndTenantId(taskTypeId, tenantId)
                .orElseThrow(() -> SchedulingExceptions.notFound("任务类型不存在: %s", taskTypeId));
    }

    private SchedulingTask requireTaskInTenant(Long taskId, Long tenantId) {
        return taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> SchedulingExceptions.notFound("任务不存在: %s", taskId));
    }

    private SchedulingDag requireDagInTenant(Long dagId, Long tenantId) {
        return dagRepository.findByIdAndTenantId(dagId, tenantId)
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG不存在: %s", dagId));
    }

    private SchedulingDagVersion requireDagVersionInTenant(Long dagId, Long versionId, Long tenantId) {
        requireDagInTenant(dagId, tenantId);
        return dagVersionRepository.findById(versionId)
                .filter(version -> Objects.equals(version.getDagId(), dagId))
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG版本不存在: %s", versionId));
    }

    private SchedulingDagTask requireDagNodeInTenant(Long dagId, Long versionId, Long nodeId, Long tenantId) {
        requireDagVersionInTenant(dagId, versionId, tenantId);
        return dagTaskRepository.findById(nodeId)
                .filter(node -> Objects.equals(node.getDagVersionId(), versionId))
                .orElseThrow(() -> SchedulingExceptions.notFound("节点不存在: %s", nodeId));
    }

    private void ensureDagVersionMutable(SchedulingDagVersion version) {
        if (version != null && "ACTIVE".equalsIgnoreCase(version.getStatus())) {
            throw SchedulingExceptions.operationNotAllowed("ACTIVE版本不可直接修改，请创建新版本");
        }
    }

    private SchedulingDagVersion requireMutableDagVersion(Long dagId, Long versionId, Long tenantId) {
        SchedulingDagVersion version = requireDagVersionInTenant(dagId, versionId, tenantId);
        ensureDagVersionMutable(version);
        return version;
    }

    private void activateDagVersion(Long dagId, SchedulingDagVersion version) {
        List<SchedulingDagVersion> activeVersions = dagVersionRepository.findByDagId(dagId).stream()
                .filter(v -> "ACTIVE".equals(v.getStatus()) && !Objects.equals(v.getId(), version.getId()))
                .collect(Collectors.toList());
        for (SchedulingDagVersion activeVersion : activeVersions) {
            activeVersion.setStatus("ARCHIVED");
            dagVersionRepository.save(activeVersion);
        }
        version.setStatus("ACTIVE");
        version.setActivatedAt(LocalDateTime.now());
    }

    private SchedulingDagRun requireDagRunInTenant(Long dagId, Long runId, Long tenantId) {
        requireDagInTenant(dagId, tenantId);
        return dagRunRepository.findByIdAndTenantId(runId, tenantId)
                .filter(run -> Objects.equals(run.getDagId(), dagId))
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG运行实例不存在: %s", runId));
    }

    private SchedulingTaskInstance requireTaskInstanceInTenant(Long instanceId, Long tenantId) {
        return taskInstanceRepository.findByIdAndTenantId(instanceId, tenantId)
                .orElseThrow(() -> SchedulingExceptions.notFound("任务实例不存在: %s", instanceId));
    }

    private Optional<SchedulingTaskHistory> findTaskHistoryInTenant(Long historyId, Long tenantId) {
        Optional<SchedulingTaskHistory> directHit = taskHistoryRepository.findByIdAndTenantId(historyId, tenantId);
        if (directHit.isPresent()) {
            return directHit;
        }
        return taskHistoryRepository.findById(historyId)
                .filter(history -> {
                    if (history.getTaskInstanceId() != null
                            && taskInstanceRepository.findByIdAndTenantId(history.getTaskInstanceId(), tenantId).isPresent()) {
                        return true;
                    }
                    return history.getDagRunId() != null
                            && dagRunRepository.findByIdAndTenantId(history.getDagRunId(), tenantId).isPresent();
                });
    }

    private String serializeExecutionSnapshot(
            SchedulingTask task,
            SchedulingTaskType taskType) {
        try {
            return objectMapper.writeValueAsString(SchedulingTaskExecutionSnapshot.from(task, taskType));
        } catch (Exception e) {
            throw SchedulingExceptions.systemError("序列化任务执行快照失败", e);
        }
    }

    private SchedulingExecutionContext buildExecutionContext(
            Long tenantId,
            Long dagId,
            Long dagRunId,
            Long dagVersionId,
            String triggerType,
            String fallbackUsername) {
        String username = resolveCurrentUsername();
        if (!StringUtils.hasText(username)) {
            username = fallbackUsername;
        }
        return SchedulingExecutionContext.builder()
                .tenantId(tenantId)
                .userId(resolveCurrentUserId())
                .username(username)
                .dagId(dagId)
                .dagRunId(dagRunId)
                .dagVersionId(dagVersionId)
                .triggerType(triggerType)
                .build();
    }

    private void applyExecutionContextTenant(SchedulingExecutionContext executionContext) {
        TenantContext.clear();
        if (executionContext != null && executionContext.getTenantId() != null) {
            TenantContext.setTenantId(executionContext.getTenantId());
            TenantContext.setTenantSource(TenantContext.SOURCE_UNKNOWN);
        }
    }

    private void restoreTenantContext(Long previousTenantId, String previousTenantSource) {
        TenantContext.clear();
        if (previousTenantId != null) {
            TenantContext.setTenantId(previousTenantId);
        }
        if (previousTenantSource != null) {
            TenantContext.setTenantSource(previousTenantSource);
        }
    }

    // ==================== TaskType 相关 ====================

    @Transactional
    public SchedulingTaskType createTaskType(SchedulingTaskTypeCreateUpdateDto dto) {
        Long tenantId = requireCurrentTenantId();
        if (taskTypeRepository.findByTenantIdAndCode(tenantId, dto.getCode()).isPresent()) {
            throw SchedulingExceptions.conflict("任务类型编码已存在: %s", dto.getCode());
        }
        validateTaskTypeConfiguration(dto.getExecutor(), dto.getParamSchema());
        SchedulingTaskType taskType = new SchedulingTaskType();
        taskType.setTenantId(tenantId);
        taskType.setCode(dto.getCode());
        taskType.setName(dto.getName());
        taskType.setDescription(dto.getDescription());
        taskType.setExecutor(StringUtils.hasText(dto.getExecutor()) ? dto.getExecutor().trim() : null);
        taskType.setParamSchema(normalizeJsonColumn(dto.getParamSchema()));
        taskType.setDefaultTimeoutSec(dto.getDefaultTimeoutSec() != null ? dto.getDefaultTimeoutSec() : 0);
        taskType.setDefaultMaxRetry(dto.getDefaultMaxRetry() != null ? dto.getDefaultMaxRetry() : 0);
        taskType.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        taskType.setCreatedBy(resolveCurrentActor());
        SchedulingTaskType saved = taskTypeRepository.save(taskType);
        recordAudit("task_type", saved.getId(), "CREATE", saved, saved.getTenantId());
        return saved;
    }

    @Transactional
    public SchedulingTaskType updateTaskType(Long id, SchedulingTaskTypeCreateUpdateDto dto) {
        Long tenantId = requireCurrentTenantId();
        SchedulingTaskType taskType = taskTypeRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> {
                    logger.warn("任务类型更新失败: id={}, tenantId={}, 原因=任务类型不存在", id, tenantId);
                    return SchedulingExceptions.notFound("任务类型不存在: %s", id);
                });
        validateTaskTypeConfiguration(
                dto.getExecutor() != null ? dto.getExecutor() : taskType.getExecutor(),
                dto.getParamSchema() != null ? dto.getParamSchema() : taskType.getParamSchema());
        if (dto.getCode() != null && !dto.getCode().equals(taskType.getCode())) {
            if (taskTypeRepository.findByTenantIdAndCode(taskType.getTenantId(), dto.getCode()).isPresent()) {
                logger.warn("任务类型更新失败: id={}, 原因=任务类型编码已存在, code={}", id, dto.getCode());
                throw SchedulingExceptions.conflict("任务类型编码已存在: %s", dto.getCode());
            }
            taskType.setCode(dto.getCode());
        }
        if (dto.getName() != null) taskType.setName(dto.getName());
        if (dto.getDescription() != null) taskType.setDescription(dto.getDescription());
        if (dto.getExecutor() != null) {
            taskType.setExecutor(StringUtils.hasText(dto.getExecutor()) ? dto.getExecutor().trim() : null);
        }
        if (dto.getParamSchema() != null) taskType.setParamSchema(normalizeJsonColumn(dto.getParamSchema()));
        if (dto.getDefaultTimeoutSec() != null) taskType.setDefaultTimeoutSec(dto.getDefaultTimeoutSec());
        if (dto.getDefaultMaxRetry() != null) taskType.setDefaultMaxRetry(dto.getDefaultMaxRetry());
        if (dto.getEnabled() != null) taskType.setEnabled(dto.getEnabled());
        SchedulingTaskType saved = taskTypeRepository.save(taskType);
        recordAudit("task_type", saved.getId(), "UPDATE", saved, saved.getTenantId());
        return saved;
    }

    @Transactional
    public void deleteTaskType(Long id) {
        SchedulingTaskType taskType = requireTaskTypeInTenant(id, requireCurrentTenantId());
        // 检查是否有任务在使用
        long count = taskRepository.count((root, query, cb) -> 
                cb.equal(root.get("typeId"), id));
        if (count > 0) {
            throw SchedulingExceptions.operationNotAllowed(
                    "该任务类型正在被使用（被 %d 个任务使用），无法删除。请先解除任务关联后再删除。", count);
        }
        taskTypeRepository.delete(taskType);
        recordAudit("task_type", id, "DELETE", Map.of("id", id, "code", taskType.getCode()), taskType.getTenantId());
    }

    public Optional<SchedulingTaskType> getTaskType(Long id) {
        return taskTypeRepository.findByIdAndTenantId(id, requireCurrentTenantId());
    }

    public Page<SchedulingTaskType> listTaskTypes(String code, String name, Pageable pageable) {
        Long currentTenantId = requireCurrentTenantId();
        Specification<SchedulingTaskType> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), currentTenantId));
            if (code != null && !code.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("code")), "%" + code.toLowerCase() + "%"));
            }
            if (name != null && !name.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
        return taskTypeRepository.findAll(spec, pageable);
    }

    // ==================== Task 相关 ====================

    @Transactional
    public SchedulingTask createTask(SchedulingTaskCreateUpdateDto dto) {
        Long tenantId = requireCurrentTenantId();
        if (dto.getTypeId() == null) {
            throw SchedulingExceptions.validation("任务类型(typeId)不能为空");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw SchedulingExceptions.validation("任务名称不能为空");
        }
        requireTaskTypeInTenant(dto.getTypeId(), tenantId);
        String normalizedCode = normalizeOptionalCode(dto.getCode());
        if (normalizedCode != null) {
            if (taskRepository.findByTenantIdAndCode(tenantId, normalizedCode).isPresent()) {
                throw SchedulingExceptions.conflict("任务编码已存在: %s", normalizedCode);
            }
        }
        SchedulingTask task = new SchedulingTask();
        task.setTenantId(tenantId);
        task.setTypeId(dto.getTypeId());
        task.setCode(normalizedCode);
        task.setName(dto.getName().trim());
        task.setDescription(dto.getDescription());
        task.setParams(normalizeJsonColumn(dto.getParams()));
        task.setTimeoutSec(dto.getTimeoutSec());
        task.setMaxRetry(dto.getMaxRetry() != null ? dto.getMaxRetry() : 0);
        task.setRetryPolicy(normalizeJsonColumn(dto.getRetryPolicy()));
        task.setConcurrencyPolicy(dto.getConcurrencyPolicy() != null ? dto.getConcurrencyPolicy() : "PARALLEL");
        task.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        task.setCreatedBy(resolveCurrentActor());
        SchedulingTask saved = taskRepository.save(task);
        recordAudit("task", saved.getId(), "CREATE", saved, saved.getTenantId());
        return saved;
    }

    @Transactional
    public SchedulingTask updateTask(Long id, SchedulingTaskCreateUpdateDto dto) {
        Long tenantId = requireCurrentTenantId();
        SchedulingTask task = requireTaskInTenant(id, tenantId);
        if (dto.getCode() != null) {
            String normalizedCode = normalizeOptionalCode(dto.getCode());
            if (!Objects.equals(normalizedCode, task.getCode())) {
                if (normalizedCode != null
                        && taskRepository.findByTenantIdAndCode(task.getTenantId(), normalizedCode).isPresent()) {
                    throw SchedulingExceptions.conflict("任务编码已存在: %s", normalizedCode);
                }
                task.setCode(normalizedCode);
            }
        }
        if (dto.getName() != null) {
            if (dto.getName().isBlank()) {
                throw SchedulingExceptions.validation("任务名称不能为空");
            }
            task.setName(dto.getName().trim());
        }
        if (dto.getDescription() != null) task.setDescription(dto.getDescription());
        if (dto.getTypeId() != null) {
            requireTaskTypeInTenant(dto.getTypeId(), tenantId);
            task.setTypeId(dto.getTypeId());
        }
        if (dto.getParams() != null) task.setParams(normalizeJsonColumn(dto.getParams()));
        if (dto.getTimeoutSec() != null) task.setTimeoutSec(dto.getTimeoutSec());
        if (dto.getMaxRetry() != null) task.setMaxRetry(dto.getMaxRetry());
        if (dto.getRetryPolicy() != null) task.setRetryPolicy(normalizeJsonColumn(dto.getRetryPolicy()));
        if (dto.getConcurrencyPolicy() != null) task.setConcurrencyPolicy(dto.getConcurrencyPolicy());
        if (dto.getEnabled() != null) task.setEnabled(dto.getEnabled());
        SchedulingTask saved = taskRepository.save(task);
        recordAudit("task", saved.getId(), "UPDATE", saved, saved.getTenantId());
        return saved;
    }

    @Transactional
    public void deleteTask(Long id) {
        SchedulingTask task = requireTaskInTenant(id, requireCurrentTenantId());
        // 检查是否有 DAG 节点在使用
        List<SchedulingDagTask> usingNodes = dagTaskRepository.findByTaskId(id);
        if (!usingNodes.isEmpty()) {
            throw SchedulingExceptions.operationNotAllowed("该任务正在被 DAG 使用，无法删除");
        }
        if (taskInstanceRepository.existsByTaskIdAndStatusIn(id, Set.of("PENDING", "RESERVED", "RUNNING", "PAUSED"))) {
            throw SchedulingExceptions.operationNotAllowed("该任务仍存在待执行或运行中的任务实例，无法删除");
        }
        if (taskInstanceRepository.existsByTaskId(id) || taskHistoryRepository.existsByTaskId(id)) {
            throw SchedulingExceptions.operationNotAllowed("该任务已有执行历史，无法删除");
        }
        taskRepository.delete(task);
        recordAudit("task", id, "DELETE", Map.of("id", id, "code", task.getCode()), task.getTenantId());
    }

    public Optional<SchedulingTask> getTask(Long id) {
        return taskRepository.findByIdAndTenantId(id, requireCurrentTenantId());
    }

    public Page<SchedulingTask> listTasks(Long typeId, String code, String name, Pageable pageable) {
        Long currentTenantId = requireCurrentTenantId();
        Specification<SchedulingTask> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), currentTenantId));
            if (typeId != null) {
                predicates.add(cb.equal(root.get("typeId"), typeId));
            }
            if (code != null && !code.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("code")), "%" + code.toLowerCase() + "%"));
            }
            if (name != null && !name.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
        return taskRepository.findAll(spec, pageable);
    }

    private void recordAudit(String objectType, Object objectId, String action, Object detail, Long tenantId) {
        try {
            SchedulingAudit audit = new SchedulingAudit();
            audit.setObjectType(objectType);
            audit.setObjectId(objectId != null ? String.valueOf(objectId) : null);
            audit.setAction(action);
            audit.setTenantId(tenantId);
            audit.setPerformedBy(resolveCurrentActor());
            if (detail != null) {
                try {
                    audit.setDetail(objectMapper.writeValueAsString(detail));
                } catch (Exception serializationException) {
                    audit.setDetail(objectMapper.copy().findAndRegisterModules().writeValueAsString(detail));
                }
            }
            auditRepository.save(audit);
        } catch (Exception e) {
            logger.warn("记录审计日志失败, objectType: {}, action: {}, error: {}", objectType, action, e.getMessage());
        }
    }

    private void validateTaskTypeConfiguration(String executor, String paramSchema) {
        if (StringUtils.hasText(executor) && taskExecutorRegistry.find(executor.trim()).isEmpty()) {
            throw SchedulingExceptions.validation("执行器不存在: %s", executor.trim());
        }
        jsonSchemaValidationService.ensureValidSchema(paramSchema);
    }

    private void validateCronTimezone(String cronTimezone) {
        if (!StringUtils.hasText(cronTimezone)) {
            return;
        }
        try {
            java.time.ZoneId.of(cronTimezone.trim());
        } catch (Exception e) {
            throw SchedulingExceptions.validation("Cron 时区无效: %s", cronTimezone.trim());
        }
    }

    private void validateJsonDocument(String json, String fieldName) {
        if (!StringUtils.hasText(json)) {
            return;
        }
        try {
            objectMapper.readTree(json);
        } catch (Exception e) {
            throw SchedulingExceptions.validation("%s 不是合法 JSON: %s", fieldName, e.getMessage());
        }
    }

    private String normalizeOptionalCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeDagVersionStatus(String status) {
        if (status == null) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        if (!Set.of("DRAFT", "ACTIVE", "ARCHIVED").contains(normalized)) {
            throw SchedulingExceptions.validation("DAG版本状态无效: %s", status);
        }
        return normalized;
    }

    @FunctionalInterface
    private interface QuartzOperation {
        void run() throws Exception;
    }

    private void runQuartzOperation(String actionDescription, QuartzOperation operation) {
        try {
            operation.run();
        } catch (Exception e) {
            throw SchedulingExceptions.systemError(actionDescription + ": %s", e, e.getMessage());
        }
    }

    private void runQuartzOperationAfterCommit(String actionDescription, QuartzOperation operation) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        operation.run();
                    } catch (Exception e) {
                        logger.error("{}（事务已提交）失败: {}", actionDescription, e.getMessage(), e);
                    }
                }
            });
            return;
        }
        runQuartzOperation(actionDescription, operation);
    }

    private void triggerDagExecutionAfterCommit(
            SchedulingDag dag,
            SchedulingExecutionContext executionContext,
            Long dagRunId,
            String failureActionDescription) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        quartzSchedulerService.triggerDagNow(dag, executionContext);
                    } catch (Exception e) {
                        markDagRunTriggerFailed(dagRunId, failureActionDescription, e);
                    }
                }
            });
            return;
        }
        try {
            quartzSchedulerService.triggerDagNow(dag, executionContext);
        } catch (Exception e) {
            markDagRunTriggerFailed(dagRunId, failureActionDescription, e);
            throw SchedulingExceptions.systemError(failureActionDescription + ": %s", e, e.getMessage());
        }
    }

    private void markDagRunTriggerFailed(Long dagRunId, String failureActionDescription, Exception e) {
        try {
            dagRunRepository.findById(dagRunId).ifPresent(run -> {
                run.setStatus("FAILED");
                if (run.getStartTime() == null) {
                    run.setStartTime(LocalDateTime.now());
                }
                run.setEndTime(LocalDateTime.now());
                dagRunRepository.save(run);
            });
        } catch (Exception saveException) {
            logger.error("触发失败后回写 DAG Run 状态失败, dagRunId: {}, message: {}", dagRunId, saveException.getMessage(), saveException);
        }
        logger.error("{}失败, dagRunId: {}, message: {}", failureActionDescription, dagRunId, e.getMessage(), e);
    }

    // ==================== DAG 相关 ====================

    @Transactional
    public SchedulingDag createDag(SchedulingDagCreateUpdateDto dto) {
        Long tenantId = requireCurrentTenantId();
        if (dto.getCode() != null && !dto.getCode().isEmpty()) {
            if (dagRepository.findByTenantIdAndCode(tenantId, dto.getCode()).isPresent()) {
                throw SchedulingExceptions.conflict("DAG编码已存在: %s", dto.getCode());
            }
        }
        if (Boolean.TRUE.equals(dto.getCronEnabled()) && dto.getCronExpression() != null && !dto.getCronExpression().trim().isEmpty()) {
            validateCronExpression(dto.getCronExpression());
        }
        validateCronTimezone(dto.getCronTimezone());
        if (isCronSchedulingActive(
                dto.getEnabled() != null ? dto.getEnabled() : true,
                dto.getCronEnabled() != null ? dto.getCronEnabled() : true,
                dto.getCronExpression())) {
            throw SchedulingExceptions.operationNotAllowed("DAG尚无ACTIVE版本，无法启用定时调度，请先创建并激活版本");
        }
        SchedulingDag dag = new SchedulingDag();
        dag.setTenantId(tenantId);
        dag.setCode(dto.getCode());
        dag.setName(dto.getName());
        dag.setDescription(dto.getDescription());
        dag.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        dag.setCreatedBy(resolveCurrentActor());
        String cron = (dto.getCronExpression() != null && !dto.getCronExpression().trim().isEmpty())
                ? dto.getCronExpression().trim() : null;
        dag.setCronExpression(cron);
        dag.setCronTimezone(dto.getCronTimezone());
        dag.setCronEnabled(dto.getCronEnabled() != null ? dto.getCronEnabled() : true);
        dag = dagRepository.save(dag);

        // 从数据库读取 cron 配置同步到 Quartz（事务提交后执行，避免 DB/Quartz 分叉）
        syncDagCronToQuartz(dag);
        
        recordAudit("dag", dag.getId(), "CREATE", dag, dag.getTenantId());
        return dag;
    }

    @Transactional
    public SchedulingDag updateDag(Long id, SchedulingDagCreateUpdateDto dto) {
        SchedulingDag dag = requireDagInTenant(id, requireCurrentTenantId());
        if (dto.getCode() != null && !dto.getCode().equals(dag.getCode())) {
            if (dagRepository.findByTenantIdAndCode(dag.getTenantId(), dto.getCode()).isPresent()) {
                throw SchedulingExceptions.conflict("DAG编码已存在: %s", dto.getCode());
            }
            dag.setCode(dto.getCode());
        }
        if (dto.getName() != null) dag.setName(dto.getName());
        if (dto.getDescription() != null) dag.setDescription(dto.getDescription());
        if (dto.getEnabled() != null) dag.setEnabled(dto.getEnabled());
        if (dto.getCronExpression() != null) {
            String c = dto.getCronExpression().trim();
            dag.setCronExpression(c.isEmpty() ? null : c);
        }
        if (dto.getCronTimezone() != null) {
            validateCronTimezone(dto.getCronTimezone());
            dag.setCronTimezone(dto.getCronTimezone().trim().isEmpty() ? null : dto.getCronTimezone().trim());
        }
        if (dto.getCronEnabled() != null) {
            dag.setCronEnabled(dto.getCronEnabled());
        }
        if (Boolean.TRUE.equals(dag.getCronEnabled()) && dag.getCronExpression() != null && !dag.getCronExpression().trim().isEmpty()) {
            validateCronExpression(dag.getCronExpression());
        }
        if (isCronSchedulingActive(dag.getEnabled(), dag.getCronEnabled(), dag.getCronExpression())) {
            ensureDagHasActiveVersion(dag.getId());
        }
        dag = dagRepository.save(dag);
        // 从数据库读取 cron 配置同步到 Quartz（事务提交后执行，避免 DB/Quartz 分叉）
        syncDagCronToQuartz(dag);
        
        recordAudit("dag", dag.getId(), "UPDATE", dag, dag.getTenantId());
        return dag;
    }

    @Transactional
    public void deleteDag(Long id) {
        SchedulingDag dag = requireDagInTenant(id, requireCurrentTenantId());
        if (dagRunRepository.countByDagId(id) > 0) {
            throw SchedulingExceptions.operationNotAllowed("该DAG已有运行历史，无法删除");
        }
        
        // 删除 Quartz Job 改为事务提交后执行，避免 DB 回滚后 Quartz 已删除
        runQuartzOperationAfterCommit("删除 DAG Job", () -> quartzSchedulerService.deleteDagJob(id));
        
        // 删除版本、节点、边、运行记录等
        List<SchedulingDagVersion> versions = dagVersionRepository.findByDagId(id);
        for (SchedulingDagVersion version : versions) {
            dagTaskRepository.deleteByDagVersionId(version.getId());
            dagEdgeRepository.deleteByDagVersionId(version.getId());
        }
        dagVersionRepository.deleteAll(versions);
        dagRepository.delete(dag);
        recordAudit("dag", id, "DELETE", Map.of("id", id, "code", dag.getCode()), dag.getTenantId());
    }

    public Optional<SchedulingDag> getDag(Long id) {
        return dagRepository.findByIdAndTenantId(id, requireCurrentTenantId())
                .map(this::enrichDagCurrentVersionId);
    }

    public Page<SchedulingDag> listDags(String code, String name, Pageable pageable) {
        Long currentTenantId = requireCurrentTenantId();
        Specification<SchedulingDag> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), currentTenantId));
            if (code != null && !code.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("code")), "%" + code.toLowerCase() + "%"));
            }
            if (name != null && !name.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<SchedulingDag> page = dagRepository.findAll(spec, pageable);
        enrichDagCurrentVersionIds(page.getContent());
        return page;
    }

    private SchedulingDag enrichDagCurrentVersionId(SchedulingDag dag) {
        if (dag == null || dag.getId() == null) {
            return dag;
        }
        dag.setCurrentVersionId(
                dagVersionRepository.findByDagIdAndStatus(dag.getId(), "ACTIVE")
                        .map(SchedulingDagVersion::getId)
                        .orElse(null));
        List<SchedulingDagRun> relevantRuns = dagRunRepository.findByDagIdInAndStatusInOrderByIdDesc(
                List.of(dag.getId()),
                List.of("RUNNING", "FAILED", "PARTIAL_FAILED"));
        Set<String> statuses = relevantRuns.stream()
                .map(SchedulingDagRun::getStatus)
                .collect(Collectors.toSet());
        dag.setHasRunningRun(statuses.contains("RUNNING"));
        dag.setHasRetryableRun(statuses.contains("FAILED") || statuses.contains("PARTIAL_FAILED"));
        return dag;
    }

    private void enrichDagCurrentVersionIds(List<SchedulingDag> dags) {
        if (dags == null || dags.isEmpty()) {
            return;
        }
        List<Long> dagIds = dags.stream()
                .map(SchedulingDag::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (dagIds.isEmpty()) {
            return;
        }
        Map<Long, Long> activeVersionByDagId = dagVersionRepository.findByDagIdInAndStatus(dagIds, "ACTIVE")
                .stream()
                .collect(Collectors.toMap(
                        SchedulingDagVersion::getDagId,
                        SchedulingDagVersion::getId,
                        (left, right) -> right));
        Map<Long, Set<String>> dagRunStatuses = dagRunRepository
                .findByDagIdInAndStatusInOrderByIdDesc(dagIds, List.of("RUNNING", "FAILED", "PARTIAL_FAILED"))
                .stream()
                .collect(Collectors.groupingBy(
                        SchedulingDagRun::getDagId,
                        Collectors.mapping(SchedulingDagRun::getStatus, Collectors.toSet())));
        for (SchedulingDag dag : dags) {
            dag.setCurrentVersionId(activeVersionByDagId.get(dag.getId()));
            Set<String> statuses = dagRunStatuses.getOrDefault(dag.getId(), Collections.emptySet());
            dag.setHasRunningRun(statuses.contains("RUNNING"));
            dag.setHasRetryableRun(statuses.contains("FAILED") || statuses.contains("PARTIAL_FAILED"));
        }
    }

    // ==================== DAG Version 相关 ====================

    @Transactional
    public SchedulingDagVersion createDagVersion(Long dagId, SchedulingDagVersionCreateUpdateDto dto) {
        Long tenantId = requireCurrentTenantId();
        requireDagInTenant(dagId, tenantId);
        Integer maxVersion = dagVersionRepository.findMaxVersionNoByDagId(dagId);
        int nextVersion = (maxVersion != null ? maxVersion : 0) + 1;
        String normalizedStatus = normalizeDagVersionStatus(dto.getStatus());
        SchedulingDagVersion version = new SchedulingDagVersion();
        version.setDagId(dagId);
        version.setVersionNo(nextVersion);
        version.setStatus(normalizedStatus != null ? normalizedStatus : "DRAFT");
        version.setDefinition(normalizeJsonColumn(dto.getDefinition()));
        version.setCreatedBy(resolveCurrentActor());
        if ("ACTIVE".equals(version.getStatus())) {
            activateDagVersion(dagId, version);
        }
        SchedulingDagVersion saved = dagVersionRepository.save(version);
        recordAudit("dag_version", saved.getId(), "CREATE", saved, tenantId);
        return saved;
    }

    @Transactional
    public SchedulingDagVersion updateDagVersion(Long dagId, Long versionId, SchedulingDagVersionCreateUpdateDto dto) {
        Long tenantId = requireCurrentTenantId();
        SchedulingDagVersion version = requireDagVersionInTenant(dagId, versionId, tenantId);
        if (dto.getDefinition() != null) {
            ensureDagVersionMutable(version);
            version.setDefinition(normalizeJsonColumn(dto.getDefinition()));
        }
        if (dto.getStatus() != null) {
            String normalizedStatus = normalizeDagVersionStatus(dto.getStatus());
            if ("ACTIVE".equals(normalizedStatus)) {
                activateDagVersion(dagId, version);
            } else {
                version.setStatus(normalizedStatus);
            }
        }
        SchedulingDagVersion saved = dagVersionRepository.save(version);
        recordAudit("dag_version", saved.getId(), "UPDATE", saved, tenantId);
        return saved;
    }

    public Optional<SchedulingDagVersion> getDagVersion(Long dagId, Long versionId) {
        Long tenantId = requireCurrentTenantId();
        if (dagRepository.findByIdAndTenantId(dagId, tenantId).isEmpty()) {
            return Optional.empty();
        }
        return dagVersionRepository.findById(versionId)
                .filter(v -> Objects.equals(v.getDagId(), dagId));
    }

    public List<SchedulingDagVersion> listDagVersions(Long dagId) {
        requireDagInTenant(dagId, requireCurrentTenantId());
        return dagVersionRepository.findByDagId(dagId);
    }

    // ==================== DAG Node 相关 ====================

    @Transactional
    public SchedulingDagTask createDagNode(Long dagId, Long versionId, SchedulingDagTaskCreateUpdateDto dto) {
        Long tenantId = requireCurrentTenantId();
        requireMutableDagVersion(dagId, versionId, tenantId);
        requireTaskInTenant(dto.getTaskId(), tenantId);
        if (dagTaskRepository.findByDagVersionIdAndNodeCode(versionId, dto.getNodeCode()).isPresent()) {
            throw SchedulingExceptions.conflict("节点编码已存在: %s", dto.getNodeCode());
        }
        SchedulingDagTask node = new SchedulingDagTask();
        node.setDagVersionId(versionId);
        node.setNodeCode(dto.getNodeCode());
        node.setTaskId(dto.getTaskId());
        node.setName(dto.getName());
        node.setOverrideParams(normalizeJsonColumn(dto.getOverrideParams()));
        node.setTimeoutSec(dto.getTimeoutSec());
        node.setMaxRetry(dto.getMaxRetry());
        node.setParallelGroup(dto.getParallelGroup());
        node.setMeta(normalizeJsonColumn(dto.getMeta()));
        SchedulingDagTask saved = dagTaskRepository.save(node);
        recordAudit("dag_node", saved.getId(), "CREATE", saved, tenantId);
        return saved;
    }

    @Transactional
    public SchedulingDagTask updateDagNode(Long dagId, Long versionId, Long nodeId, SchedulingDagTaskCreateUpdateDto dto) {
        Long tenantId = requireCurrentTenantId();
        requireMutableDagVersion(dagId, versionId, tenantId);
        SchedulingDagTask node = requireDagNodeInTenant(dagId, versionId, nodeId, tenantId);
        String originalNodeCode = node.getNodeCode();
        if (dto.getNodeCode() != null && !dto.getNodeCode().equals(node.getNodeCode())) {
            if (dagTaskRepository.findByDagVersionIdAndNodeCode(versionId, dto.getNodeCode()).isPresent()) {
                throw SchedulingExceptions.conflict("节点编码已存在: %s", dto.getNodeCode());
            }
            node.setNodeCode(dto.getNodeCode());
        }
        if (dto.getTaskId() != null) {
            requireTaskInTenant(dto.getTaskId(), tenantId);
            node.setTaskId(dto.getTaskId());
        }
        if (dto.getName() != null) node.setName(dto.getName());
        if (dto.getOverrideParams() != null) node.setOverrideParams(normalizeJsonColumn(dto.getOverrideParams()));
        if (dto.getTimeoutSec() != null) node.setTimeoutSec(dto.getTimeoutSec());
        if (dto.getMaxRetry() != null) node.setMaxRetry(dto.getMaxRetry());
        if (dto.getParallelGroup() != null) node.setParallelGroup(dto.getParallelGroup());
        if (dto.getMeta() != null) node.setMeta(normalizeJsonColumn(dto.getMeta()));
        SchedulingDagTask saved = dagTaskRepository.save(node);
        if (!Objects.equals(originalNodeCode, saved.getNodeCode())) {
            dagEdgeRepository.updateFromNodeCode(versionId, originalNodeCode, saved.getNodeCode());
            dagEdgeRepository.updateToNodeCode(versionId, originalNodeCode, saved.getNodeCode());
        }
        recordAudit("dag_node", saved.getId(), "UPDATE", saved, tenantId);
        return saved;
    }

    @Transactional
    public void deleteDagNode(Long dagId, Long versionId, Long nodeId) {
        Long tenantId = requireCurrentTenantId();
        requireMutableDagVersion(dagId, versionId, tenantId);
        SchedulingDagTask node = requireDagNodeInTenant(dagId, versionId, nodeId, tenantId);
        // 删除相关边
        dagEdgeRepository.findByDagVersionId(versionId).stream()
                .filter(e -> e.getFromNodeCode().equals(node.getNodeCode()) || e.getToNodeCode().equals(node.getNodeCode()))
                .forEach(dagEdgeRepository::delete);
        dagTaskRepository.delete(node);
        recordAudit("dag_node", nodeId, "DELETE",
                Map.of("dagId", dagId, "nodeCode", node.getNodeCode()), tenantId);
    }

    public Optional<SchedulingDagTask> getDagNode(Long dagId, Long versionId, Long nodeId) {
        Long tenantId = requireCurrentTenantId();
        if (dagRepository.findByIdAndTenantId(dagId, tenantId).isEmpty()) {
            return Optional.empty();
        }
        return dagVersionRepository.findById(versionId)
                .filter(v -> Objects.equals(v.getDagId(), dagId))
                .flatMap(v -> dagTaskRepository.findById(nodeId)
                        .filter(n -> Objects.equals(n.getDagVersionId(), versionId)));
    }

    public List<SchedulingDagTask> getDagNodes(Long dagId, Long versionId) {
        requireDagVersionInTenant(dagId, versionId, requireCurrentTenantId());
        return dagTaskRepository.findByDagVersionId(versionId);
    }

    public List<SchedulingDagTask> getUpstreamNodes(Long dagId, Long versionId, Long nodeId) {
        SchedulingDagTask node = getDagNode(dagId, versionId, nodeId)
                .orElseThrow(() -> SchedulingExceptions.notFound("节点不存在: %s", nodeId));
        List<String> upstreamCodes = dagEdgeRepository.findByDagVersionIdAndToNodeCode(versionId, node.getNodeCode()).stream()
                .map(SchedulingDagEdge::getFromNodeCode)
                .collect(Collectors.toList());
        return dagTaskRepository.findByDagVersionId(versionId).stream()
                .filter(n -> upstreamCodes.contains(n.getNodeCode()))
                .collect(Collectors.toList());
    }

    public List<SchedulingDagTask> getDownstreamNodes(Long dagId, Long versionId, Long nodeId) {
        SchedulingDagTask node = getDagNode(dagId, versionId, nodeId)
                .orElseThrow(() -> SchedulingExceptions.notFound("节点不存在: %s", nodeId));
        List<String> downstreamCodes = dagEdgeRepository.findByDagVersionIdAndFromNodeCode(versionId, node.getNodeCode()).stream()
                .map(SchedulingDagEdge::getToNodeCode)
                .collect(Collectors.toList());
        return dagTaskRepository.findByDagVersionId(versionId).stream()
                .filter(n -> downstreamCodes.contains(n.getNodeCode()))
                .collect(Collectors.toList());
    }

    // ==================== DAG Edge 相关 ====================

    @Transactional
    public SchedulingDagEdge createDagEdge(Long dagId, Long versionId, SchedulingDagEdgeCreateDto dto) {
        Long tenantId = requireCurrentTenantId();
        requireMutableDagVersion(dagId, versionId, tenantId);
        validateJsonDocument(dto.getCondition(), "边条件");
        // 检查节点是否存在
        SchedulingDagTask fromNode = dagTaskRepository.findByDagVersionIdAndNodeCode(versionId, dto.getFromNodeCode())
                .orElseThrow(() -> SchedulingExceptions.notFound("上游节点不存在: %s", dto.getFromNodeCode()));
        SchedulingDagTask toNode = dagTaskRepository.findByDagVersionIdAndNodeCode(versionId, dto.getToNodeCode())
                .orElseThrow(() -> SchedulingExceptions.notFound("下游节点不存在: %s", dto.getToNodeCode()));
        if (fromNode.getNodeCode().equals(toNode.getNodeCode())) {
            throw SchedulingExceptions.validation("节点不能依赖自身");
        }
        // 检查是否已存在
        List<SchedulingDagEdge> existing = dagEdgeRepository.findByDagVersionId(versionId).stream()
                .filter(e -> e.getFromNodeCode().equals(dto.getFromNodeCode()) && e.getToNodeCode().equals(dto.getToNodeCode()))
                .collect(Collectors.toList());
        if (!existing.isEmpty()) {
            throw SchedulingExceptions.conflict("依赖关系已存在");
        }
        // 环检测：加入 (from, to) 后若存在从 to 到 from 的路径则形成环
        if (wouldCreateCycle(versionId, dto.getFromNodeCode(), dto.getToNodeCode())) {
            throw SchedulingExceptions.validation("DAG 存在环，禁止创建边");
        }
        SchedulingDagEdge edge = new SchedulingDagEdge();
        edge.setDagVersionId(versionId);
        edge.setFromNodeCode(dto.getFromNodeCode());
        edge.setToNodeCode(dto.getToNodeCode());
        edge.setCondition(normalizeJsonColumn(dto.getCondition()));
        SchedulingDagEdge saved = dagEdgeRepository.save(edge);
        recordAudit("dag_edge", saved.getId(), "CREATE", saved, tenantId);
        return saved;
    }

    @Transactional
    public void deleteDagEdge(Long dagId, Long versionId, Long edgeId) {
        Long tenantId = requireCurrentTenantId();
        requireMutableDagVersion(dagId, versionId, tenantId);
        SchedulingDagEdge edge = dagEdgeRepository.findById(edgeId)
                .orElseThrow(() -> SchedulingExceptions.notFound("依赖关系不存在: %s", edgeId));
        if (!edge.getDagVersionId().equals(versionId)) {
            throw SchedulingExceptions.validation("依赖关系不属于该版本");
        }
        dagEdgeRepository.delete(edge);
        recordAudit("dag_edge", edgeId, "DELETE",
                Map.of("dagId", dagId, "from", edge.getFromNodeCode(), "to", edge.getToNodeCode()),
                tenantId);
    }

    public List<SchedulingDagEdge> getDagEdges(Long dagId, Long versionId) {
        requireDagVersionInTenant(dagId, versionId, requireCurrentTenantId());
        return dagEdgeRepository.findByDagVersionId(versionId);
    }

    /**
     * DAG 运行统计（Run 级别）：total/success/failed/avgDurationMs/p95DurationMs/p99DurationMs。
     * 使用 SQL 聚合与分位点查询，避免大数据量时全量加载到内存。
     */
    public SchedulingDagStatsDto getDagStats(Long dagId) {
        requireDagInTenant(dagId, requireCurrentTenantId());
        Object[] agg = dagRunRepository.getDagRunStatsAggregation(dagId);
        if (agg == null || agg.length < 5) {
            SchedulingDagStatsDto dto = new SchedulingDagStatsDto();
            dto.setTotal(0);
            dto.setSuccess(0);
            dto.setFailed(0);
            return dto;
        }
        long total = ((Number) agg[0]).longValue();
        long success = ((Number) agg[1]).longValue();
        long failed = ((Number) agg[2]).longValue();
        long completedCount = ((Number) agg[3]).longValue();
        Double avgMs = agg[4] != null ? ((Number) agg[4]).doubleValue() : null;

        SchedulingDagStatsDto dto = new SchedulingDagStatsDto();
        dto.setTotal(total);
        dto.setSuccess(success);
        dto.setFailed(failed);
        dto.setAvgDurationMs(avgMs != null ? avgMs.longValue() : null);

        if (completedCount > 0) {
            int idx95 = Math.max(0, Math.min((int) Math.ceil(completedCount * 0.95) - 1, (int) completedCount - 1));
            int idx99 = Math.max(0, Math.min((int) Math.ceil(completedCount * 0.99) - 1, (int) completedCount - 1));
            Double p95 = dagRunRepository.getDurationMsAtOffset(dagId, idx95);
            Double p99 = dagRunRepository.getDurationMsAtOffset(dagId, idx99);
            dto.setP95DurationMs(p95 != null ? p95.longValue() : null);
            dto.setP99DurationMs(p99 != null ? p99.longValue() : null);
        }
        return dto;
    }

    // ==================== DAG 调度触发/控制 ====================

    @Transactional
    public SchedulingDagRun triggerDag(Long dagId) {
        SchedulingDag dag = requireDagInTenant(dagId, requireCurrentTenantId());
        if (!dag.getEnabled()) {
            throw SchedulingExceptions.operationNotAllowed("DAG已禁用，无法触发");
        }
        // 获取当前激活版本
        SchedulingDagVersion version = dagVersionRepository.findByDagIdAndStatus(dagId, "ACTIVE")
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG没有激活版本"));
        String currentActor = resolveCurrentActor();
        
        // 先创建运行实例
        SchedulingDagRun run = new SchedulingDagRun();
        run.setDagId(dagId);
        run.setDagVersionId(version.getId());
        run.setRunNo(UUID.randomUUID().toString());
        run.setTenantId(dag.getTenantId());
        run.setTriggerType("MANUAL");
        run.setTriggeredBy(StringUtils.hasText(currentActor) ? currentActor : "system");
        run.setStatus("SCHEDULED");
        run = dagRunRepository.save(run);

        logger.info("手动触发 DAG, dagId: {}, runId: {}, triggeredBy: {}", dagId, run.getId(), run.getTriggeredBy());

        triggerDagExecutionAfterCommit(
                dag,
                buildExecutionContext(
                        dag.getTenantId(),
                        dagId,
                        run.getId(),
                        version.getId(),
                        "MANUAL",
                        run.getTriggeredBy()),
                run.getId(),
                "触发 DAG 执行失败");
        
        Map<String, Object> detail = new HashMap<>();
        detail.put("runId", run.getId());
        if (run.getTriggeredBy() != null) {
            detail.put("triggeredBy", run.getTriggeredBy());
        }
        recordAudit("dag", dagId, "TRIGGER", detail, dag.getTenantId());

        return run;
    }

    @Transactional
    public void pauseDag(Long dagId) {
        SchedulingDag dag = requireDagInTenant(dagId, requireCurrentTenantId());
        dag.setEnabled(false);
        dagRepository.save(dag);
        
        runQuartzOperationAfterCommit("暂停 DAG Job 失败", () -> quartzSchedulerService.pauseDagJob(dagId));

        recordAudit("dag", dagId, "PAUSE", Map.of("enabled", false), dag.getTenantId());
    }

    @Transactional
    public void resumeDag(Long dagId) {
        SchedulingDag dag = requireDagInTenant(dagId, requireCurrentTenantId());
        dag.setEnabled(true);
        dagRepository.save(dag);
        
        runQuartzOperationAfterCommit("恢复 DAG Job 失败", () -> quartzSchedulerService.resumeDagJob(dagId));

        recordAudit("dag", dagId, "RESUME", Map.of("enabled", true), dag.getTenantId());
    }

    @Transactional
    public void stopDag(Long dagId) {
        SchedulingDag dag = requireDagInTenant(dagId, requireCurrentTenantId());
        
        runQuartzOperationAfterCommit("停止 DAG Job 失败", () -> quartzSchedulerService.pauseDagJob(dagId));
        
        // 停止所有运行中的 Run，并将对应任务实例中未终态的标为 CANCELLED
        List<SchedulingDagRun> runningRuns = dagRunRepository.findByDagIdAndStatus(dagId, "RUNNING");
        for (SchedulingDagRun run : runningRuns) {
            cancelDagRun(run);
        }

        recordAudit("dag", dagId, "STOP", Map.of("cancelledRuns", runningRuns.size()), dag.getTenantId());
    }

    @Transactional
    public void stopDagRun(Long dagId, Long runId) {
        Long tenantId = requireCurrentTenantId();
        SchedulingDag dag = requireDagInTenant(dagId, tenantId);
        SchedulingDagRun run = requireDagRunInTenant(dagId, runId, tenantId);
        if (!"RUNNING".equals(run.getStatus())) {
            throw SchedulingExceptions.operationNotAllowed("仅支持停止 RUNNING 的运行实例");
        }

        int cancelledInstances = cancelDagRun(run);
        recordAudit("dag_run", runId, "STOP",
                Map.of("dagId", dagId, "runId", runId, "cancelledInstances", cancelledInstances),
                dag.getTenantId());
    }

    @Transactional
    public void retryDag(Long dagId) {
        SchedulingDag dag = requireDagInTenant(dagId, requireCurrentTenantId());
        if (!dag.getEnabled()) {
            throw SchedulingExceptions.operationNotAllowed("DAG已禁用，无法重试");
        }
        SchedulingDagRun failedRun = dagRunRepository
                .findTopByDagIdAndStatusInOrderByIdDesc(dagId, List.of("FAILED", "PARTIAL_FAILED"))
                .orElseThrow(() -> SchedulingExceptions.operationNotAllowed("没有可重试的失败运行"));
        retryDagRunInternal(dag, failedRun);
    }

    @Transactional
    public void retryDagRun(Long dagId, Long runId) {
        Long tenantId = requireCurrentTenantId();
        SchedulingDag dag = requireDagInTenant(dagId, tenantId);
        if (!dag.getEnabled()) {
            throw SchedulingExceptions.operationNotAllowed("DAG已禁用，无法重试");
        }
        SchedulingDagRun failedRun = requireDagRunInTenant(dagId, runId, tenantId);
        if (!isRetryableDagRunStatus(failedRun.getStatus())) {
            throw SchedulingExceptions.operationNotAllowed("仅支持重试失败的运行实例");
        }
        retryDagRunInternal(dag, failedRun);
    }

    /**
     * 执行 DAG（由 Quartz Job 调用，确保事务一致性）
     * @param dagId DAG ID
     * @param dagRunId DAG 运行实例 ID（手动触发时传递，定时触发时为 null）
     * @param dagVersionId DAG 版本 ID（手动触发时传递，定时触发时为 null）
     */
    @Transactional
    public void executeDag(Long dagId, Long dagRunId, Long dagVersionId) {
        executeDag(SchedulingExecutionContext.builder()
                .tenantId(TenantContext.getTenantId())
                .dagId(dagId)
                .dagRunId(dagRunId)
                .dagVersionId(dagVersionId)
                .triggerType(dagRunId != null && dagRunId > 0 ? "MANUAL" : "SCHEDULE")
                .build());
    }

    @Transactional
    public void executeDag(SchedulingExecutionContext executionContext) {
        if (executionContext == null || executionContext.getDagId() == null) {
            throw SchedulingExceptions.validation("执行 DAG 缺少 dagId");
        }
        Long previousTenantId = TenantContext.getTenantId();
        String previousTenantSource = TenantContext.getTenantSource();
        try {
            applyExecutionContextTenant(executionContext);
            doExecuteDag(executionContext);
        } finally {
            restoreTenantContext(previousTenantId, previousTenantSource);
        }
    }

    private void doExecuteDag(SchedulingExecutionContext executionContext) {
        Long dagId = executionContext.getDagId();
        Long dagRunId = executionContext.getDagRunId();
        Long dagVersionId = executionContext.getDagVersionId();
        Long tenantId = executionContext.getTenantId();
        boolean isManualTrigger = (dagRunId != null && dagRunId > 0);
        logger.info("开始执行 DAG, tenantId: {}, dagId: {}, dagRunId: {}, triggerType: {}, isManualTrigger: {}",
                tenantId,
                dagId,
                dagRunId,
                executionContext.getTriggerType(),
                isManualTrigger);

        SchedulingDag dag = tenantId != null
                ? requireDagInTenant(dagId, tenantId)
                : dagRepository.findById(dagId)
                        .orElseThrow(() -> SchedulingExceptions.notFound("DAG不存在: %s", dagId));

        if (!dag.getEnabled()) {
            logger.warn("DAG已禁用，跳过执行, dagId: {}", dagId);
            if (isManualTrigger && dagRunId != null && dagRunId > 0) {
                Optional<SchedulingDagRun> existingRun = tenantId != null
                        ? dagRunRepository.findByIdAndTenantId(dagRunId, tenantId)
                        : dagRunRepository.findById(dagRunId);
                existingRun.ifPresent(run -> {
                    run.setStatus("CANCELLED");
                    run.setEndTime(LocalDateTime.now());
                    dagRunRepository.save(run);
                });
            }
            return;
        }

        SchedulingDagVersion version;
        SchedulingDagRun run;

        if (isManualTrigger) {
            // 手动触发：使用已存在的 dagRun 和 dagVersion
            if (dagRunId == null || dagRunId <= 0) {
                throw SchedulingExceptions.validation("手动触发时 dagRunId 不能为空");
            }
            run = tenantId != null
                    ? dagRunRepository.findByIdAndTenantId(dagRunId, tenantId)
                        .orElseThrow(() -> SchedulingExceptions.notFound("DAG运行实例不存在: %s", dagRunId))
                    : dagRunRepository.findById(dagRunId)
                        .orElseThrow(() -> SchedulingExceptions.notFound("DAG运行实例不存在: %s", dagRunId));
            Long versionId = (dagVersionId != null && dagVersionId > 0) ? dagVersionId : run.getDagVersionId();
            if (versionId == null) {
                throw SchedulingExceptions.validation("DAG版本ID不能为空");
            }
            version = tenantId != null
                    ? requireDagVersionInTenant(dagId, versionId, tenantId)
                    : dagVersionRepository.findById(versionId)
                        .orElseThrow(() -> SchedulingExceptions.notFound("DAG版本不存在: %s", versionId));

            // 更新运行状态
            run.setStatus("RUNNING");
            run.setStartTime(LocalDateTime.now());
            run = dagRunRepository.save(run);
        } else {
            // 定时触发：创建新的 dagRun
                version = dagVersionRepository.findByDagIdAndStatus(dagId, "ACTIVE")
                        .orElseThrow(() -> SchedulingExceptions.notFound("DAG没有激活版本: %s", dagId));

            run = new SchedulingDagRun();
            run.setDagId(dagId);
            run.setDagVersionId(version.getId());
            run.setRunNo(UUID.randomUUID().toString());
            run.setTenantId(dag.getTenantId());
            run.setTriggerType(StringUtils.hasText(executionContext.getTriggerType()) ? executionContext.getTriggerType() : "SCHEDULE");
            run.setTriggeredBy(StringUtils.hasText(executionContext.getUsername()) ? executionContext.getUsername() : "Quartz Scheduler");
            run.setStatus("RUNNING");
            run.setStartTime(LocalDateTime.now());
            run = dagRunRepository.save(run);
        }

        // 创建任务实例（根据 DAG 节点和依赖关系创建）
        List<SchedulingTaskInstance> instances = createDagTaskInstances(run, version);
        taskInstanceRepository.saveAll(instances);

        logger.info("DAG执行完成, dagId: {}, runId: {}", dagId, run.getId());
    }

    /**
     * 创建 DAG 任务实例
     * 根据 DAG 版本中的节点和依赖关系创建任务实例
     */
    private List<SchedulingTaskInstance> createDagTaskInstances(
            SchedulingDagRun run,
            SchedulingDagVersion version) {

        // 1. 获取版本下的所有节点
        List<SchedulingDagTask> nodes = dagTaskRepository.findByDagVersionId(version.getId());
        if (nodes.isEmpty()) {
            logger.warn("DAG版本 {} 没有节点", version.getId());
            return List.of();
        }

        // 2. 获取所有边（依赖关系）
        List<SchedulingDagEdge> edges = dagEdgeRepository.findByDagVersionId(version.getId());

        // 3. 构建依赖图：找出每个节点的上游节点
        Map<String, List<String>> upstreamMap = new HashMap<>();
        Set<String> allNodeCodes = nodes.stream()
                .map(SchedulingDagTask::getNodeCode)
                .collect(Collectors.toSet());

        for (SchedulingDagEdge edge : edges) {
            upstreamMap.computeIfAbsent(edge.getToNodeCode(), k -> new ArrayList<>())
                    .add(edge.getFromNodeCode());
        }

        // 4. 找出没有上游节点的节点（起始节点）
        Set<String> startNodes = allNodeCodes.stream()
                .filter(nodeCode -> !upstreamMap.containsKey(nodeCode) || upstreamMap.get(nodeCode).isEmpty())
                .collect(Collectors.toSet());

        // 5. 为每个节点创建任务实例
        List<SchedulingTaskInstance> instances = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        Map<Long, SchedulingTask> tasksById = nodes.stream()
                .map(SchedulingDagTask::getTaskId)
                .distinct()
                .collect(Collectors.toMap(taskId -> taskId, taskId -> requireTaskInTenant(taskId, run.getTenantId())));
        Map<Long, SchedulingTaskType> taskTypesById = tasksById.values().stream()
                .map(SchedulingTask::getTypeId)
                .distinct()
                .collect(Collectors.toMap(typeId -> typeId, typeId -> requireTaskTypeInTenant(typeId, run.getTenantId())));

        for (SchedulingDagTask node : nodes) {
            SchedulingTask task = tasksById.get(node.getTaskId());
            SchedulingTaskType taskType = taskTypesById.get(task.getTypeId());
            SchedulingTaskInstance instance = new SchedulingTaskInstance();
            instance.setDagRunId(run.getId());
            instance.setDagId(run.getDagId());
            instance.setDagVersionId(version.getId());
            instance.setNodeCode(node.getNodeCode());
            instance.setConcurrencyKey(resolveConcurrencyKey(node, node.getTaskId()));
            instance.setTaskId(node.getTaskId());
            instance.setTenantId(run.getTenantId());
            instance.setAttemptNo(1);
            instance.setExecutionSnapshot(serializeExecutionSnapshot(task, taskType));

            // 如果是起始节点，立即调度；否则等待依赖完成
            if (startNodes.contains(node.getNodeCode())) {
                instance.setStatus("PENDING");
                instance.setScheduledAt(now);
            } else {
                instance.setStatus("PENDING");
                instance.setScheduledAt(null); // 等待依赖完成后再调度
            }

            // 设置参数（合并节点覆盖参数和任务默认参数）
            instance.setParams(node.getOverrideParams());

            instances.add(instance);
        }

        logger.info("为 DAG Run {} 创建了 {} 个任务实例", run.getId(), instances.size());
        return instances;
    }

    // ==================== DAG 节点调度 ====================

    @Transactional
    public void triggerNode(Long dagId, Long nodeId) {
        Long tenantId = requireCurrentTenantId();
        SchedulingDag dag = requireDagInTenant(dagId, tenantId);
        SchedulingDagTask node = requireDagNodeForDag(dagId, nodeId, tenantId);
        SchedulingDagVersion version = requireDagVersionInTenant(dagId, node.getDagVersionId(), tenantId);

        SchedulingDagRun run = dagRunRepository.findTopByDagIdAndStatusOrderByIdDesc(dagId, "RUNNING")
                .filter(existingRun -> Objects.equals(existingRun.getTenantId(), tenantId))
                .orElseGet(() -> createManualNodeTriggerRun(dag, version));
        triggerNodeInternal(dag, node, version, run);
    }

    @Transactional
    public void triggerNode(Long dagId, Long runId, Long nodeId) {
        Long tenantId = requireCurrentTenantId();
        SchedulingDag dag = requireDagInTenant(dagId, tenantId);
        SchedulingDagTask node = requireDagNodeForDag(dagId, nodeId, tenantId);
        SchedulingDagVersion version = requireDagVersionInTenant(dagId, node.getDagVersionId(), tenantId);
        SchedulingDagRun run = requireDagRunInTenant(dagId, runId, tenantId);
        if (!"RUNNING".equals(run.getStatus())) {
            throw SchedulingExceptions.operationNotAllowed("仅支持在RUNNING运行中触发节点");
        }
        triggerNodeInternal(dag, node, version, run);
    }

    @Transactional
    public void retryNode(Long dagId, Long nodeId) {
        Long tenantId = requireCurrentTenantId();
        SchedulingDag dag = requireDagInTenant(dagId, tenantId);
        SchedulingDagTask node = requireDagNodeForDag(dagId, nodeId, tenantId);
        SchedulingDagVersion version = requireDagVersionInTenant(dagId, node.getDagVersionId(), tenantId);

        SchedulingDagRun run = dagRunRepository.findByDagIdOrderByIdDesc(dagId).stream()
                .filter(existingRun -> Objects.equals(existingRun.getTenantId(), tenantId))
                .filter(existingRun -> taskInstanceRepository
                        .findTopByDagRunIdAndNodeCodeOrderByIdDesc(existingRun.getId(), node.getNodeCode())
                        .filter(instance -> "FAILED".equals(instance.getStatus()))
                        .isPresent())
                .findFirst()
                .orElseThrow(() -> SchedulingExceptions.notFound("未找到失败的任务实例"));
        retryNodeInternal(dag, node, version, run);
    }

    @Transactional
    public void retryNode(Long dagId, Long runId, Long nodeId) {
        Long tenantId = requireCurrentTenantId();
        SchedulingDag dag = requireDagInTenant(dagId, tenantId);
        SchedulingDagTask node = requireDagNodeForDag(dagId, nodeId, tenantId);
        SchedulingDagVersion version = requireDagVersionInTenant(dagId, node.getDagVersionId(), tenantId);
        SchedulingDagRun run = requireDagRunInTenant(dagId, runId, tenantId);
        retryNodeInternal(dag, node, version, run);
    }

    @Transactional
    public void pauseNode(Long dagId, Long nodeId) {
        Long tenantId = requireCurrentTenantId();
        SchedulingDag dag = requireDagInTenant(dagId, tenantId);
        SchedulingDagTask node = requireDagNodeForDag(dagId, nodeId, tenantId);
        SchedulingDagRun run = dagRunRepository.findByDagIdOrderByIdDesc(dagId).stream()
                .filter(existingRun -> Objects.equals(existingRun.getTenantId(), tenantId))
                .filter(existingRun -> taskInstanceRepository
                        .findTopByDagRunIdAndNodeCodeAndStatusInOrderByIdDesc(
                                existingRun.getId(),
                                node.getNodeCode(),
                                List.of("PENDING", "RESERVED", "PAUSED"))
                        .isPresent())
                .findFirst()
                .orElseThrow(() -> SchedulingExceptions.notFound("未找到可暂停的任务实例"));
        pauseNodeInternal(dag, node, run);
    }

    @Transactional
    public void pauseNode(Long dagId, Long runId, Long nodeId) {
        Long tenantId = requireCurrentTenantId();
        SchedulingDag dag = requireDagInTenant(dagId, tenantId);
        SchedulingDagTask node = requireDagNodeForDag(dagId, nodeId, tenantId);
        SchedulingDagRun run = requireDagRunInTenant(dagId, runId, tenantId);
        pauseNodeInternal(dag, node, run);
    }

    @Transactional
    public void resumeNode(Long dagId, Long nodeId) {
        Long tenantId = requireCurrentTenantId();
        SchedulingDag dag = requireDagInTenant(dagId, tenantId);
        SchedulingDagTask node = requireDagNodeForDag(dagId, nodeId, tenantId);
        SchedulingDagRun run = dagRunRepository.findByDagIdOrderByIdDesc(dagId).stream()
                .filter(existingRun -> Objects.equals(existingRun.getTenantId(), tenantId))
                .filter(existingRun -> taskInstanceRepository
                        .findTopByDagRunIdAndNodeCodeAndStatusOrderByIdDesc(
                                existingRun.getId(),
                                node.getNodeCode(),
                                "PAUSED")
                        .isPresent())
                .findFirst()
                .orElseThrow(() -> SchedulingExceptions.notFound("未找到可恢复的任务实例"));
        resumeNodeInternal(dag, node, run);
    }

    @Transactional
    public void resumeNode(Long dagId, Long runId, Long nodeId) {
        Long tenantId = requireCurrentTenantId();
        SchedulingDag dag = requireDagInTenant(dagId, tenantId);
        SchedulingDagTask node = requireDagNodeForDag(dagId, nodeId, tenantId);
        SchedulingDagRun run = requireDagRunInTenant(dagId, runId, tenantId);
        resumeNodeInternal(dag, node, run);
    }

    private SchedulingDagTask requireDagNodeForDag(Long dagId, Long nodeId, Long tenantId) {
        SchedulingDagTask node = dagTaskRepository.findById(nodeId)
                .orElseThrow(() -> SchedulingExceptions.notFound("节点不存在: %s", nodeId));
        requireDagVersionInTenant(dagId, node.getDagVersionId(), tenantId);
        return node;
    }

    private SchedulingDagRun createManualNodeTriggerRun(SchedulingDag dag, SchedulingDagVersion version) {
        SchedulingDagRun newRun = new SchedulingDagRun();
        newRun.setDagId(dag.getId());
        newRun.setDagVersionId(version.getId());
        newRun.setRunNo(UUID.randomUUID().toString());
        newRun.setTenantId(dag.getTenantId());
        newRun.setTriggerType("MANUAL");
        newRun.setTriggeredBy(resolveCurrentActor());
        newRun.setStatus("RUNNING");
        newRun.setStartTime(LocalDateTime.now());
        return dagRunRepository.save(newRun);
    }

    private void triggerNodeInternal(
            SchedulingDag dag,
            SchedulingDagTask node,
            SchedulingDagVersion version,
            SchedulingDagRun run) {
        Optional<SchedulingTaskInstance> latestInstance = taskInstanceRepository
                .findTopByDagRunIdAndNodeCodeOrderByIdDesc(run.getId(), node.getNodeCode());

        SchedulingTaskInstance instance;
        if (latestInstance.isEmpty()) {
            SchedulingTask task = requireTaskInTenant(node.getTaskId(), run.getTenantId());
            SchedulingTaskType taskType = requireTaskTypeInTenant(task.getTypeId(), run.getTenantId());
            instance = new SchedulingTaskInstance();
            instance.setDagRunId(run.getId());
            instance.setDagId(dag.getId());
            instance.setDagVersionId(version.getId());
            instance.setNodeCode(node.getNodeCode());
            instance.setConcurrencyKey(resolveConcurrencyKey(node, node.getTaskId()));
            instance.setTaskId(node.getTaskId());
            instance.setTenantId(run.getTenantId());
            instance.setAttemptNo(1);
            instance.setStatus("PENDING");
            instance.setScheduledAt(LocalDateTime.now());
            instance.setParams(node.getOverrideParams());
            instance.setExecutionSnapshot(serializeExecutionSnapshot(task, taskType));
            instance = taskInstanceRepository.save(instance);
        } else {
            instance = latestInstance.get();
            if (!"PENDING".equals(instance.getStatus()) && !"FAILED".equals(instance.getStatus())) {
                throw SchedulingExceptions.operationNotAllowed("节点任务实例状态不允许触发: %s", instance.getStatus());
            }
            instance.setStatus("PENDING");
            instance.setScheduledAt(LocalDateTime.now());
            instance.setAttemptNo(1);
            instance.setLockedBy(null);
            instance.setLockTime(null);
            instance.setNextRetryAt(null);
            instance = taskInstanceRepository.save(instance);
        }

        logger.info("节点触发成功, dagId: {}, dagRunId: {}, nodeId: {}, instanceId: {}",
                dag.getId(), run.getId(), node.getId(), instance.getId());
        Map<String, Object> detail = new HashMap<>();
        detail.put("dagRunId", run.getId());
        detail.put("instanceId", instance.getId());
        recordAudit("dag_node", node.getId(), "TRIGGER_NODE", detail, dag.getTenantId());
    }

    private void retryNodeInternal(
            SchedulingDag dag,
            SchedulingDagTask node,
            SchedulingDagVersion version,
            SchedulingDagRun run) {
        SchedulingTaskInstance failedInstance = taskInstanceRepository
                .findTopByDagRunIdAndNodeCodeOrderByIdDesc(run.getId(), node.getNodeCode())
                .filter(instance -> "FAILED".equals(instance.getStatus()))
                .orElseThrow(() -> SchedulingExceptions.notFound("未找到最新失败的任务实例"));

        SchedulingTaskInstance retryInstance = new SchedulingTaskInstance();
        retryInstance.setDagRunId(run.getId());
        retryInstance.setDagId(dag.getId());
        retryInstance.setDagVersionId(version.getId());
        retryInstance.setNodeCode(node.getNodeCode());
        retryInstance.setConcurrencyKey(resolveConcurrencyKey(node, node.getTaskId()));
        retryInstance.setTaskId(node.getTaskId());
        retryInstance.setTenantId(run.getTenantId());
        retryInstance.setAttemptNo((failedInstance.getAttemptNo() != null ? failedInstance.getAttemptNo() : 0) + 1);
        retryInstance.setStatus("PENDING");
        retryInstance.setScheduledAt(LocalDateTime.now());
        retryInstance.setParams(node.getOverrideParams());
        retryInstance.setExecutionSnapshot(failedInstance.getExecutionSnapshot());
        retryInstance = taskInstanceRepository.save(retryInstance);

        if (!"RUNNING".equals(run.getStatus())) {
            run.setStatus("RUNNING");
            run.setEndTime(null);
            dagRunRepository.save(run);
        }

        List<String> downstreamNodeCodes = dagEdgeRepository
                .findByDagVersionIdAndFromNodeCode(version.getId(), node.getNodeCode())
                .stream()
                .map(SchedulingDagEdge::getToNodeCode)
                .collect(Collectors.toList());
        if (!downstreamNodeCodes.isEmpty()) {
            List<SchedulingTaskInstance> runInstances = taskInstanceRepository.findByDagRunId(run.getId());
            Set<String> downstreamSet = new HashSet<>(downstreamNodeCodes);
            for (SchedulingTaskInstance inst : runInstances) {
                if ("SKIPPED".equals(inst.getStatus()) && inst.getNodeCode() != null && downstreamSet.contains(inst.getNodeCode())) {
                    inst.setStatus("PENDING");
                    inst.setScheduledAt(null);
                    inst.setNextRetryAt(null);
                    taskInstanceRepository.save(inst);
                    logger.info("节点重试时恢复下游实例为 PENDING, instanceId: {}, nodeCode: {}", inst.getId(), inst.getNodeCode());
                }
            }
        }

        logger.info("节点重试成功, dagId: {}, dagRunId: {}, nodeId: {}, instanceId: {}",
                dag.getId(), run.getId(), node.getId(), retryInstance.getId());
        Map<String, Object> detail = new HashMap<>();
        detail.put("dagRunId", run.getId());
        detail.put("instanceId", retryInstance.getId());
        recordAudit("dag_node", node.getId(), "RETRY_NODE", detail, dag.getTenantId());
    }

    private void pauseNodeInternal(
            SchedulingDag dag,
            SchedulingDagTask node,
            SchedulingDagRun run) {
        SchedulingTaskInstance instance = taskInstanceRepository
                .findTopByDagRunIdAndNodeCodeAndStatusInOrderByIdDesc(
                        run.getId(),
                        node.getNodeCode(),
                        List.of("PENDING", "RESERVED", "PAUSED"))
                .orElseThrow(() -> SchedulingExceptions.notFound("未找到可暂停的任务实例"));
        int pausedCount = 1;
        if ("PENDING".equals(instance.getStatus()) || "RESERVED".equals(instance.getStatus())) {
            instance.setLockedBy(null);
            instance.setLockTime(null);
            instance.setStatus("PAUSED");
            taskInstanceRepository.save(instance);
        }

        logger.info("节点暂停成功, dagId: {}, dagRunId: {}, nodeId: {}, 暂停实例数: {}",
                dag.getId(), run.getId(), node.getId(), pausedCount);
        recordAudit("dag_node", node.getId(), "PAUSE_NODE",
                Map.of("dagRunId", run.getId(), "pausedInstances", pausedCount), dag.getTenantId());
    }

    private void resumeNodeInternal(
            SchedulingDag dag,
            SchedulingDagTask node,
            SchedulingDagRun run) {
        SchedulingTaskInstance instance = taskInstanceRepository
                .findTopByDagRunIdAndNodeCodeAndStatusOrderByIdDesc(run.getId(), node.getNodeCode(), "PAUSED")
                .orElseThrow(() -> SchedulingExceptions.notFound("未找到可恢复的任务实例"));
        instance.setStatus("PENDING");
        instance.setScheduledAt(LocalDateTime.now());
        instance.setLockedBy(null);
        instance.setLockTime(null);
        taskInstanceRepository.save(instance);
        int resumedCount = 1;

        logger.info("节点恢复成功, dagId: {}, dagRunId: {}, nodeId: {}, 恢复实例数: {}",
                dag.getId(), run.getId(), node.getId(), resumedCount);
        recordAudit("dag_node", node.getId(), "RESUME_NODE",
                Map.of("dagRunId", run.getId(), "resumedInstances", resumedCount), dag.getTenantId());
    }

    // ==================== 运行历史 ====================

    /**
     * 分页查询 DAG 运行历史，支持按状态、触发类型、运行编号、开始时间范围筛选。
     */
    public Page<SchedulingDagRun> getDagRuns(Long dagId, Pageable pageable,
            String status, String triggerType, String runNo,
            LocalDateTime startTimeFrom, LocalDateTime startTimeTo) {
        requireDagInTenant(dagId, requireCurrentTenantId());
        Specification<SchedulingDagRun> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("dagId"), dagId));
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            if (triggerType != null && !triggerType.isBlank()) {
                predicates.add(cb.equal(root.get("triggerType"), triggerType.trim()));
            }
            if (runNo != null && !runNo.isBlank()) {
                predicates.add(cb.like(root.get("runNo"), "%" + runNo.trim() + "%"));
            }
            if (startTimeFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startTime"), startTimeFrom));
            }
            if (startTimeTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startTime"), startTimeTo));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
        return dagRunRepository.findAll(spec, pageable);
    }

    public Optional<SchedulingDagRun> getDagRun(Long dagId, Long runId) {
        Long tenantId = requireCurrentTenantId();
        if (dagRepository.findByIdAndTenantId(dagId, tenantId).isEmpty()) {
            return Optional.empty();
        }
        return dagRunRepository.findByIdAndTenantId(runId, tenantId)
                .filter(r -> Objects.equals(r.getDagId(), dagId));
    }

    public List<SchedulingTaskInstance> getDagRunNodes(Long dagId, Long runId) {
        requireDagRunInTenant(dagId, runId, requireCurrentTenantId());
        return taskInstanceRepository.findByDagRunId(runId);
    }

    public Optional<SchedulingTaskInstance> getDagRunNode(Long dagId, Long runId, Long nodeId) {
        Long tenantId = requireCurrentTenantId();
        if (dagRunRepository.findByIdAndTenantId(runId, tenantId).filter(run -> Objects.equals(run.getDagId(), dagId)).isEmpty()) {
            return Optional.empty();
        }
        return taskInstanceRepository.findByIdAndTenantId(nodeId, tenantId)
                .filter(instance -> Objects.equals(instance.getDagRunId(), runId) && Objects.equals(instance.getDagId(), dagId));
    }

    public Optional<String> getTaskInstanceLog(Long instanceId) {
        Long tenantId = requireCurrentTenantId();
        return taskInstanceRepository.findByIdAndTenantId(instanceId, tenantId)
                .map(instance -> {
                    // 从历史记录中获取日志
                    Optional<SchedulingTaskHistory> latestHistory =
                            taskHistoryRepository.findTopByTaskInstanceIdAndTenantIdOrderByIdDesc(instanceId, tenantId);
                    if (latestHistory.isEmpty()) {
                        return "暂无日志";
                    }
                    SchedulingTaskHistory latest = latestHistory.get();
                    if (latest.getLogPath() != null) {
                        return "日志路径: " + latest.getLogPath();
                    }
                    if ("SUCCESS".equals(latest.getStatus())) {
                        return latest.getResult() != null ? latest.getResult() : "执行成功";
                    } else {
                        return (latest.getErrorMessage() != null ? latest.getErrorMessage() : "") + 
                               (latest.getStackTrace() != null ? "\n" + latest.getStackTrace() : "");
                    }
                });
    }

    public Optional<SchedulingTaskHistory> getTaskHistory(Long historyId) {
        return findTaskHistoryInTenant(historyId, requireCurrentTenantId());
    }

    // ==================== 审计与监控 ====================

    public Page<SchedulingAudit> listAudits(String objectType, String action, Pageable pageable) {
        Long currentTenantId = requireCurrentTenantId();
        Specification<SchedulingAudit> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), currentTenantId));
            if (objectType != null && !objectType.isEmpty()) {
                predicates.add(cb.equal(root.get("objectType"), objectType));
            }
            if (action != null && !action.isEmpty()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
        return auditRepository.findAll(spec, pageable);
    }

    /**
     * 同步 DAG 的 Cron 配置到 Quartz（以数据库为准）。
     * DAG 禁用（enabled=false）时删除 Quartz Job，避免禁用后仍触发；cron 为空或 cronEnabled=false 时同样删除。
     * 事务存在时在 afterCommit 执行，避免 DB 回滚但 Quartz 已经更新。
     */
    private void syncDagCronToQuartz(SchedulingDag dag) {
        runQuartzOperationAfterCommit("同步定时调度失败", () -> {
            if (!Boolean.TRUE.equals(dag.getEnabled())) {
                quartzSchedulerService.deleteDagJob(dag.getId());
                return;
            }
            String cronExpression = dag.getCronExpression();
            Boolean cronEnabled = dag.getCronEnabled();
            if (cronExpression == null || cronExpression.trim().isEmpty()
                    || (cronEnabled != null && !cronEnabled)) {
                // 删除 Quartz Job
                quartzSchedulerService.deleteDagJob(dag.getId());
            } else {
                if (!hasActiveDagVersion(dag.getId())) {
                    logger.warn("DAG {} 未找到 ACTIVE 版本，跳过 Quartz 定时调度同步并删除已有 Job", dag.getId());
                    quartzSchedulerService.deleteDagJob(dag.getId());
                    return;
                }
                // 创建或更新 Quartz Job（从 DB 读取）
                quartzSchedulerService.createOrUpdateDagJob(dag, cronExpression, dag.getCronTimezone());
            }
        });
    }

    private void ensureDagHasActiveVersion(Long dagId) {
        if (!hasActiveDagVersion(dagId)) {
            throw SchedulingExceptions.operationNotAllowed("DAG尚无ACTIVE版本，无法启用定时调度，请先创建并激活版本");
        }
    }

    private boolean hasActiveDagVersion(Long dagId) {
        return dagVersionRepository.findByDagIdAndStatus(dagId, "ACTIVE").isPresent();
    }

    private boolean isCronSchedulingActive(Boolean dagEnabled, Boolean cronEnabled, String cronExpression) {
        return Boolean.TRUE.equals(dagEnabled)
                && !Boolean.FALSE.equals(cronEnabled)
                && cronExpression != null
                && !cronExpression.trim().isEmpty();
    }

    private boolean isRetryableDagRunStatus(String status) {
        return "FAILED".equals(status) || "PARTIAL_FAILED".equals(status);
    }

    private int cancelDagRun(SchedulingDagRun run) {
        run.setStatus("CANCELLED");
        run.setEndTime(LocalDateTime.now());
        dagRunRepository.save(run);

        List<SchedulingTaskInstance> instances = taskInstanceRepository.findByDagRunId(run.getId());
        int cancelledInstances = 0;
        for (SchedulingTaskInstance inst : instances) {
            String s = inst.getStatus();
            if ("PENDING".equals(s) || "RESERVED".equals(s) || "RUNNING".equals(s)) {
                inst.setStatus("CANCELLED");
                inst.setLockedBy(null);
                inst.setLockTime(null);
                inst.setScheduledAt(null);
                inst.setNextRetryAt(null);
                cancelledInstances++;
            }
        }
        if (!instances.isEmpty()) {
            taskInstanceRepository.saveAll(instances);
        }
        return cancelledInstances;
    }

    private void retryDagRunInternal(SchedulingDag dag, SchedulingDagRun failedRun) {
        String actor = resolveCurrentActor();
        if (!StringUtils.hasText(actor)) {
            actor = failedRun.getTriggeredBy();
        }
        SchedulingDagRun retryRun = new SchedulingDagRun();
        retryRun.setDagId(dag.getId());
        retryRun.setDagVersionId(failedRun.getDagVersionId());
        retryRun.setRunNo(UUID.randomUUID().toString());
        retryRun.setTenantId(failedRun.getTenantId());
        retryRun.setTriggerType("RETRY");
        retryRun.setTriggeredBy(actor);
        retryRun.setStatus("SCHEDULED");
        retryRun = dagRunRepository.save(retryRun);

        logger.info("开始重试 DAG, dagId: {}, sourceRunId: {}, retryRunId: {}", dag.getId(), failedRun.getId(), retryRun.getId());

        triggerDagExecutionAfterCommit(
                dag,
                buildExecutionContext(
                        retryRun.getTenantId(),
                        dag.getId(),
                        retryRun.getId(),
                        retryRun.getDagVersionId(),
                        "RETRY",
                        actor),
                retryRun.getId(),
                "重试 DAG 执行失败");

        Map<String, Object> detail = new HashMap<>();
        detail.put("retryRunId", retryRun.getId());
        detail.put("sourceRunId", failedRun.getId());
        recordAudit("dag", dag.getId(), "RETRY", detail, dag.getTenantId());
    }

    /**
     * 判断加入边 (fromNodeCode, toNodeCode) 是否会在当前版本的 DAG 中形成环。
     * 策略：加载当前版本所有边并加上新边构建有向图，仅从 toNode 做 DFS（不遍历全图），
     * 若能到达 fromNode 则存在环。图很大时可考虑按 versionId 缓存邻接表并在增删边时失效。
     */
    private boolean wouldCreateCycle(Long versionId, String fromNodeCode, String toNodeCode) {
        List<SchedulingDagEdge> edges = dagEdgeRepository.findByDagVersionId(versionId);
        Map<String, List<String>> adj = new HashMap<>();
        for (SchedulingDagEdge e : edges) {
            adj.computeIfAbsent(e.getFromNodeCode(), k -> new ArrayList<>()).add(e.getToNodeCode());
        }
        adj.computeIfAbsent(fromNodeCode, k -> new ArrayList<>()).add(toNodeCode);

        Set<String> visited = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(toNodeCode);
        while (!stack.isEmpty()) {
            String u = stack.pop();
            if (u.equals(fromNodeCode)) {
                return true;
            }
            if (visited.add(u)) {
                for (String v : adj.getOrDefault(u, Collections.emptyList())) {
                    if (!visited.contains(v)) {
                        stack.push(v);
                    }
                }
            }
        }
        return false;
    }

    /**
     * 解析 KEYED 并发键：parallelGroup 优先，否则 nodeCode，否则 TASK-&lt;taskId&gt;。最长 128 字符。
     */
    private static String resolveConcurrencyKey(SchedulingDagTask node, Long taskId) {
        String key = null;
        if (node.getParallelGroup() != null && !node.getParallelGroup().isBlank()) {
            key = node.getParallelGroup();
        } else if (node.getNodeCode() != null && !node.getNodeCode().isBlank()) {
            key = node.getNodeCode();
        } else if (taskId != null) {
            key = "TASK-" + taskId;
        }
        if (key == null) {
            return null;
        }
        return key.length() > 128 ? key.substring(0, 128) : key;
    }

    /**
     * 使用 Quartz CronExpression 校验，与 Quartz 调度器及前端设计器（Quartz 风格，含 L/? 等）一致。
     */
    private void validateCronExpression(String cronExpression) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return;
        }
        String trimmed = cronExpression.trim();
        if (!CronExpression.isValidExpression(trimmed)) {
            throw SchedulingExceptions.validation("Cron 表达式无效: %s", trimmed);
        }
    }
}
