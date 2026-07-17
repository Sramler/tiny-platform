package com.tiny.platform.application.oauth.workflow;

import com.tiny.platform.infrastructure.idempotent.sdk.annotation.Idempotent;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.workflow.service.ProcessModelRuntimeBusinessAccessService;
import com.tiny.platform.infrastructure.workflow.service.PlatformWorkflowBusinessDataValidationService;
import com.tiny.platform.infrastructure.workflow.service.WorkflowBusinessRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

/**
 * SaaS 流程平台统一 API 控制器骨架
 * 按功能模块分类
 */
@RestController
@RequestMapping("/process")
//@CrossOrigin(origins = "*") // 允许跨域访问
@ConditionalOnProperty(prefix = "camunda.bpm", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProcessController {

    @Autowired
    private ProcessEngineService processEngineService;

    @Autowired
    private BpmnValidationHelper bpmnValidationHelper;

    @Autowired
    private ProcessModelRuntimeBusinessAccessService processModelRuntimeBusinessAccessService;

    @Autowired
    private PlatformWorkflowBusinessDataValidationService platformWorkflowBusinessDataValidationService;

    @Autowired
    private WorkflowBusinessRequestService workflowBusinessRequestService;

    // ------------------- 1. 部署管理 -------------------

    /**
     * 部署流程（传入 BPMN XML 字符串）
     */
    @PostMapping("/deploy")
    @PreAuthorize("@workflowAccessGuard.canConfig(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<Map<String, Object>> deploy(@RequestBody String bpmnXml) {
        try {
            String activeTenantId = resolveCurrentWorkflowTenantForRuntime();
            String deploymentId = processEngineService.deployProcess(bpmnXml, activeTenantId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "deploymentId", deploymentId,
                "message", "流程部署成功"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "未知错误"
            ));
        }
    }

    /**
     * 部署流程（传入 BPMN XML 字符串和流程信息）
     */
    @PostMapping("/deploy-with-info")
    @PreAuthorize("@workflowAccessGuard.canConfig(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<Map<String, Object>> deployWithInfo(@RequestBody Map<String, Object> request, Principal principal) {
        try {
            String activeTenantId = resolveCurrentWorkflowTenantForRuntime();
            String bpmnXml = (String) request.get("bpmnXml");
            String deploymentName = (String) request.get("deploymentName");
            String source = (String) request.get("source");
            String deployer = (String) request.get("deployer");
            if (deployer == null || deployer.isBlank()) {
                deployer = principal != null ? principal.getName() : null;
            }

            String deploymentId = processEngineService.deployProcess(bpmnXml, activeTenantId, deploymentName, deployer,source);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "deploymentId", deploymentId != null ? deploymentId : "",
                "processName", deploymentName != null ? deploymentName : "",
                "source", source != null ? source : "",
                "message", "流程部署成功"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "未知错误"
            ));
        }
    }

    /**
     * 查询部署列表（分页 + 按租户过滤）
     */
    @GetMapping("/deployments")
    @PreAuthorize("@workflowAccessGuard.canView(authentication)")
    public ResponseEntity<Object> listDeployments(@RequestParam(value = "recordTenantId", required = false) String recordTenantId) {
        try {
            Object result = processEngineService.listDeployments(resolveEffectiveRecordTenantId(recordTenantId));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "未知错误"
            ));
        }
    }

    /**
     * 删除部署（默认级联删除历史数据）
     */
    @DeleteMapping("/deployment/{deploymentId}")
    @PreAuthorize("@workflowAccessGuard.canConfig(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<Map<String, Object>> deleteDeployment(@PathVariable String deploymentId) {
        try {
            processEngineService.deleteDeployment(deploymentId, true);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "部署删除成功"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "未知错误"
            ));
        }
    }

    // ------------------- 2. 流程实例 -------------------

    /**
     * 启动流程实例
     */
    @PostMapping("/start")
    @PreAuthorize("@workflowAccessGuard.canControlInstance(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<Map<String, Object>> start(@RequestParam(value = "processKey") String processKey,
                                                     @RequestBody(required = false) Map<String, Object> variables,
                                                     Authentication authentication) {
        try {
            String activeTenantId = resolveCurrentWorkflowTenantForRuntime();
            processModelRuntimeBusinessAccessService.assertCanStartProcess(processKey, authentication);
            Map<String, Object> startVariables = platformWorkflowBusinessDataValidationService.prepareStartVariables(
                processKey,
                variables,
                authentication
            );
            Long businessRequestId = workflowBusinessRequestService.createSubmittedRequest(
                processKey,
                activeTenantId,
                startVariables,
                authentication
            ).orElse(null);
            if (businessRequestId != null) {
                startVariables.put("tpBusinessRequestId", businessRequestId);
            }
            String instanceId;
            try {
                instanceId = processEngineService.startProcessInstance(processKey, activeTenantId, startVariables);
                workflowBusinessRequestService.attachProcessInstance(
                    businessRequestId,
                    instanceId,
                    null,
                    authentication
                );
            } catch (Exception startException) {
                workflowBusinessRequestService.markStartFailed(businessRequestId, startException, authentication);
                throw startException;
            }
            return ResponseEntity.ok(Map.of(
                "success", true,
                "instanceId", instanceId,
                "message", "流程实例启动成功"
            ));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "未知错误"
            ));
        }
    }

    /**
     * 查询流程实例列表
     */
    @GetMapping("/instances")
    @PreAuthorize("@workflowAccessGuard.canView(authentication)")
    public ResponseEntity<Object> listInstances(@RequestParam(value = "recordTenantId", required = false) String recordTenantId,
                                @RequestParam(value = "state", required = false) String state) {
        try {
            Object result = processEngineService.listProcessInstances(resolveEffectiveRecordTenantId(recordTenantId), state);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "未知错误"
            ));
        }
    }

    /**
     * 挂起 / 激活 流程实例
     */
    @PostMapping("/instance/{instanceId}/suspend")
    @PreAuthorize("@workflowAccessGuard.canControlInstance(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<Map<String, Object>> suspendInstance(@PathVariable String instanceId) {
        try {
            processEngineService.suspendInstance(instanceId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "流程实例已挂起"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "未知错误"
            ));
        }
    }

    @PostMapping("/instance/{instanceId}/activate")
    @PreAuthorize("@workflowAccessGuard.canControlInstance(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<Map<String, Object>> activateInstance(@PathVariable String instanceId) {
        try {
            processEngineService.activateInstance(instanceId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "流程实例已激活"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "未知错误"
            ));
        }
    }

    /**
     * 删除流程实例
     */
    @DeleteMapping("/instance/{instanceId}")
    @PreAuthorize("@workflowAccessGuard.canControlInstance(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<Map<String, Object>> deleteInstance(@PathVariable String instanceId) {
        try {
            processEngineService.deleteInstance(instanceId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "流程实例删除成功"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "未知错误"
            ));
        }
    }

    /**
     * 获取流程实例的任务列表
     */
    @GetMapping("/instance/{instanceId}/tasks")
    @PreAuthorize("@workflowAccessGuard.canView(authentication)")
    public ResponseEntity<Object> getInstanceTasks(@PathVariable String instanceId) {
        try {
            Object result = processEngineService.getInstanceTasks(instanceId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "未知错误"
            ));
        }
    }

    // ------------------- 3. 任务管理 -------------------

    /**
     * 查询任务列表（按处理人）
     */
    @GetMapping("/tasks")
    @PreAuthorize("@workflowAccessGuard.canView(authentication)")
    public ResponseEntity<Object> tasks(@RequestParam(value = "assignee", required = false) String assignee) {
        try {
            String activeTenantId = resolveCurrentWorkflowTenantForRuntime();
            Object result = processEngineService.getTasks(assignee, activeTenantId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "未知错误"
            ));
        }
    }

    /**
     * 领取任务
     */
    @PostMapping("/task/{taskId}/claim")
    @PreAuthorize("@workflowAccessGuard.canControlInstance(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<Map<String, Object>> claimTask(@PathVariable String taskId, @RequestParam(value = "userId") String userId) {
        try {
            processEngineService.claimTask(taskId, userId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "任务已领取"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "未知错误"
            ));
        }
    }

    /**
     * 完成任务
     */
    @PostMapping("/task/{taskId}/complete")
    @PreAuthorize("@workflowAccessGuard.canControlInstance(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<Map<String, Object>> completeTask(@PathVariable String taskId,
                               @RequestBody(required = false) Map<String, Object> variables,
                               Authentication authentication) {
        try {
            WorkflowTaskContext taskContext = processEngineService.getTaskContext(taskId);
            Map<String, Object> completeVariables = platformWorkflowBusinessDataValidationService
                .prepareTaskCompleteVariables(taskContext, variables, authentication);
            if (workflowBusinessRequestService.isRejectDecision(completeVariables)
                && workflowBusinessRequestService.isPlatformWorkflow(taskContext.processDefinitionKey())) {
                workflowBusinessRequestService.rejectTask(taskContext, completeVariables, authentication);
                processEngineService.deleteInstance(taskContext.processInstanceId());
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "任务已拒绝，流程实例已终止"
                ));
            }
            processEngineService.completeTask(taskId, completeVariables);
            workflowBusinessRequestService.finishTask(
                taskContext,
                completeVariables,
                authentication,
                processEngineService.hasOpenTasks(taskContext.processInstanceId())
            );
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "任务完成"
            ));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "未知错误"
            ));
        }
    }

    // ------------------- 4. 历史数据 -------------------

    /**
     * 查询历史流程实例
     */
    @GetMapping("/history/instances")
    @PreAuthorize("@workflowAccessGuard.canView(authentication)")
    public ResponseEntity<Object> historyInstances(@RequestParam(value = "recordTenantId", required = false) String recordTenantId) {
        try {
            Object result = processEngineService.listHistoricInstances(resolveEffectiveRecordTenantId(recordTenantId));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "未知错误"
            ));
        }
    }

    /**
     * 查询历史任务记录
     */
    @GetMapping("/history/tasks")
    @PreAuthorize("@workflowAccessGuard.canView(authentication)")
    public ResponseEntity<Object> historyTasks(@RequestParam(value = "processInstanceId") String processInstanceId) {
        try {
            Object result = processEngineService.listHistoricTasks(processInstanceId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "未知错误"
            ));
        }
    }

    // ------------------- 5. 租户管理 -------------------

    /**
     * 注册新租户
     */
    @PostMapping("/tenant")
    @PreAuthorize("@workflowAccessGuard.canManageTenant(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<Map<String, Object>> createTenant(@RequestBody Map<String, Object> tenantInfo) {
        try {
            String createdTenantId = processEngineService.createTenant(tenantInfo);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "createdTenantId", createdTenantId,
                "message", "租户创建成功"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "未知错误"
            ));
        }
    }

    /**
     * 查询租户列表
     */
    @GetMapping("/tenants")
    @PreAuthorize("@workflowAccessGuard.canManageTenant(authentication)")
    public ResponseEntity<Object> listTenants() {
        try {
            Object result = processEngineService.listTenants();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "未知错误"
            ));
        }
    }

    // ------------------- 6. 运维管理 -------------------

    /**
     * 获取引擎信息（版本、数据库等）
     */
    @GetMapping("/engine/info")
    @PreAuthorize("@workflowAccessGuard.canView(authentication)")
    public ResponseEntity<Object> engineInfo() {
        try {
            Object result = processEngineService.getEngineInfo();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "未知错误"
            ));
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "message", "流程引擎运行正常",
            "timestamp", System.currentTimeMillis()
        ));
    }

    // ------------------- 7. 流程设计器相关 -------------------

    /**
     * 获取流程定义列表
     */
    @GetMapping("/definitions")
    @PreAuthorize("@workflowAccessGuard.canView(authentication)")
    public ResponseEntity<Object> listProcessDefinitions(@RequestParam(value = "recordTenantId", required = false) String recordTenantId) {
        try {
            Object result = processEngineService.listProcessDefinitions(resolveEffectiveRecordTenantId(recordTenantId));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "未知错误"
            ));
        }
    }

    /**
     * 获取流程定义的 BPMN XML
     */
    @GetMapping("/definition/{processDefinitionId}/xml")
    @PreAuthorize("@workflowAccessGuard.canView(authentication)")
    public ResponseEntity<Map<String, Object>> getProcessDefinitionXml(@PathVariable String processDefinitionId) {
        try {
            String bpmnXml = processEngineService.getProcessDefinitionXml(processDefinitionId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "bpmnXml", bpmnXml
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "未知错误"
            ));
        }
    }

    /**
     * 删除流程定义（通过部署ID删除整个部署）
     */
    @DeleteMapping("/definition/{processDefinitionId}")
    @PreAuthorize("@workflowAccessGuard.canConfig(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<Map<String, Object>> deleteProcessDefinition(@PathVariable String processDefinitionId) {
        try {
            // 通过流程定义ID获取部署ID，然后删除整个部署
            processEngineService.deleteProcessDefinition(processDefinitionId, true);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "流程定义删除成功"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "未知错误"
            ));
        }
    }

    /**
     * 验证 BPMN XML 格式
     */
    @PostMapping("/validate")
    @PreAuthorize("@workflowAccessGuard.canConfig(authentication)")
    public ResponseEntity<Map<String, Object>> validateBpmnXml(@RequestBody String bpmnXml) {
        try {
            BpmnValidationHelper.BpmnValidationResult result = bpmnValidationHelper.validateBpmnXml(bpmnXml);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "valid", result.isValid(),
                "message", result.getMessage(),
                "warnings", result.getWarnings()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "未知错误"
            ));
        }
    }

    /**
     * 修复 BPMN XML 验证错误
     */
    @PostMapping("/fix-validation-errors")
    @PreAuthorize("@workflowAccessGuard.canConfig(authentication)")
    public ResponseEntity<Map<String, Object>> fixBpmnValidationErrors(@RequestBody String bpmnXml) {
        try {
            String fixedBpmnXml = bpmnValidationHelper.fixBpmnValidationErrors(bpmnXml);

            // 验证修复后的 BPMN
            BpmnValidationHelper.BpmnValidationResult result = bpmnValidationHelper.validateBpmnXml(fixedBpmnXml);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "fixedBpmnXml", fixedBpmnXml,
                "valid", result.isValid(),
                "message", result.getMessage(),
                "warnings", result.getWarnings()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "未知错误"
            ));
        }
    }

    private String resolveEffectiveRecordTenantId(String recordTenantId) {
        if (com.tiny.platform.core.oauth.tenant.TenantContext.isPlatformScope()) {
            if (recordTenantId != null && !recordTenantId.isBlank()) {
                throw new IllegalStateException("平台流程管理不支持按租户过滤");
            }
            return null;
        }
        return requireCurrentWorkflowTenant();
    }

    private String resolveCurrentWorkflowTenantForRuntime() {
        if (com.tiny.platform.core.oauth.tenant.TenantContext.isPlatformScope()) {
            return null;
        }
        return requireCurrentWorkflowTenant();
    }

    private String requireCurrentWorkflowTenant() {
        String currentTenant = resolveCurrentWorkflowTenant();
        if (currentTenant != null && !currentTenant.isBlank()) {
            return currentTenant;
        }
        if (com.tiny.platform.core.oauth.tenant.TenantContext.isPlatformScope()) {
            throw new IllegalStateException("平台流程请求使用无租户运行态，不应要求租户上下文");
        }
        throw new IllegalStateException("当前请求未解析到有效租户上下文");
    }

    private String resolveCurrentWorkflowTenant() {
        Long coreTenantId = com.tiny.platform.core.oauth.tenant.TenantContext.getActiveTenantId();
        if (coreTenantId != null && coreTenantId > 0) {
            return String.valueOf(coreTenantId);
        }
        String currentTenant = TenantContext.getCurrentTenant();
        return currentTenant != null && !currentTenant.isBlank() ? currentTenant : null;
    }
}
