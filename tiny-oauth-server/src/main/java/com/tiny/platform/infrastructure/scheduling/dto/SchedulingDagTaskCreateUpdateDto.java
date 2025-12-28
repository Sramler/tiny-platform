package com.tiny.platform.infrastructure.scheduling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SchedulingDagTaskCreateUpdateDto {
    private Long id;
    
    private Long dagVersionId;
    
    @NotBlank(message = "节点编码不能为空")
    @Size(max = 128, message = "节点编码长度不能超过128")
    private String nodeCode;
    
    @NotNull(message = "任务ID不能为空")
    private Long taskId;
    
    @Size(max = 128, message = "节点名称长度不能超过128")
    private String name;
    
    private String overrideParams;
    
    private Integer timeoutSec;
    
    private Integer maxRetry;
    
    @Size(max = 64, message = "并行组长度不能超过64")
    private String parallelGroup;
    
    private String meta;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOverrideParams() {
        return overrideParams;
    }

    public void setOverrideParams(String overrideParams) {
        this.overrideParams = overrideParams;
    }

    public Integer getTimeoutSec() {
        return timeoutSec;
    }

    public void setTimeoutSec(Integer timeoutSec) {
        this.timeoutSec = timeoutSec;
    }

    public Integer getMaxRetry() {
        return maxRetry;
    }

    public void setMaxRetry(Integer maxRetry) {
        this.maxRetry = maxRetry;
    }

    public String getParallelGroup() {
        return parallelGroup;
    }

    public void setParallelGroup(String parallelGroup) {
        this.parallelGroup = parallelGroup;
    }

    public String getMeta() {
        return meta;
    }

    public void setMeta(String meta) {
        this.meta = meta;
    }
}


