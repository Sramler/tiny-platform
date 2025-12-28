package com.tiny.platform.application.oauth.workflow;

import java.util.Map;

/**
 * 流程引擎通用接口
 * SaaS 平台可支持多种 BPM 引擎（Camunda / Flowable / Activiti）
 */
public interface ProcessEngineService {

    // 部署管理
    String deployProcess(String bpmnXml, String tenantId) throws Exception;
    String deployProcess(String bpmnXml, String tenantId, String source) throws Exception;
    String deployProcess(String bpmnXml, String tenantId, String deploymentName, String explicitDeployer,String source) throws Exception;
    Object listDeployments(String tenantId);
    void deleteDeployment(String deploymentId, boolean cascade);

    // 流程实例
    String startProcessInstance(String processKey, String tenantId, Map<String, Object> variables);
    Object listProcessInstances(String tenantId, String state);
    void suspendInstance(String instanceId);
    void activateInstance(String instanceId);
    void deleteInstance(String instanceId);
    Object getInstanceTasks(String instanceId);

    // 任务管理
    Object getTasks(String assignee, String tenantId);
    void claimTask(String taskId, String userId);
    void completeTask(String taskId, Map<String, Object> variables);

    // 历史查询
    Object listHistoricInstances(String tenantId);
    Object listHistoricTasks(String processInstanceId);

    // 租户管理
    String createTenant(Map<String, Object> tenantInfo);
    Object listTenants();

    // 运维
    Object getEngineInfo();

    // 流程定义管理
    Object listProcessDefinitions(String tenantId);
    String getProcessDefinitionXml(String processDefinitionId);
    void deleteProcessDefinition(String processDefinitionId, boolean cascade);
    boolean validateBpmnXml(String bpmnXml);
}