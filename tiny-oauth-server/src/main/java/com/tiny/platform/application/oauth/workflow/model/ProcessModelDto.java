package com.tiny.platform.application.oauth.workflow.model;

import com.tiny.platform.infrastructure.workflow.model.ProcessModelEntity;

import java.time.LocalDateTime;

public record ProcessModelDto(
    Long id,
    String modelKey,
    String name,
    String description,
    String scopeType,
    Long recordTenantId,
    String status,
    String runtimeState,
    Integer version,
    String bpmnXml,
    String svg,
    String validationStatus,
    String validationSummary,
    String deploymentId,
    String processDefinitionId,
    String processDefinitionKey,
    Integer processDefinitionVersion,
    String createdBy,
    LocalDateTime createdAt,
    String updatedBy,
    LocalDateTime updatedAt,
    String deployedBy,
    LocalDateTime deployedAt,
    Long lockVersion
) {

    public static ProcessModelDto from(ProcessModelEntity entity) {
        return from(entity, defaultRuntimeState(entity));
    }

    public static ProcessModelDto from(ProcessModelEntity entity, String runtimeState) {
        return new ProcessModelDto(
            entity.getId(),
            entity.getModelKey(),
            entity.getName(),
            entity.getDescription(),
            entity.getScopeType() != null ? entity.getScopeType().name() : null,
            entity.getTenantId(),
            entity.getStatus() != null ? entity.getStatus().name() : null,
            runtimeState,
            entity.getVersion(),
            entity.getBpmnXml(),
            entity.getSvg(),
            entity.getValidationStatus() != null ? entity.getValidationStatus().name() : null,
            entity.getValidationSummary(),
            entity.getDeploymentId(),
            entity.getProcessDefinitionId(),
            entity.getProcessDefinitionKey(),
            entity.getProcessDefinitionVersion(),
            entity.getCreatedBy(),
            entity.getCreatedAt(),
            entity.getUpdatedBy(),
            entity.getUpdatedAt(),
            entity.getDeployedBy(),
            entity.getDeployedAt(),
            entity.getLockVersion()
        );
    }

    private static String defaultRuntimeState(ProcessModelEntity entity) {
        return entity.getDeploymentId() == null || entity.getDeploymentId().isBlank()
            ? "NOT_DEPLOYED"
            : "HISTORICAL_DEPLOYED";
    }
}
