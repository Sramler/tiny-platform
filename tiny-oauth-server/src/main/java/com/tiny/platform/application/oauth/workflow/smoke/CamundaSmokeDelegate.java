package com.tiny.platform.application.oauth.workflow.smoke;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 最小 smoke 流程使用的 Spring Bean Delegate。
 */
@Component
@ConditionalOnProperty(prefix = "camunda.bpm", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CamundaSmokeDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(CamundaSmokeDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String observedTenantId = execution.getTenantId();
        Object runId = execution.getVariable("smokeRunId");

        execution.setVariable("camundaSmokeExecuted", true);
        execution.setVariable("camundaSmokeObservedTenantId", observedTenantId);
        execution.setVariable("camundaSmokeRunId", runId);

        log.info("Camunda SB4 smoke ServiceTask executed, tenantId={}, runId={}", observedTenantId, runId);
    }
}
