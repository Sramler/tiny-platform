package com.tiny.platform.application.oauth.workflow.smoke;

import com.tiny.platform.application.oauth.workflow.ProcessEngineService;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用于验证 Camunda 7 fork 在 Boot 4 环境中的最小可运行能力。
 */
@Component
@ConditionalOnProperty(prefix = "camunda.bpm", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CamundaSmokeVerifier {

    private static final Logger log = LoggerFactory.getLogger(CamundaSmokeVerifier.class);
    private static final Duration HISTORY_WAIT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration HISTORY_WAIT_INTERVAL = Duration.ofMillis(200);
    private static final String SMOKE_TENANT_ID = "camunda-sb4-smoke";

    private final ProcessEngineService processEngineService;
    private final RepositoryService repositoryService;
    private final HistoryService historyService;

    public CamundaSmokeVerifier(
            ProcessEngineService processEngineService,
            RepositoryService repositoryService,
            HistoryService historyService
    ) {
        this.processEngineService = processEngineService;
        this.repositoryService = repositoryService;
        this.historyService = historyService;
    }

    public void verify() throws Exception {
        String runId = "camunda_sb4_smoke_" + Instant.now().toEpochMilli();
        String processKey = runId;
        String deploymentName = "SB4_SMOKE_" + runId;
        String deploymentId = null;

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("smokeRunId", runId);
        variables.put("smokeRequestedTenantId", SMOKE_TENANT_ID);

        try {
            deploymentId = processEngineService.deployProcess(
                    buildBpmnXml(processKey),
                    SMOKE_TENANT_ID,
                    deploymentName,
                    "codex-smoke",
                    "camunda-sb4-smoke"
            );

            long processDefinitionCount = repositoryService.createProcessDefinitionQuery()
                    .deploymentId(deploymentId)
                    .tenantIdIn(SMOKE_TENANT_ID)
                    .count();
            if (processDefinitionCount != 1) {
                throw new IllegalStateException("Smoke deployment expected 1 process definition, but got " + processDefinitionCount);
            }

            String instanceId = processEngineService.startProcessInstance(processKey, SMOKE_TENANT_ID, variables);
            HistoricProcessInstance historicInstance = waitForHistoricProcessInstance(instanceId);
            HistoricVariableInstance executedFlag = waitForHistoricVariable(instanceId, "camundaSmokeExecuted");
            HistoricVariableInstance observedTenant = waitForHistoricVariable(instanceId, "camundaSmokeObservedTenantId");

            if (historicInstance == null || historicInstance.getEndTime() == null) {
                throw new IllegalStateException("Smoke process did not finish successfully, instanceId=" + instanceId);
            }
            if (executedFlag == null || !Boolean.TRUE.equals(executedFlag.getValue())) {
                throw new IllegalStateException("Smoke ServiceTask execution flag not found or false, instanceId=" + instanceId);
            }
            if (observedTenant == null || !SMOKE_TENANT_ID.equals(observedTenant.getValue())) {
                throw new IllegalStateException("Smoke tenant check failed, expected tenantId=" + SMOKE_TENANT_ID);
            }

            log.info(
                    "Camunda SB4 smoke passed: deploymentId={}, instanceId={}, processKey={}, tenantId={}",
                    deploymentId,
                    instanceId,
                    processKey,
                    SMOKE_TENANT_ID
            );
        } finally {
            if (deploymentId != null) {
                processEngineService.deleteDeployment(deploymentId, true);
                log.info("Camunda SB4 smoke cleanup completed, deploymentId={}", deploymentId);
            }
        }
    }

    private HistoricProcessInstance waitForHistoricProcessInstance(String instanceId) throws InterruptedException {
        Instant deadline = Instant.now().plus(HISTORY_WAIT_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            HistoricProcessInstance instance = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(instanceId)
                    .singleResult();
            if (instance != null && instance.getEndTime() != null) {
                return instance;
            }
            Thread.sleep(HISTORY_WAIT_INTERVAL.toMillis());
        }
        return historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();
    }

    private HistoricVariableInstance waitForHistoricVariable(String instanceId, String variableName) throws InterruptedException {
        Instant deadline = Instant.now().plus(HISTORY_WAIT_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            HistoricVariableInstance variable = historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(instanceId)
                    .variableName(variableName)
                    .singleResult();
            if (variable != null) {
                return variable;
            }
            Thread.sleep(HISTORY_WAIT_INTERVAL.toMillis());
        }
        return historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(instanceId)
                .variableName(variableName)
                .singleResult();
    }

    private String buildBpmnXml(String processKey) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                  xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  targetNamespace="camunda-sb4-smoke">
                  <bpmn:process id="%s" name="Camunda SB4 Smoke" isExecutable="true">
                    <bpmn:startEvent id="startEvent" />
                    <bpmn:sequenceFlow id="flow_start_to_service" sourceRef="startEvent" targetRef="serviceTask" />
                    <bpmn:serviceTask id="serviceTask"
                                      name="Smoke ServiceTask"
                                      camunda:delegateExpression="${camundaSmokeDelegate}" />
                    <bpmn:sequenceFlow id="flow_service_to_end" sourceRef="serviceTask" targetRef="endEvent" />
                    <bpmn:endEvent id="endEvent" />
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(processKey);
    }
}
