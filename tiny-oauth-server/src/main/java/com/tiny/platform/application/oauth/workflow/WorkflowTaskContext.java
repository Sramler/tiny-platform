package com.tiny.platform.application.oauth.workflow;

public record WorkflowTaskContext(
    String taskId,
    String taskName,
    String taskDefinitionKey,
    String processInstanceId,
    String processDefinitionId,
    String processDefinitionKey,
    String tenantId
) {
}
