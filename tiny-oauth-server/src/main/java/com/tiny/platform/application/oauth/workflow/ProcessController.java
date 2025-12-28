package com.tiny.platform.application.oauth.workflow;

import org.springframework.beans.factory.annotation.Autowired;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SaaS 流程平台统一 API 控制器骨架
 * 按功能模块分类
 */
@RestController
@RequestMapping("/process")
//@CrossOrigin(origins = "*") // 允许跨域访问
public class ProcessController {

    @Autowired
    private ProcessEngineService processEngineService;

    @Autowired
    private BpmnValidationHelper bpmnValidationHelper;

    // ------------------- 1. 部署管理 -------------------

    /**
     * 部署流程（传入 BPMN XML 字符串）
     */
    @PostMapping("/deploy")
    public ResponseEntity<Map<String, Object>> deploy(@RequestBody String bpmnXml) {
        try {
            String tenantId = TenantContext.getCurrentTenant();
            String deploymentId = processEngineService.deployProcess(bpmnXml, tenantId);
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
    public ResponseEntity<Map<String, Object>> deployWithInfo(@RequestBody Map<String, Object> request, Principal principal) {
        try {
            String tenantId = TenantContext.getCurrentTenant();
            String bpmnXml = (String) request.get("bpmnXml");
            String deploymentName = (String) request.get("deploymentName");
            String source = (String) request.get("source");
            String deployer = (String) request.get("deployer");
            if (deployer == null || deployer.isBlank()) {
                deployer = principal != null ? principal.getName() : null;
            }

            String deploymentId = processEngineService.deployProcess(bpmnXml, tenantId, deploymentName, deployer,source);
            return ResponseEntity.ok(Map.of(
                "  ", true,
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
    public ResponseEntity<Object> listDeployments(@RequestParam(value = "tenantId", required = false) String tenantId) {
        try {
            Object result = processEngineService.listDeployments(tenantId);
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
    public ResponseEntity<Map<String, Object>> start(@RequestParam(value = "processKey") String processKey,
                        @RequestBody(required = false) Map<String, Object> variables) {
        try {
            String tenantId = TenantContext.getCurrentTenant();
            String instanceId = processEngineService.startProcessInstance(processKey, tenantId, variables);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "instanceId", instanceId,
                "message", "流程实例启动成功"
            ));
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
    public ResponseEntity<Object> listInstances(@RequestParam(value = "tenantId", required = false) String tenantId,
                                @RequestParam(value = "state", required = false) String state) {
        try {
            Object result = processEngineService.listProcessInstances(tenantId, state);
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
    public ResponseEntity<Object> tasks(@RequestParam(value = "assignee", required = false) String assignee) {
        try {
            String tenantId = TenantContext.getCurrentTenant();
            Object result = processEngineService.getTasks(assignee, tenantId);
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
    public ResponseEntity<Map<String, Object>> completeTask(@PathVariable String taskId,
                               @RequestBody(required = false) Map<String, Object> variables) {
        try {
            processEngineService.completeTask(taskId, variables);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "任务完成"
            ));
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
    public ResponseEntity<Object> historyInstances(@RequestParam(value = "tenantId", required = false) String tenantId) {
        try {
            Object result = processEngineService.listHistoricInstances(tenantId);
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
    public ResponseEntity<Map<String, Object>> createTenant(@RequestBody Map<String, Object> tenantInfo) {
        try {
            String tenantId = processEngineService.createTenant(tenantInfo);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "tenantId", tenantId,
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
    public ResponseEntity<Object> listProcessDefinitions(@RequestParam(value = "tenantId", required = false) String tenantId) {
        try {
            Object result = processEngineService.listProcessDefinitions(tenantId);
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
}