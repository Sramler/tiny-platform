package com.tiny.platform.infrastructure.scheduling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SchedulingDagEdgeCreateDto {
    private Long dagVersionId;
    
    @NotBlank(message = "上游节点编码不能为空")
    @Size(max = 128, message = "节点编码长度不能超过128")
    private String fromNodeCode;
    
    @NotBlank(message = "下游节点编码不能为空")
    @Size(max = 128, message = "节点编码长度不能超过128")
    private String toNodeCode;
    
    private String condition;

    // Getters and Setters
    public Long getDagVersionId() {
        return dagVersionId;
    }

    public void setDagVersionId(Long dagVersionId) {
        this.dagVersionId = dagVersionId;
    }

    public String getFromNodeCode() {
        return fromNodeCode;
    }

    public void setFromNodeCode(String fromNodeCode) {
        this.fromNodeCode = fromNodeCode;
    }

    public String getToNodeCode() {
        return toNodeCode;
    }

    public void setToNodeCode(String toNodeCode) {
        this.toNodeCode = toNodeCode;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }
}

