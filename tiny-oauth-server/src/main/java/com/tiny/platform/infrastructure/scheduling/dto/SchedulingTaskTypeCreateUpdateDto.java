package com.tiny.platform.infrastructure.scheduling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SchedulingTaskTypeCreateUpdateDto {
    private Long id;
    
    private Long tenantId;
    
    @NotBlank(message = "任务类型编码不能为空")
    @Size(max = 128, message = "编码长度不能超过128")
    private String code;
    
    @NotBlank(message = "任务类型名称不能为空")
    @Size(max = 128, message = "名称长度不能超过128")
    private String name;
    
    private String description;
    
    @Size(max = 255, message = "执行器标识长度不能超过255")
    private String executor;
    
    private String paramSchema;
    
    private Integer defaultTimeoutSec = 0;
    
    private Integer defaultMaxRetry = 0;
    
    private Boolean enabled = true;
    
    private String createdBy;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getExecutor() {
        return executor;
    }

    public void setExecutor(String executor) {
        this.executor = executor;
    }

    public String getParamSchema() {
        return paramSchema;
    }

    public void setParamSchema(String paramSchema) {
        this.paramSchema = paramSchema;
    }

    public Integer getDefaultTimeoutSec() {
        return defaultTimeoutSec;
    }

    public void setDefaultTimeoutSec(Integer defaultTimeoutSec) {
        this.defaultTimeoutSec = defaultTimeoutSec;
    }

    public Integer getDefaultMaxRetry() {
        return defaultMaxRetry;
    }

    public void setDefaultMaxRetry(Integer defaultMaxRetry) {
        this.defaultMaxRetry = defaultMaxRetry;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}


