package com.tiny.platform.application.oauth.workflow.model;

public record ProcessModelDeployResponse(
    Long id,
    String deploymentId,
    String processDefinitionKey,
    String status,
    String message
) {
}
