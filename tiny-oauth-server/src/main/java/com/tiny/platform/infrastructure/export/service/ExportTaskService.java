package com.tiny.platform.infrastructure.export.service;

import com.tiny.platform.infrastructure.export.persistence.ExportTaskEntity;
import com.tiny.platform.infrastructure.export.persistence.ExportTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ExportTaskService —— 任务持久化服务
 *
 * 提供任务创建、状态更新、进度上报、结果保存与过期清理能力。
 */
@Service
public class ExportTaskService {

    private final ExportTaskRepository repository;

    public ExportTaskService(ExportTaskRepository repository) {
        this.repository = repository;
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
            entity.setStatus(ExportTaskStatus.SUCCESS);
            entity.setProgress(100);
            if (totalRows != null) {
                entity.setTotalRows(totalRows);
                entity.setProcessedRows(totalRows);
            }
            entity.setFilePath(filePath);
            entity.setDownloadUrl(downloadUrl);
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
    public List<ExportTaskEntity> findAllTasks() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Transactional(readOnly = true)
    public List<ExportTaskEntity> findPendingTasks() {
        return repository.findByStatusOrderByCreatedAtAsc(ExportTaskStatus.PENDING);
    }

    @Transactional
    public int cleanupExpired(LocalDateTime now) {
        return repository.deleteExpired(now);
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
}