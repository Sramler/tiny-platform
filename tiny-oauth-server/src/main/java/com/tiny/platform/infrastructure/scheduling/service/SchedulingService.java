package com.tiny.platform.infrastructure.scheduling.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.infrastructure.scheduling.dto.*;
import com.tiny.platform.infrastructure.scheduling.exception.SchedulingExceptions;
import com.tiny.platform.infrastructure.scheduling.model.*;
import com.tiny.platform.infrastructure.scheduling.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        this.objectMapper = objectMapper;
    }

    // ==================== TaskType 相关 ====================

    @Transactional
    public SchedulingTaskType createTaskType(SchedulingTaskTypeCreateUpdateDto dto) {
        Long tenantId = dto.getTenantId();
        if (taskTypeRepository.findByTenantIdAndCode(tenantId, dto.getCode()).isPresent()) {
            throw SchedulingExceptions.conflict("任务类型编码已存在: %s", dto.getCode());
        }
        SchedulingTaskType taskType = new SchedulingTaskType();
        taskType.setTenantId(tenantId);
        taskType.setCode(dto.getCode());
        taskType.setName(dto.getName());
        taskType.setDescription(dto.getDescription());
        taskType.setExecutor(dto.getExecutor());
        taskType.setParamSchema(dto.getParamSchema());
        taskType.setDefaultTimeoutSec(dto.getDefaultTimeoutSec() != null ? dto.getDefaultTimeoutSec() : 0);
        taskType.setDefaultMaxRetry(dto.getDefaultMaxRetry() != null ? dto.getDefaultMaxRetry() : 0);
        taskType.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        taskType.setCreatedBy(dto.getCreatedBy());
        SchedulingTaskType saved = taskTypeRepository.save(taskType);
        recordAudit("task_type", saved.getId(), "CREATE", saved, saved.getTenantId());
        return saved;
    }

    @Transactional
    public SchedulingTaskType updateTaskType(Long id, SchedulingTaskTypeCreateUpdateDto dto) {
        SchedulingTaskType taskType = taskTypeRepository.findById(id)
                .orElseThrow(() -> SchedulingExceptions.notFound("任务类型不存在: %s", id));
        if (dto.getCode() != null && !dto.getCode().equals(taskType.getCode())) {
            if (taskTypeRepository.findByTenantIdAndCode(taskType.getTenantId(), dto.getCode()).isPresent()) {
                throw SchedulingExceptions.conflict("任务类型编码已存在: %s", dto.getCode());
            }
            taskType.setCode(dto.getCode());
        }
        if (dto.getName() != null) taskType.setName(dto.getName());
        if (dto.getDescription() != null) taskType.setDescription(dto.getDescription());
        if (dto.getExecutor() != null) taskType.setExecutor(dto.getExecutor());
        if (dto.getParamSchema() != null) taskType.setParamSchema(dto.getParamSchema());
        if (dto.getDefaultTimeoutSec() != null) taskType.setDefaultTimeoutSec(dto.getDefaultTimeoutSec());
        if (dto.getDefaultMaxRetry() != null) taskType.setDefaultMaxRetry(dto.getDefaultMaxRetry());
        if (dto.getEnabled() != null) taskType.setEnabled(dto.getEnabled());
        SchedulingTaskType saved = taskTypeRepository.save(taskType);
        recordAudit("task_type", saved.getId(), "UPDATE", saved, saved.getTenantId());
        return saved;
    }

    @Transactional
    public void deleteTaskType(Long id) {
        SchedulingTaskType taskType = taskTypeRepository.findById(id)
                .orElseThrow(() -> SchedulingExceptions.notFound("任务类型不存在: %s", id));
        // 检查是否有任务在使用
        long count = taskRepository.count((root, query, cb) -> 
                cb.equal(root.get("typeId"), id));
        if (count > 0) {
            throw SchedulingExceptions.operationNotAllowed("该任务类型正在被使用，无法删除");
        }
        taskTypeRepository.delete(taskType);
        recordAudit("task_type", id, "DELETE", Map.of("id", id, "code", taskType.getCode()), taskType.getTenantId());
    }

    public Optional<SchedulingTaskType> getTaskType(Long id) {
        return taskTypeRepository.findById(id);
    }

    public Page<SchedulingTaskType> listTaskTypes(Long tenantId, String code, String name, Pageable pageable) {
        Specification<SchedulingTaskType> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (tenantId != null) {
                predicates.add(cb.equal(root.get("tenantId"), tenantId));
            }
            if (code != null && !code.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("code")), "%" + code.toLowerCase() + "%"));
            }
            if (name != null && !name.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return taskTypeRepository.findAll(spec, pageable);
    }

    // ==================== Task 相关 ====================

    @Transactional
    public SchedulingTask createTask(SchedulingTaskCreateUpdateDto dto) {
        Long tenantId = dto.getTenantId();
        if (dto.getCode() != null && !dto.getCode().isEmpty()) {
            if (taskRepository.findByTenantIdAndCode(tenantId, dto.getCode()).isPresent()) {
            throw SchedulingExceptions.conflict("任务编码已存在: %s", dto.getCode());
            }
        }
        SchedulingTask task = new SchedulingTask();
        task.setTenantId(tenantId);
        task.setTypeId(dto.getTypeId());
        task.setCode(dto.getCode());
        task.setName(dto.getName() != null ? dto.getName() : "");
        task.setDescription(dto.getDescription());
        task.setParams(dto.getParams());
        task.setTimeoutSec(dto.getTimeoutSec());
        task.setMaxRetry(dto.getMaxRetry() != null ? dto.getMaxRetry() : 0);
        task.setRetryPolicy(dto.getRetryPolicy());
        task.setConcurrencyPolicy(dto.getConcurrencyPolicy() != null ? dto.getConcurrencyPolicy() : "PARALLEL");
        task.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        task.setCreatedBy(dto.getCreatedBy());
        SchedulingTask saved = taskRepository.save(task);
        recordAudit("task", saved.getId(), "CREATE", saved, saved.getTenantId());
        return saved;
    }

    @Transactional
    public SchedulingTask updateTask(Long id, SchedulingTaskCreateUpdateDto dto) {
        SchedulingTask task = taskRepository.findById(id)
                .orElseThrow(() -> SchedulingExceptions.notFound("任务不存在: %s", id));
        if (dto.getCode() != null && !dto.getCode().equals(task.getCode())) {
            if (taskRepository.findByTenantIdAndCode(task.getTenantId(), dto.getCode()).isPresent()) {
                throw SchedulingExceptions.conflict("任务编码已存在: %s", dto.getCode());
            }
            task.setCode(dto.getCode());
        }
        if (dto.getName() != null) task.setName(dto.getName());
        if (dto.getDescription() != null) task.setDescription(dto.getDescription());
        if (dto.getParams() != null) task.setParams(dto.getParams());
        if (dto.getTimeoutSec() != null) task.setTimeoutSec(dto.getTimeoutSec());
        if (dto.getMaxRetry() != null) task.setMaxRetry(dto.getMaxRetry());
        if (dto.getRetryPolicy() != null) task.setRetryPolicy(dto.getRetryPolicy());
        if (dto.getConcurrencyPolicy() != null) task.setConcurrencyPolicy(dto.getConcurrencyPolicy());
        if (dto.getEnabled() != null) task.setEnabled(dto.getEnabled());
        SchedulingTask saved = taskRepository.save(task);
        recordAudit("task", saved.getId(), "UPDATE", saved, saved.getTenantId());
        return saved;
    }

    @Transactional
    public void deleteTask(Long id) {
        SchedulingTask task = taskRepository.findById(id)
                .orElseThrow(() -> SchedulingExceptions.notFound("任务不存在: %s", id));
        // 检查是否有 DAG 节点在使用
        List<SchedulingDagTask> usingNodes = dagTaskRepository.findByTaskId(id);
        if (!usingNodes.isEmpty()) {
            throw SchedulingExceptions.operationNotAllowed("该任务正在被 DAG 使用，无法删除");
        }
        taskRepository.delete(task);
        recordAudit("task", id, "DELETE", Map.of("id", id, "code", task.getCode()), task.getTenantId());
    }

    public Optional<SchedulingTask> getTask(Long id) {
        return taskRepository.findById(id);
    }

    public Page<SchedulingTask> listTasks(Long tenantId, Long typeId, String code, String name, Pageable pageable) {
        Specification<SchedulingTask> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (tenantId != null) {
                predicates.add(cb.equal(root.get("tenantId"), tenantId));
            }
            if (typeId != null) {
                predicates.add(cb.equal(root.get("typeId"), typeId));
            }
            if (code != null && !code.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("code")), "%" + code.toLowerCase() + "%"));
            }
            if (name != null && !name.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
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
            if (detail != null) {
                audit.setDetail(objectMapper.writeValueAsString(detail));
            }
            auditRepository.save(audit);
        } catch (Exception e) {
            logger.warn("记录审计日志失败, objectType: {}, action: {}, error: {}", objectType, action, e.getMessage());
        }
    }

    // ==================== DAG 相关 ====================

    @Transactional
    public SchedulingDag createDag(SchedulingDagCreateUpdateDto dto) {
        Long tenantId = dto.getTenantId();
        if (dto.getCode() != null && !dto.getCode().isEmpty()) {
            if (dagRepository.findByTenantIdAndCode(tenantId, dto.getCode()).isPresent()) {
                throw SchedulingExceptions.conflict("DAG编码已存在: %s", dto.getCode());
            }
        }
        validateCronExpression(dto.getCronExpression());
        SchedulingDag dag = new SchedulingDag();
        dag.setTenantId(tenantId);
        dag.setCode(dto.getCode());
        dag.setName(dto.getName());
        dag.setDescription(dto.getDescription());
        dag.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        dag.setCreatedBy(dto.getCreatedBy());
        dag = dagRepository.save(dag);
        
        // 如果有 Cron 表达式，创建定时调度的 Quartz Job
        if (dto.getCronExpression() != null && !dto.getCronExpression().trim().isEmpty()) {
            try {
                quartzSchedulerService.createOrUpdateDagJob(dag, dto.getCronExpression());
            } catch (Exception e) {
                throw SchedulingExceptions.systemError("创建定时调度失败: %s", e, e.getMessage());
            }
        }
        
        recordAudit("dag", dag.getId(), "CREATE", dag, dag.getTenantId());
        return dag;
    }

    @Transactional
    public SchedulingDag updateDag(Long id, SchedulingDagCreateUpdateDto dto) {
        SchedulingDag dag = dagRepository.findById(id)
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG不存在: %s", id));
        if (dto.getCode() != null && !dto.getCode().equals(dag.getCode())) {
            if (dagRepository.findByTenantIdAndCode(dag.getTenantId(), dto.getCode()).isPresent()) {
                throw SchedulingExceptions.conflict("DAG编码已存在: %s", dto.getCode());
            }
            dag.setCode(dto.getCode());
        }
        if (dto.getName() != null) dag.setName(dto.getName());
        if (dto.getDescription() != null) dag.setDescription(dto.getDescription());
        if (dto.getEnabled() != null) dag.setEnabled(dto.getEnabled());
        dag = dagRepository.save(dag);
        
        validateCronExpression(dto.getCronExpression());
        // 更新 Cron 表达式（如果有）
        if (dto.getCronExpression() != null) {
            try {
                if (dto.getCronExpression().trim().isEmpty()) {
                    // 如果 Cron 表达式为空，删除定时调度
                    quartzSchedulerService.deleteDagJob(id);
                } else {
                    // 更新或创建定时调度
                    quartzSchedulerService.createOrUpdateDagJob(dag, dto.getCronExpression());
                }
            } catch (Exception e) {
                throw SchedulingExceptions.systemError("更新定时调度失败: %s", e, e.getMessage());
            }
        }
        
        recordAudit("dag", dag.getId(), "UPDATE", dag, dag.getTenantId());
        return dag;
    }

    @Transactional
    public void deleteDag(Long id) {
        SchedulingDag dag = dagRepository.findById(id)
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG不存在: %s", id));
        
        // 删除 Quartz Job
        try {
            quartzSchedulerService.deleteDagJob(id);
        } catch (Exception e) {
            // 记录日志但不阻止删除
            System.err.println("删除 DAG Quartz Job 失败: " + e.getMessage());
        }
        
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
        return dagRepository.findById(id);
    }

    public Page<SchedulingDag> listDags(Long tenantId, String code, String name, Pageable pageable) {
        Specification<SchedulingDag> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (tenantId != null) {
                predicates.add(cb.equal(root.get("tenantId"), tenantId));
            }
            if (code != null && !code.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("code")), "%" + code.toLowerCase() + "%"));
            }
            if (name != null && !name.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return dagRepository.findAll(spec, pageable);
    }

    // ==================== DAG Version 相关 ====================

    @Transactional
    public SchedulingDagVersion createDagVersion(Long dagId, SchedulingDagVersionCreateUpdateDto dto) {
        dagRepository.findById(dagId)
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG不存在: %s", dagId));
        Integer maxVersion = dagVersionRepository.findMaxVersionNoByDagId(dagId);
        int nextVersion = (maxVersion != null ? maxVersion : 0) + 1;
        SchedulingDagVersion version = new SchedulingDagVersion();
        version.setDagId(dagId);
        version.setVersionNo(nextVersion);
        version.setStatus(dto.getStatus() != null ? dto.getStatus() : "DRAFT");
        version.setDefinition(dto.getDefinition());
        version.setCreatedBy(dto.getCreatedBy());
        SchedulingDagVersion saved = dagVersionRepository.save(version);
        recordAudit("dag_version", saved.getId(), "CREATE", saved, saved.getDagId() != null ? dagRepository.findById(saved.getDagId()).map(SchedulingDag::getTenantId).orElse(null) : null);
        return saved;
    }

    @Transactional
    public SchedulingDagVersion updateDagVersion(Long dagId, Long versionId, SchedulingDagVersionCreateUpdateDto dto) {
        SchedulingDagVersion version = dagVersionRepository.findById(versionId)
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG版本不存在: %s", versionId));
        if (!version.getDagId().equals(dagId)) {
            throw SchedulingExceptions.validation("版本不属于该 DAG");
        }
        if (dto.getStatus() != null) {
            version.setStatus(dto.getStatus());
            if ("ACTIVE".equals(dto.getStatus())) {
                // 激活时，将其他版本设为 ARCHIVED
                List<SchedulingDagVersion> activeVersions = dagVersionRepository.findByDagId(dagId).stream()
                        .filter(v -> "ACTIVE".equals(v.getStatus()) && !v.getId().equals(versionId))
                        .collect(Collectors.toList());
                for (SchedulingDagVersion v : activeVersions) {
                    v.setStatus("ARCHIVED");
                    dagVersionRepository.save(v);
                }
                version.setActivatedAt(LocalDateTime.now());
            }
        }
        if (dto.getDefinition() != null) version.setDefinition(dto.getDefinition());
        SchedulingDagVersion saved = dagVersionRepository.save(version);
        Long tenantId = dagRepository.findById(saved.getDagId()).map(SchedulingDag::getTenantId).orElse(null);
        recordAudit("dag_version", saved.getId(), "UPDATE", saved, tenantId);
        return saved;
    }

    public Optional<SchedulingDagVersion> getDagVersion(Long dagId, Long versionId) {
        return dagVersionRepository.findById(versionId)
                .filter(v -> v.getDagId().equals(dagId));
    }

    public List<SchedulingDagVersion> listDagVersions(Long dagId) {
        return dagVersionRepository.findByDagId(dagId);
    }

    // ==================== DAG Node 相关 ====================

    @Transactional
    public SchedulingDagTask createDagNode(Long dagId, Long versionId, SchedulingDagTaskCreateUpdateDto dto) {
        getDagVersion(dagId, versionId)
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG版本不存在: %s", versionId));
        if (dagTaskRepository.findByDagVersionIdAndNodeCode(versionId, dto.getNodeCode()).isPresent()) {
            throw SchedulingExceptions.conflict("节点编码已存在: %s", dto.getNodeCode());
        }
        SchedulingDagTask node = new SchedulingDagTask();
        node.setDagVersionId(versionId);
        node.setNodeCode(dto.getNodeCode());
        node.setTaskId(dto.getTaskId());
        node.setName(dto.getName());
        node.setOverrideParams(dto.getOverrideParams());
        node.setTimeoutSec(dto.getTimeoutSec());
        node.setMaxRetry(dto.getMaxRetry());
        node.setParallelGroup(dto.getParallelGroup());
        node.setMeta(dto.getMeta());
        SchedulingDagTask saved = dagTaskRepository.save(node);
        SchedulingDag dag = dagRepository.findById(dagId).orElse(null);
        recordAudit("dag_node", saved.getId(), "CREATE", saved, dag != null ? dag.getTenantId() : null);
        return saved;
    }

    @Transactional
    public SchedulingDagTask updateDagNode(Long dagId, Long versionId, Long nodeId, SchedulingDagTaskCreateUpdateDto dto) {
        SchedulingDagTask node = dagTaskRepository.findById(nodeId)
                .orElseThrow(() -> SchedulingExceptions.notFound("节点不存在: %s", nodeId));
        if (!node.getDagVersionId().equals(versionId)) {
            throw SchedulingExceptions.validation("节点不属于该版本");
        }
        if (dto.getNodeCode() != null && !dto.getNodeCode().equals(node.getNodeCode())) {
            if (dagTaskRepository.findByDagVersionIdAndNodeCode(versionId, dto.getNodeCode()).isPresent()) {
                throw SchedulingExceptions.conflict("节点编码已存在: %s", dto.getNodeCode());
            }
            node.setNodeCode(dto.getNodeCode());
        }
        if (dto.getTaskId() != null) node.setTaskId(dto.getTaskId());
        if (dto.getName() != null) node.setName(dto.getName());
        if (dto.getOverrideParams() != null) node.setOverrideParams(dto.getOverrideParams());
        if (dto.getTimeoutSec() != null) node.setTimeoutSec(dto.getTimeoutSec());
        if (dto.getMaxRetry() != null) node.setMaxRetry(dto.getMaxRetry());
        if (dto.getParallelGroup() != null) node.setParallelGroup(dto.getParallelGroup());
        if (dto.getMeta() != null) node.setMeta(dto.getMeta());
        SchedulingDagTask saved = dagTaskRepository.save(node);
        SchedulingDag dag = dagRepository.findById(dagId).orElse(null);
        recordAudit("dag_node", saved.getId(), "UPDATE", saved, dag != null ? dag.getTenantId() : null);
        return saved;
    }

    @Transactional
    public void deleteDagNode(Long dagId, Long versionId, Long nodeId) {
        SchedulingDagTask node = dagTaskRepository.findById(nodeId)
                .orElseThrow(() -> SchedulingExceptions.notFound("节点不存在: %s", nodeId));
        if (!node.getDagVersionId().equals(versionId)) {
            throw SchedulingExceptions.validation("节点不属于该版本");
        }
        // 删除相关边
        dagEdgeRepository.findByDagVersionId(versionId).stream()
                .filter(e -> e.getFromNodeCode().equals(node.getNodeCode()) || e.getToNodeCode().equals(node.getNodeCode()))
                .forEach(dagEdgeRepository::delete);
        dagTaskRepository.delete(node);
        SchedulingDag dag = dagRepository.findById(dagId).orElse(null);
        recordAudit("dag_node", nodeId, "DELETE",
                Map.of("dagId", dagId, "nodeCode", node.getNodeCode()), dag != null ? dag.getTenantId() : null);
    }

    public Optional<SchedulingDagTask> getDagNode(Long dagId, Long versionId, Long nodeId) {
        return dagTaskRepository.findById(nodeId)
                .filter(n -> n.getDagVersionId().equals(versionId));
    }

    public List<SchedulingDagTask> getDagNodes(Long dagId, Long versionId) {
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
        getDagVersion(dagId, versionId)
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG版本不存在: %s", versionId));
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
        SchedulingDagEdge edge = new SchedulingDagEdge();
        edge.setDagVersionId(versionId);
        edge.setFromNodeCode(dto.getFromNodeCode());
        edge.setToNodeCode(dto.getToNodeCode());
        edge.setCondition(dto.getCondition());
        SchedulingDagEdge saved = dagEdgeRepository.save(edge);
        SchedulingDag dag = dagRepository.findById(dagId).orElse(null);
        recordAudit("dag_edge", saved.getId(), "CREATE", saved, dag != null ? dag.getTenantId() : null);
        return saved;
    }

    @Transactional
    public void deleteDagEdge(Long dagId, Long versionId, Long edgeId) {
        SchedulingDagEdge edge = dagEdgeRepository.findById(edgeId)
                .orElseThrow(() -> SchedulingExceptions.notFound("依赖关系不存在: %s", edgeId));
        if (!edge.getDagVersionId().equals(versionId)) {
            throw SchedulingExceptions.validation("依赖关系不属于该版本");
        }
        dagEdgeRepository.delete(edge);
        SchedulingDag dag = dagRepository.findById(dagId).orElse(null);
        recordAudit("dag_edge", edgeId, "DELETE",
                Map.of("dagId", dagId, "from", edge.getFromNodeCode(), "to", edge.getToNodeCode()),
                dag != null ? dag.getTenantId() : null);
    }

    public List<SchedulingDagEdge> getDagEdges(Long dagId, Long versionId) {
        getDagVersion(dagId, versionId)
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG版本不存在: %s", versionId));
        return dagEdgeRepository.findByDagVersionId(versionId);
    }

    // ==================== DAG 调度触发/控制 ====================

    @Transactional
    public SchedulingDagRun triggerDag(Long dagId, String triggeredBy) {
        SchedulingDag dag = dagRepository.findById(dagId)
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG不存在: %s", dagId));
        if (!dag.getEnabled()) {
            throw SchedulingExceptions.operationNotAllowed("DAG已禁用，无法触发");
        }
        // 获取当前激活版本
        SchedulingDagVersion version = dagVersionRepository.findByDagIdAndStatus(dagId, "ACTIVE")
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG没有激活版本"));
        
        // 先创建运行实例
        SchedulingDagRun run = new SchedulingDagRun();
        run.setDagId(dagId);
        run.setDagVersionId(version.getId());
        run.setRunNo(UUID.randomUUID().toString());
        run.setTenantId(dag.getTenantId());
        run.setTriggerType("MANUAL");
        run.setTriggeredBy(triggeredBy);
        run.setStatus("SCHEDULED");
        run = dagRunRepository.save(run);

        logger.info("手动触发 DAG, dagId: {}, runId: {}, triggeredBy: {}", dagId, run.getId(), triggeredBy);

        // 然后使用 Quartz 立即触发 DAG 执行，传递 dagRunId 和 dagVersionId
        try {
            quartzSchedulerService.triggerDagNow(dag, run.getId(), version.getId());
        } catch (Exception e) {
            // 如果触发失败，更新 run 状态为失败
            run.setStatus("FAILED");
            dagRunRepository.save(run);
            logger.error("触发 DAG 执行失败, dagId: {}, runId: {}, message: {}", dagId, run.getId(), e.getMessage(), e);
            throw SchedulingExceptions.systemError("触发 DAG 执行失败: %s", e, e.getMessage());
        }
        
        Map<String, Object> detail = new HashMap<>();
        detail.put("runId", run.getId());
        if (triggeredBy != null) {
            detail.put("triggeredBy", triggeredBy);
        }
        recordAudit("dag", dagId, "TRIGGER", detail, dag.getTenantId());

        return run;
    }

    @Transactional
    public void pauseDag(Long dagId) {
        SchedulingDag dag = dagRepository.findById(dagId)
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG不存在: %s", dagId));
        dag.setEnabled(false);
        dagRepository.save(dag);
        
        // 暂停 Quartz Job
        try {
            quartzSchedulerService.pauseDagJob(dagId);
        } catch (Exception e) {
            throw SchedulingExceptions.systemError("暂停 DAG Job 失败: %s", e, e.getMessage());
        }

        recordAudit("dag", dagId, "PAUSE", Map.of("enabled", false), dag.getTenantId());
    }

    @Transactional
    public void resumeDag(Long dagId) {
        SchedulingDag dag = dagRepository.findById(dagId)
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG不存在: %s", dagId));
        dag.setEnabled(true);
        dagRepository.save(dag);
        
        // 恢复 Quartz Job
        try {
            quartzSchedulerService.resumeDagJob(dagId);
        } catch (Exception e) {
            throw SchedulingExceptions.systemError("恢复 DAG Job 失败: %s", e, e.getMessage());
        }

        recordAudit("dag", dagId, "RESUME", Map.of("enabled", true), dag.getTenantId());
    }

    @Transactional
    public void stopDag(Long dagId) {
        SchedulingDag dag = dagRepository.findById(dagId)
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG不存在: %s", dagId));
        
        // 停止 Quartz Job
        try {
            quartzSchedulerService.pauseDagJob(dagId);
        } catch (Exception e) {
            throw SchedulingExceptions.systemError("停止 DAG Job 失败: %s", e, e.getMessage());
        }
        
        // 停止所有运行中的实例
        List<SchedulingDagRun> runningRuns = dagRunRepository.findByDagIdAndStatus(dagId, "RUNNING");
        for (SchedulingDagRun run : runningRuns) {
            run.setStatus("CANCELLED");
            run.setEndTime(LocalDateTime.now());
            dagRunRepository.save(run);
        }

        recordAudit("dag", dagId, "STOP", Map.of("cancelledRuns", runningRuns.size()), dag.getTenantId());
    }

    @Transactional
    public void retryDag(Long dagId) {
        SchedulingDag dag = dagRepository.findById(dagId)
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG不存在: %s", dagId));
        if (!dag.getEnabled()) {
            throw SchedulingExceptions.operationNotAllowed("DAG已禁用，无法重试");
        }
        
        // 找到失败的运行实例，创建新的重试
        List<SchedulingDagRun> failedRuns = dagRunRepository.findByDagIdAndStatus(dagId, "FAILED");
        for (SchedulingDagRun failedRun : failedRuns) {
            SchedulingDagRun retryRun = new SchedulingDagRun();
            retryRun.setDagId(dagId);
            retryRun.setDagVersionId(failedRun.getDagVersionId());
            retryRun.setRunNo(UUID.randomUUID().toString());
            retryRun.setTenantId(failedRun.getTenantId());
            retryRun.setTriggerType("RETRY");
            retryRun.setTriggeredBy(failedRun.getTriggeredBy());
            retryRun.setStatus("SCHEDULED");
            retryRun = dagRunRepository.save(retryRun);

            logger.info("开始重试 DAG, dagId: {}, sourceRunId: {}, retryRunId: {}", dagId, failedRun.getId(), retryRun.getId());

            // 触发 Quartz Job 执行重试
            try {
                quartzSchedulerService.triggerDagNow(dag, retryRun.getId(), retryRun.getDagVersionId());
            } catch (Exception e) {
                retryRun.setStatus("FAILED");
                dagRunRepository.save(retryRun);
                logger.error("重试 DAG 执行失败, dagId: {}, sourceRunId: {}, retryRunId: {}, message: {}",
                        dagId, failedRun.getId(), retryRun.getId(), e.getMessage(), e);
                throw SchedulingExceptions.systemError("重试 DAG 执行失败: %s", e, e.getMessage());
            }

            Map<String, Object> detail = new HashMap<>();
            detail.put("retryRunId", retryRun.getId());
            detail.put("sourceRunId", failedRun.getId());
            recordAudit("dag", dagId, "RETRY", detail, dag.getTenantId());
        }
    }

    /**
     * 执行 DAG（由 Quartz Job 调用，确保事务一致性）
     * @param dagId DAG ID
     * @param dagRunId DAG 运行实例 ID（手动触发时传递，定时触发时为 null）
     * @param dagVersionId DAG 版本 ID（手动触发时传递，定时触发时为 null）
     */
    @Transactional
    public void executeDag(Long dagId, Long dagRunId, Long dagVersionId) {
        boolean isManualTrigger = (dagRunId != null && dagRunId > 0);
        logger.info("开始执行 DAG, dagId: {}, dagRunId: {}, isManualTrigger: {}", dagId, dagRunId, isManualTrigger);

        SchedulingDag dag = dagRepository.findById(dagId)
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG不存在: %s", dagId));

        if (!dag.getEnabled()) {
            logger.warn("DAG已禁用，跳过执行, dagId: {}", dagId);
            if (isManualTrigger && dagRunId != null && dagRunId > 0) {
                dagRunRepository.findById(dagRunId).ifPresent(run -> {
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
            run = dagRunRepository.findById(dagRunId)
                    .orElseThrow(() -> SchedulingExceptions.notFound("DAG运行实例不存在: %s", dagRunId));
            Long versionId = (dagVersionId != null && dagVersionId > 0) ? dagVersionId : run.getDagVersionId();
            if (versionId == null) {
                throw SchedulingExceptions.validation("DAG版本ID不能为空");
            }
            version = dagVersionRepository.findById(versionId)
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
            run.setRunNo("RUN-" + System.currentTimeMillis());
            run.setTenantId(dag.getTenantId());
            run.setTriggerType("SCHEDULE");
            run.setTriggeredBy("Quartz Scheduler");
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

        for (SchedulingDagTask node : nodes) {
            SchedulingTaskInstance instance = new SchedulingTaskInstance();
            instance.setDagRunId(run.getId());
            instance.setDagId(run.getDagId());
            instance.setDagVersionId(version.getId());
            instance.setNodeCode(node.getNodeCode());
            instance.setTaskId(node.getTaskId());
            instance.setTenantId(run.getTenantId());
            instance.setAttemptNo(1);

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
        // 1. 获取节点定义
        SchedulingDagTask node = dagTaskRepository.findById(nodeId)
                .orElseThrow(() -> SchedulingExceptions.notFound("节点不存在: %s", nodeId));
        
        // 2. 验证节点属于指定的 DAG
        SchedulingDagVersion version = dagVersionRepository.findById(node.getDagVersionId())
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG版本不存在: %s", node.getDagVersionId()));
        if (!version.getDagId().equals(dagId)) {
            throw SchedulingExceptions.validation("节点不属于指定的 DAG");
        }
        SchedulingDag dag = dagRepository.findById(dagId)
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG不存在: %s", dagId));
        
        // 3. 获取或创建最新的 DAG Run
        SchedulingDagRun run = dagRunRepository.findByDagIdAndStatus(dagId, "RUNNING")
                .stream()
                .findFirst()
                .orElseGet(() -> {
                    SchedulingDagRun newRun = new SchedulingDagRun();
                    newRun.setDagId(dagId);
                    newRun.setDagVersionId(version.getId());
                    newRun.setRunNo(UUID.randomUUID().toString());
                    newRun.setTenantId(dag.getTenantId());
                    newRun.setTriggerType("MANUAL");
                    newRun.setTriggeredBy("Node Trigger");
                    newRun.setStatus("RUNNING");
                    newRun.setStartTime(LocalDateTime.now());
                    return dagRunRepository.save(newRun);
                });
        
        // 4. 查找或创建该节点的任务实例
        List<SchedulingTaskInstance> instances = taskInstanceRepository
                .findByDagRunIdAndNodeCode(run.getId(), node.getNodeCode());
        
        SchedulingTaskInstance instance;
        if (instances.isEmpty()) {
            // 创建新的任务实例
            instance = new SchedulingTaskInstance();
            instance.setDagRunId(run.getId());
            instance.setDagId(dagId);
            instance.setDagVersionId(version.getId());
            instance.setNodeCode(node.getNodeCode());
            instance.setTaskId(node.getTaskId());
            instance.setTenantId(run.getTenantId());
            instance.setAttemptNo(1);
            instance.setStatus("PENDING");
            instance.setScheduledAt(LocalDateTime.now());
            instance.setParams(node.getOverrideParams());
            instance = taskInstanceRepository.save(instance);
        } else {
            // 使用已存在的实例（取最新的）
            instance = instances.get(instances.size() - 1);
            if (!"PENDING".equals(instance.getStatus()) && !"FAILED".equals(instance.getStatus())) {
                throw SchedulingExceptions.operationNotAllowed("节点任务实例状态不允许触发: %s", instance.getStatus());
            }
            instance.setStatus("PENDING");
            instance.setScheduledAt(LocalDateTime.now());
            instance.setAttemptNo(1);
            instance = taskInstanceRepository.save(instance);
        }
        
        logger.info("节点触发成功, dagId: {}, nodeId: {}, instanceId: {}", dagId, nodeId, instance.getId());
        Map<String, Object> detail = new HashMap<>();
        detail.put("dagRunId", run.getId());
        detail.put("instanceId", instance.getId());
        recordAudit("dag_node", nodeId, "TRIGGER_NODE", detail, dag.getTenantId());
    }

    @Transactional
    public void retryNode(Long dagId, Long nodeId) {
        // 1. 获取节点定义
        SchedulingDagTask node = dagTaskRepository.findById(nodeId)
                .orElseThrow(() -> SchedulingExceptions.notFound("节点不存在: %s", nodeId));
        
        // 2. 验证节点属于指定的 DAG
        SchedulingDagVersion version = dagVersionRepository.findById(node.getDagVersionId())
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG版本不存在: %s", node.getDagVersionId()));
        if (!version.getDagId().equals(dagId)) {
            throw SchedulingExceptions.validation("节点不属于指定的 DAG");
        }
        SchedulingDag dag = dagRepository.findById(dagId)
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG不存在: %s", dagId));
        
        // 3. 查找失败的任务实例
        List<SchedulingDagRun> runs = dagRunRepository.findByDagId(dagId);
        for (SchedulingDagRun run : runs) {
            List<SchedulingTaskInstance> instances = taskInstanceRepository
                    .findByDagRunIdAndNodeCode(run.getId(), node.getNodeCode());
            
            for (SchedulingTaskInstance instance : instances) {
                if ("FAILED".equals(instance.getStatus())) {
                    // 创建新的重试实例
                    SchedulingTaskInstance retryInstance = new SchedulingTaskInstance();
                    retryInstance.setDagRunId(run.getId());
                    retryInstance.setDagId(dagId);
                    retryInstance.setDagVersionId(version.getId());
                    retryInstance.setNodeCode(node.getNodeCode());
                    retryInstance.setTaskId(node.getTaskId());
                    retryInstance.setTenantId(run.getTenantId());
                    retryInstance.setAttemptNo(instance.getAttemptNo() + 1);
                    retryInstance.setStatus("PENDING");
                    retryInstance.setScheduledAt(LocalDateTime.now());
                    retryInstance.setParams(node.getOverrideParams());
                    taskInstanceRepository.save(retryInstance);
                    
                    logger.info("节点重试成功, dagId: {}, nodeId: {}, instanceId: {}", 
                            dagId, nodeId, retryInstance.getId());
                    Map<String, Object> detail = new HashMap<>();
                    detail.put("dagRunId", run.getId());
                    detail.put("instanceId", retryInstance.getId());
                    recordAudit("dag_node", nodeId, "RETRY_NODE", detail, dag.getTenantId());
                    return;
                }
            }
        }
        
        throw SchedulingExceptions.notFound("未找到失败的任务实例");
    }

    @Transactional
    public void pauseNode(Long dagId, Long nodeId) {
        // 1. 获取节点定义
        SchedulingDagTask node = dagTaskRepository.findById(nodeId)
                .orElseThrow(() -> SchedulingExceptions.notFound("节点不存在: %s", nodeId));
        
        // 2. 验证节点属于指定的 DAG
        SchedulingDagVersion version = dagVersionRepository.findById(node.getDagVersionId())
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG版本不存在: %s", node.getDagVersionId()));
        if (!version.getDagId().equals(dagId)) {
            throw SchedulingExceptions.validation("节点不属于指定的 DAG");
        }
        
        SchedulingDag dag = dagRepository.findById(dagId)
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG不存在: %s", dagId));
        
        // 3. 查找并暂停该节点的所有运行中的任务实例
        List<SchedulingDagRun> runs = dagRunRepository.findByDagId(dagId);
        int pausedCount = 0;
        for (SchedulingDagRun run : runs) {
            List<SchedulingTaskInstance> instances = taskInstanceRepository
                    .findByDagRunIdAndNodeCode(run.getId(), node.getNodeCode());
            
            for (SchedulingTaskInstance instance : instances) {
                if ("PENDING".equals(instance.getStatus()) || "RESERVED".equals(instance.getStatus())) {
                    instance.setLockedBy(null);
                    instance.setLockTime(null);
                    instance.setStatus("PAUSED");
                    taskInstanceRepository.save(instance);
                    pausedCount++;
                } else if ("PAUSED".equals(instance.getStatus())) {
                    instance.setStatus("SKIPPED");
                    taskInstanceRepository.save(instance);
                }
            }
        }
        
        if (pausedCount == 0) {
            throw SchedulingExceptions.notFound("未找到可暂停的任务实例");
        }
        
        logger.info("节点暂停成功, dagId: {}, nodeId: {}, 暂停实例数: {}", dagId, nodeId, pausedCount);
        recordAudit("dag_node", nodeId, "PAUSE_NODE",
                Map.of("pausedInstances", pausedCount), dag.getTenantId());
    }

    @Transactional
    public void resumeNode(Long dagId, Long nodeId) {
        // 1. 获取节点定义
        SchedulingDagTask node = dagTaskRepository.findById(nodeId)
                .orElseThrow(() -> SchedulingExceptions.notFound("节点不存在: %s", nodeId));
        
        // 2. 验证节点属于指定的 DAG
        SchedulingDagVersion version = dagVersionRepository.findById(node.getDagVersionId())
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG版本不存在: %s", node.getDagVersionId()));
        if (!version.getDagId().equals(dagId)) {
            throw SchedulingExceptions.validation("节点不属于指定的 DAG");
        }
        
        SchedulingDag dag = dagRepository.findById(dagId)
                .orElseThrow(() -> SchedulingExceptions.notFound("DAG不存在: %s", dagId));
        
        // 3. 查找并恢复该节点的所有被跳过的任务实例
        List<SchedulingDagRun> runs = dagRunRepository.findByDagId(dagId);
        int resumedCount = 0;
        for (SchedulingDagRun run : runs) {
            List<SchedulingTaskInstance> instances = taskInstanceRepository
                    .findByDagRunIdAndNodeCode(run.getId(), node.getNodeCode());
            
            for (SchedulingTaskInstance instance : instances) {
                if ("PAUSED".equals(instance.getStatus()) || "SKIPPED".equals(instance.getStatus())) {
                    instance.setStatus("PENDING");
                    instance.setScheduledAt(LocalDateTime.now());
                    taskInstanceRepository.save(instance);
                    resumedCount++;
                }
            }
        }
        
        if (resumedCount == 0) {
            throw SchedulingExceptions.notFound("未找到可恢复的任务实例");
        }
        
        logger.info("节点恢复成功, dagId: {}, nodeId: {}, 恢复实例数: {}", dagId, nodeId, resumedCount);
        recordAudit("dag_node", nodeId, "RESUME_NODE",
                Map.of("resumedInstances", resumedCount), dag.getTenantId());
    }

    // ==================== 运行历史 ====================

    public Page<SchedulingDagRun> getDagRuns(Long dagId, Pageable pageable) {
        Specification<SchedulingDagRun> spec = (root, query, cb) -> 
                cb.equal(root.get("dagId"), dagId);
        return dagRunRepository.findAll(spec, pageable);
    }

    public Optional<SchedulingDagRun> getDagRun(Long dagId, Long runId) {
        return dagRunRepository.findById(runId)
                .filter(r -> r.getDagId().equals(dagId));
    }

    public List<SchedulingTaskInstance> getDagRunNodes(Long dagId, Long runId) {
        return taskInstanceRepository.findByDagRunId(runId);
    }

    public Optional<SchedulingTaskInstance> getDagRunNode(Long dagId, Long runId, Long nodeId) {
        return taskInstanceRepository.findById(nodeId)
                .filter(instance -> instance.getDagRunId().equals(runId) && instance.getDagId().equals(dagId));
    }

    public String getTaskInstanceLog(Long instanceId) {
        return taskInstanceRepository.findById(instanceId)
                .map(instance -> {
                    // 从历史记录中获取日志
                    List<SchedulingTaskHistory> histories = taskHistoryRepository.findByTaskInstanceId(instanceId);
                    if (histories.isEmpty()) {
                        return "暂无日志";
                    }
                    SchedulingTaskHistory latest = histories.get(histories.size() - 1);
                    if (latest.getLogPath() != null) {
                        return "日志路径: " + latest.getLogPath();
                    }
                    if ("SUCCESS".equals(latest.getStatus())) {
                        return latest.getResult() != null ? latest.getResult() : "执行成功";
                    } else {
                        return (latest.getErrorMessage() != null ? latest.getErrorMessage() : "") + 
                               (latest.getStackTrace() != null ? "\n" + latest.getStackTrace() : "");
                    }
                })
                .orElse("任务实例不存在");
    }

    public Optional<SchedulingTaskHistory> getTaskHistory(Long historyId) {
        return taskHistoryRepository.findById(historyId);
    }

    // ==================== 审计与监控 ====================

    public Page<SchedulingAudit> listAudits(Long tenantId, String objectType, String action, Pageable pageable) {
        Specification<SchedulingAudit> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (tenantId != null) {
                predicates.add(cb.equal(root.get("tenantId"), tenantId));
            }
            if (objectType != null && !objectType.isEmpty()) {
                predicates.add(cb.equal(root.get("objectType"), objectType));
            }
            if (action != null && !action.isEmpty()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return auditRepository.findAll(spec, pageable);
    }

    private void validateCronExpression(String cronExpression) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return;
        }
        String trimmed = cronExpression.trim();
        try {
            CronExpression.parse(trimmed);
        } catch (IllegalArgumentException e) {
            throw SchedulingExceptions.validation("Cron 表达式无效: %s", trimmed);
        }
    }
}

