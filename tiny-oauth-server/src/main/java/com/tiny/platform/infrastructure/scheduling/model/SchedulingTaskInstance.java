package com.tiny.platform.infrastructure.scheduling.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务实例实体（scheduling_task_instance）
 */
@Entity
@Table(name = "scheduling_task_instance")
public class SchedulingTaskInstance implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dag_run_id")
    private Long dagRunId;

    @Column(name = "dag_id")
    private Long dagId;

    @Column(name = "dag_version_id")
    private Long dagVersionId;

    @Column(name = "node_code", length = 128)
    private String nodeCode;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "attempt_no")
    private Integer attemptNo = 1;

    @Column(length = 32)
    private String status = "PENDING"; // PENDING/RESERVED/RUNNING/SUCCESS/FAILED/SKIPPED/PAUSED

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "locked_by", length = 128)
    private String lockedBy;

    @Column(name = "lock_time")
    private LocalDateTime lockTime;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(columnDefinition = "JSON")
    private String params;

    @Column(columnDefinition = "JSON")
    private String result;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDagRunId() {
        return dagRunId;
    }

    public void setDagRunId(Long dagRunId) {
        this.dagRunId = dagRunId;
    }

    public Long getDagId() {
        return dagId;
    }

    public void setDagId(Long dagId) {
        this.dagId = dagId;
    }

    public Long getDagVersionId() {
        return dagVersionId;
    }

    public void setDagVersionId(Long dagVersionId) {
        this.dagVersionId = dagVersionId;
    }

    public String getNodeCode() {
        return nodeCode;
    }

    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Integer getAttemptNo() {
        return attemptNo;
    }

    public void setAttemptNo(Integer attemptNo) {
        this.attemptNo = attemptNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public void setLockedBy(String lockedBy) {
        this.lockedBy = lockedBy;
    }

    public LocalDateTime getLockTime() {
        return lockTime;
    }

    public void setLockTime(LocalDateTime lockTime) {
        this.lockTime = lockTime;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}


