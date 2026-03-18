package com.tiny.platform.infrastructure.scheduling.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DAG 主表实体（scheduling_dag）
 */
@Entity
@Table(name = "scheduling_dag")
public class SchedulingDag implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(length = 128)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Boolean enabled = true;

    /** Cron 表达式，用于定时触发 DAG；持久化后支持重启从 DB 恢复至 Quartz */
    @Column(name = "cron_expression", length = 120)
    private String cronExpression;

    /** Cron 时区（如 Asia/Shanghai），为空则使用系统默认时区 */
    @Column(name = "cron_timezone", length = 64)
    private String cronTimezone;

    /** 是否启用 Cron 调度（与 enabled 独立控制），默认 true */
    @Column(name = "cron_enabled")
    private Boolean cronEnabled = true;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Transient
    private Long currentVersionId;

    @Transient
    private Boolean hasRunningRun;

    @Transient
    private Boolean hasRetryableRun;

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

    @JsonProperty("recordTenantId")
    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public String getCronTimezone() {
        return cronTimezone;
    }

    public void setCronTimezone(String cronTimezone) {
        this.cronTimezone = cronTimezone;
    }

    public Boolean getCronEnabled() {
        return cronEnabled != null ? cronEnabled : true;
    }

    public void setCronEnabled(Boolean cronEnabled) {
        this.cronEnabled = cronEnabled;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
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

    public Long getCurrentVersionId() {
        return currentVersionId;
    }

    public void setCurrentVersionId(Long currentVersionId) {
        this.currentVersionId = currentVersionId;
    }

    public Boolean getHasRunningRun() {
        return hasRunningRun;
    }

    public void setHasRunningRun(Boolean hasRunningRun) {
        this.hasRunningRun = hasRunningRun;
    }

    public Boolean getHasRetryableRun() {
        return hasRetryableRun;
    }

    public void setHasRetryableRun(Boolean hasRetryableRun) {
        this.hasRetryableRun = hasRetryableRun;
    }
}
