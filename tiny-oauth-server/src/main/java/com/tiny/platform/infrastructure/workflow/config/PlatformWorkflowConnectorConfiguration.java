package com.tiny.platform.infrastructure.workflow.config;

import com.tiny.platform.infrastructure.workflow.service.WorkflowBusinessRequestService;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlatformWorkflowConnectorConfiguration {

    private final WorkflowBusinessRequestService workflowBusinessRequestService;

    public PlatformWorkflowConnectorConfiguration(WorkflowBusinessRequestService workflowBusinessRequestService) {
        this.workflowBusinessRequestService = workflowBusinessRequestService;
    }

    @Bean("platformTenantProvisioningConnector")
    public JavaDelegate platformTenantProvisioningConnector() {
        return execution -> workflowBusinessRequestService.applyConnector(execution, "platformTenantProvisioningConnector");
    }

    @Bean("platformTenantPlanChangeConnector")
    public JavaDelegate platformTenantPlanChangeConnector() {
        return execution -> workflowBusinessRequestService.applyConnector(execution, "platformTenantPlanChangeConnector");
    }

    @Bean("platformTenantLifecycleConnector")
    public JavaDelegate platformTenantLifecycleConnector() {
        return execution -> workflowBusinessRequestService.applyConnector(execution, "platformTenantLifecycleConnector");
    }

    @Bean("platformPermissionPublishConnector")
    public JavaDelegate platformPermissionPublishConnector() {
        return execution -> workflowBusinessRequestService.applyConnector(execution, "platformPermissionPublishConnector");
    }

    @Bean("platformRoleBaselineConnector")
    public JavaDelegate platformRoleBaselineConnector() {
        return execution -> workflowBusinessRequestService.applyConnector(execution, "platformRoleBaselineConnector");
    }

    @Bean("platformConnectorPublishConnector")
    public JavaDelegate platformConnectorPublishConnector() {
        return execution -> workflowBusinessRequestService.applyConnector(execution, "platformConnectorPublishConnector");
    }

    @Bean("platformTemplatePublishConnector")
    public JavaDelegate platformTemplatePublishConnector() {
        return execution -> workflowBusinessRequestService.applyConnector(execution, "platformTemplatePublishConnector");
    }

    @Bean("platformConfigChangeConnector")
    public JavaDelegate platformConfigChangeConnector() {
        return execution -> workflowBusinessRequestService.applyConnector(execution, "platformConfigChangeConnector");
    }
}
