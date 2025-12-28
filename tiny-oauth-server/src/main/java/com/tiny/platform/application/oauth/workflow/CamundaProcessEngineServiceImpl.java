package com.tiny.platform.application.oauth.workflow;

import org.camunda.bpm.engine.*;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Camunda 7 嵌入式引擎实现
 */
@Service
public class CamundaProcessEngineServiceImpl implements ProcessEngineService {

    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private HistoryService historyService;
    @Autowired
    private ManagementService managementService;
    @Autowired
    private IdentityService identityService;

    @Autowired
    private BpmnValidationHelper bpmnValidationHelper;

    // ------------------- 部署管理 -------------------
    @Override
    public String deployProcess(String bpmnXml, String tenantId) throws Exception {
        return deployProcess(bpmnXml, tenantId, "手动上传");
    }

    @Override
    public String deployProcess(String bpmnXml, String tenantId, String source) {
        return deployProcess(bpmnXml, tenantId, source, null,source);
    }

    public String deployProcess(String bpmnXml, String tenantId, String deploymentName, String explicitDeployer,String source) {
        // 先尝试修复 BPMN 验证错误
        String fixedBpmnXml = bpmnValidationHelper.fixBpmnValidationErrors(bpmnXml);

        Deployment deployment = repositoryService.createDeployment()
                .tenantId(tenantId)
                .name(deploymentName)
                .addInputStream("process.bpmn",
                        new ByteArrayInputStream(fixedBpmnXml.getBytes(StandardCharsets.UTF_8)))
                .source(source)
                .deploy();
        return deployment.getId();
    }

    @Override
    public Object listDeployments(String tenantId) {
        var query = repositoryService.createDeploymentQuery();
        if (tenantId != null && !tenantId.trim().isEmpty()) {
            query = query.tenantIdIn(tenantId);
        }
        List<Deployment> deployments = query.list();
        return deployments.stream()
                .map(d -> {
                    // 计算该部署包含的流程定义数量
                    var processDefinitions = repositoryService.createProcessDefinitionQuery()
                            .deploymentId(d.getId())
                            .list();

                    // 获取部署的资源名称，判断部署来源
                    String source = determineDeploymentSource(d);

                    // 计算流程定义统计
                    long activeProcessDefinitions = processDefinitions.stream()
                            .filter(pd -> !pd.isSuspended())
                            .count();
                    long suspendedProcessDefinitions = processDefinitions.size() - activeProcessDefinitions;

                    // 计算流程实例统计
                    long totalProcessInstances = processDefinitions.stream()
                            .mapToLong(pd -> runtimeService.createProcessInstanceQuery()
                                    .processDefinitionId(pd.getId())
                                    .count())
                            .sum();
                    long activeProcessInstances = processDefinitions.stream()
                            .mapToLong(pd -> runtimeService.createProcessInstanceQuery()
                                    .processDefinitionId(pd.getId())
                                    .active()
                                    .count())
                            .sum();
                    long completedProcessInstances = processDefinitions.stream()
                            .mapToLong(pd -> historyService.createHistoricProcessInstanceQuery()
                                    .processDefinitionId(pd.getId())
                                    .finished()
                                    .count())
                            .sum();

                    // 使用 HashMap 构建，因为 Map.of() 有参数数量限制
                    Map<String, Object> deploymentInfo = new java.util.HashMap<>();

                    // 基础信息
                    deploymentInfo.put("id", d.getId());
                    deploymentInfo.put("name", d.getName() != null ? d.getName() : "");
                    deploymentInfo.put("deploymentTime", d.getDeploymentTime() != null ? d.getDeploymentTime().toString() : "");
                    deploymentInfo.put("tenantId", d.getTenantId() != null ? d.getTenantId() : "");

                    // 状态和来源
                    deploymentInfo.put("status", "success"); // Camunda 部署成功即表示成功
                    deploymentInfo.put("source", source);

                    // 流程定义统计
                    deploymentInfo.put("processDefinitionCount", processDefinitions.size());
                    deploymentInfo.put("activeProcessDefinitionCount", activeProcessDefinitions);
                    deploymentInfo.put("suspendedProcessDefinitionCount", suspendedProcessDefinitions);

                    // 流程实例统计
                    deploymentInfo.put("totalProcessInstances", totalProcessInstances);
                    deploymentInfo.put("activeProcessInstances", activeProcessInstances);
                    deploymentInfo.put("completedProcessInstances", completedProcessInstances);

                    // 时间格式化
                    deploymentInfo.put("deploymentTimeFormatted", formatDeploymentTime(d.getDeploymentTime()));
                    deploymentInfo.put("deploymentTimeAgo", getTimeAgo(d.getDeploymentTime()));

                    // 业务信息
                    deploymentInfo.put("deployer", getDeployer(d));
                    deploymentInfo.put("environment", getEnvironment(d));
                    deploymentInfo.put("version", getDeploymentVersion(d));
                    deploymentInfo.put("description", getDeploymentDescription(d));
                    deploymentInfo.put("tags", getDeploymentTags(d));

                    // 详细流程定义信息
                    deploymentInfo.put("processDefinitions", processDefinitions.stream()
                            .map(pd -> Map.of(
                                    "id", pd.getId(),
                                    "name", pd.getName(),
                                    "key", pd.getKey(),
                                    "version", pd.getVersion(),
                                    "suspended", pd.isSuspended(),
                                    "resourceName", pd.getResourceName(),
                                    "deploymentId", pd.getDeploymentId(),
                                    "tenantId", pd.getTenantId() != null ? pd.getTenantId() : ""
                            ))
                            .collect(Collectors.toList()));

                    return deploymentInfo;
                })
                .collect(Collectors.toList());
    }

    /**
     * 根据部署信息判断部署来源
     */
    private String determineDeploymentSource(Deployment deployment) {
        if (deployment.getName() != null) {
            String name = deployment.getName();
            if (name.startsWith("流程建模_")) {
                return "流程建模";
            } else if (name.startsWith("API部署_")) {
                return "API部署";
            } else if (name.startsWith("定时部署_")) {
                return "定时部署";
            } else if (name.startsWith("CI/CD_")) {
                return "CI/CD";
            } else if (name.startsWith("手动上传_")) {
                return "手动上传";
            } else if (name.contains("API")) {
                return "API部署";
            } else if (name.contains("定时")) {
                return "定时部署";
            } else if (name.contains("CI/CD")) {
                return "CI/CD";
            } else if (name.contains("建模") || name.contains("设计")) {
                return "流程建模";
            }
        }
        return "手动上传";
    }

    /**
     * 格式化部署时间
     */
    private String formatDeploymentTime(java.util.Date deploymentTime) {
        if (deploymentTime == null) return "";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(deploymentTime);
    }

    /**
     * 获取相对时间（如：2小时前）
     */
    private String getTimeAgo(java.util.Date deploymentTime) {
        if (deploymentTime == null) return "";

        long now = System.currentTimeMillis();
        long deploymentTimeMillis = deploymentTime.getTime();
        long diff = now - deploymentTimeMillis;

        if (diff < 60000) { // 1分钟内
            return "刚刚";
        } else if (diff < 3600000) { // 1小时内
            return (diff / 60000) + "分钟前";
        } else if (diff < 86400000) { // 1天内
            return (diff / 3600000) + "小时前";
        } else if (diff < 2592000000L) { // 30天内
            return (diff / 86400000) + "天前";
        } else {
            return "很久以前";
        }
    }

    /**
     * 获取部署者
     */
    private String getDeployer(Deployment deployment) {
        // 优先从名称中解析: xxxx_by_<user>
        if (deployment != null && deployment.getName() != null) {
            String name = deployment.getName();
            int idx = name.lastIndexOf("_by_");
            if (idx >= 0 && idx + 4 < name.length()) {
                return name.substring(idx + 4);
            }
        }
        // 回退到当前登录用户
        return getCurrentUsername();
    }

    /**
     * 获取当前登录用户名（来自 Spring Security），失败则返回默认值。
     */
    private String getCurrentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getName() != null) {
                return auth.getName();
            }
        } catch (Exception ignored) {
        }
        return "系统管理员";
    }

    /**
     * 获取部署环境
     */
    private String getEnvironment(Deployment deployment) {
        // 可以根据部署名称或配置判断环境
        if (deployment.getName() != null) {
            if (deployment.getName().contains("prod") || deployment.getName().contains("生产")) {
                return "生产环境";
            } else if (deployment.getName().contains("test") || deployment.getName().contains("测试")) {
                return "测试环境";
            } else if (deployment.getName().contains("dev") || deployment.getName().contains("开发")) {
                return "开发环境";
            }
        }
        return "默认环境";
    }

    /**
     * 获取部署版本
     */
    private String getDeploymentVersion(Deployment deployment) {
        // 可以从部署名称中提取版本号
        if (deployment.getName() != null && deployment.getName().matches(".*v?\\d+\\.\\d+.*")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("v?(\\d+\\.\\d+)");
            java.util.regex.Matcher matcher = pattern.matcher(deployment.getName());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "1.0.0";
    }

    /**
     * 获取部署描述
     */
    private String getDeploymentDescription(Deployment deployment) {
        // 可以从部署名称或元数据中获取描述
        if (deployment.getName() != null && !deployment.getName().isEmpty()) {
            return "部署: " + deployment.getName();
        }
        return "流程部署";
    }

    /**
     * 获取部署标签
     */
    private List<String> getDeploymentTags(Deployment deployment) {
        List<String> tags = new java.util.ArrayList<>();

        // 根据部署名称添加标签
        if (deployment.getName() != null) {
            if (deployment.getName().contains("紧急")) {
                tags.add("紧急");
            }
            if (deployment.getName().contains("重要")) {
                tags.add("重要");
            }
            if (deployment.getName().contains("测试")) {
                tags.add("测试");
            }
            if (deployment.getName().contains("生产")) {
                tags.add("生产");
            }
        }

        // 默认添加 BPMN 标签，因为这是流程部署
        tags.add("BPMN");

        return tags;
    }

    @Override
    public void deleteDeployment(String deploymentId, boolean cascade) {
        repositoryService.deleteDeployment(deploymentId, cascade);
    }

    // ------------------- 流程实例 -------------------
    @Override
    public String startProcessInstance(String processKey, String tenantId, Map<String, Object> variables) {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(processKey, tenantId, variables);
        return instance.getId();
    }

    @Override
    public Object listProcessInstances(String tenantId, String state) {
        var query = runtimeService.createProcessInstanceQuery();
        if (tenantId != null && !tenantId.trim().isEmpty()) {
            query = query.tenantIdIn(tenantId);
        }
        if ("active".equalsIgnoreCase(state)) {
            query = query.active();
        } else if ("suspended".equalsIgnoreCase(state)) {
            query = query.suspended();
        }

        List<ProcessInstance> instances = query.list();
        return instances.stream()
                .map(this::convertProcessInstanceToMap)
                .collect(Collectors.toList());
    }

    /**
     * 将 ProcessInstance 转换为 Map，避免序列化问题
     */
    private Map<String, Object> convertProcessInstanceToMap(ProcessInstance instance) {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("id", instance.getId());
        result.put("processKey", instance.getProcessDefinitionKey());
        result.put("processDefinitionId", instance.getProcessDefinitionId());
        result.put("processDefinitionName", ""); // ProcessInstance 没有 getName() 方法
        result.put("suspended", instance.isSuspended());
        result.put("startTime", ""); // ProcessInstance 没有 getStartTime() 方法
        result.put("endTime", ""); // ProcessInstance 没有 getEndTime() 方法
        result.put("tenantId", instance.getTenantId() != null ? instance.getTenantId() : "");
        result.put("state", instance.isSuspended() ? "suspended" : "active");

        // 添加流程变量
        try {
            Map<String, Object> variables = runtimeService.getVariables(instance.getId());
            result.put("variables", variables);
        } catch (Exception e) {
            result.put("variables", new java.util.HashMap<>());
        }

        return result;
    }

    @Override
    public void suspendInstance(String instanceId) {
        runtimeService.suspendProcessInstanceById(instanceId);
    }

    @Override
    public void activateInstance(String instanceId) {
        runtimeService.activateProcessInstanceById(instanceId);
    }

    @Override
    public void deleteInstance(String instanceId) {
        runtimeService.deleteProcessInstance(instanceId, "用户删除");
    }

    @Override
    public Object getInstanceTasks(String instanceId) {
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(instanceId)
                .list();
        return tasks.stream()
                .map(t -> Map.of(
                        "id", t.getId(),
                        "name", t.getName(),
                        "assignee", t.getAssignee() != null ? t.getAssignee() : "",
                        "createTime", t.getCreateTime() != null ? t.getCreateTime().toString() : "",
                        "processInstanceId", t.getProcessInstanceId(),
                        "tenantId", t.getTenantId() != null ? t.getTenantId() : ""
                ))
                .collect(Collectors.toList());
    }

    // ------------------- 任务管理 -------------------
    @Override
    public Object getTasks(String assignee, String tenantId) {
        var query = taskService.createTaskQuery();

        // 根据assignee参数决定查询类型
        if (assignee == null || assignee.trim().isEmpty()) {
            // 查询未分配的任务
            query = query.taskUnassigned();
        } else if ("*".equals(assignee)) {
            // 查询所有任务（已分配和未分配）
            // 不添加assignee条件
        } else {
            // 查询分配给特定用户的任务
            query = query.taskAssignee(assignee);
        }

        if (tenantId != null && !tenantId.trim().isEmpty()) {
            query = query.tenantIdIn(tenantId);
        }

        List<Task> tasks = query.list();
        return tasks.stream()
                .map(t -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", t.getId());
                    map.put("name", t.getName());
                    map.put("assignee", t.getAssignee() != null ? t.getAssignee() : "");
                    map.put("createTime", t.getCreateTime() != null ? t.getCreateTime().toString() : "");
                    map.put("dueDate", t.getDueDate() != null ? t.getDueDate().toString() : "");
                    map.put("priority", t.getPriority());
                    map.put("processInstanceId", t.getProcessInstanceId());
                    map.put("tenantId", t.getTenantId() != null ? t.getTenantId() : "");
                    return map;
                })
                .collect(Collectors.toList());
    }

    @Override
    public void claimTask(String taskId, String userId) {
        taskService.claim(taskId, userId);
    }

    @Override
    public void completeTask(String taskId, Map<String, Object> variables) {
        taskService.complete(taskId, variables);
    }

    // ------------------- 历史数据 -------------------
    @Override
    public Object listHistoricInstances(String tenantId) {
        var query = historyService.createHistoricProcessInstanceQuery();
        if (tenantId != null && !tenantId.trim().isEmpty()) {
            query = query.tenantIdIn(tenantId);
        }
        List<org.camunda.bpm.engine.history.HistoricProcessInstance> instances = query.list();
        return instances.stream()
                .map(this::convertHistoricInstanceToMap)
                .collect(Collectors.toList());
    }

    /**
     * 将 HistoricProcessInstance 转换为 Map，避免序列化问题
     */
    private Map<String, Object> convertHistoricInstanceToMap(org.camunda.bpm.engine.history.HistoricProcessInstance instance) {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("id", instance.getId());
        result.put("processKey", instance.getProcessDefinitionKey());
        result.put("processDefinitionId", instance.getProcessDefinitionId());
        result.put("processDefinitionName", instance.getProcessDefinitionName());
        result.put("startTime", instance.getStartTime() != null ? instance.getStartTime().toString() : "");
        result.put("endTime", instance.getEndTime() != null ? instance.getEndTime().toString() : "");
        result.put("tenantId", instance.getTenantId() != null ? instance.getTenantId() : "");
        result.put("state", instance.getEndTime() != null ? "completed" : "active");
        result.put("duration", instance.getDurationInMillis() != null ? instance.getDurationInMillis() : 0);
        return result;
    }

    @Override
    public Object listHistoricTasks(String processInstanceId) {
        List<org.camunda.bpm.engine.history.HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .list();
        return tasks.stream()
                .map(this::convertHistoricTaskToMap)
                .collect(Collectors.toList());
    }

    /**
     * 将 HistoricTaskInstance 转换为 Map，避免序列化问题
     */
    private Map<String, Object> convertHistoricTaskToMap(org.camunda.bpm.engine.history.HistoricTaskInstance task) {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("id", task.getId());
        result.put("name", task.getName());
        result.put("assignee", task.getAssignee() != null ? task.getAssignee() : "");
        result.put("createTime", task.getStartTime() != null ? task.getStartTime().toString() : ""); // 使用 getStartTime()
        result.put("endTime", task.getEndTime() != null ? task.getEndTime().toString() : "");
        result.put("processInstanceId", task.getProcessInstanceId());
        result.put("tenantId", task.getTenantId() != null ? task.getTenantId() : "");
        result.put("duration", task.getDurationInMillis() != null ? task.getDurationInMillis() : 0);
        result.put("state", task.getEndTime() != null ? "completed" : "active");
        return result;
    }

    // ------------------- 租户管理 -------------------
    @Override
    public String createTenant(Map<String, Object> tenantInfo) {
        String tenantId = (String) tenantInfo.get("id");
        String name = (String) tenantInfo.get("name");
        var tenant = identityService.newTenant(tenantId);
        tenant.setName(name);
        identityService.saveTenant(tenant);
        return tenantId;
    }

    @Override
    public Object listTenants() {
        return identityService.createTenantQuery().list();
    }

    // ------------------- 运维 -------------------
    @Override
    public Object getEngineInfo() {
        return managementService.getProperties();
    }

    // ------------------- 流程定义管理 -------------------
    @Override
    public Object listProcessDefinitions(String tenantId) {
        var query = repositoryService.createProcessDefinitionQuery();
        if (tenantId != null && !tenantId.trim().isEmpty()) {
            query = query.tenantIdIn(tenantId);
        }
        List<org.camunda.bpm.engine.repository.ProcessDefinition> definitions = query.list();
        return definitions.stream()
                .map(pd -> Map.of(
                        "id", pd.getId(),
                        "key", pd.getKey(),
                        "name", pd.getName() != null ? pd.getName() : "",
                        "version", pd.getVersion(),
                        "deploymentId", pd.getDeploymentId(),
                        "tenantId", pd.getTenantId() != null ? pd.getTenantId() : "",
                        // 使用部署时间作为流程定义的创建时间
                        "created", resolveDeploymentTime(pd.getDeploymentId())
                ))
                .collect(Collectors.toList());
    }

    /**
     * 根据部署ID解析部署时间，格式化为字符串
     */
    private String resolveDeploymentTime(String deploymentId) {
        try {
            if (deploymentId == null || deploymentId.isEmpty()) {
                return "";
            }
            Deployment d = repositoryService.createDeploymentQuery()
                    .deploymentId(deploymentId)
                    .singleResult();
            if (d == null || d.getDeploymentTime() == null) {
                return "";
            }
            return formatDeploymentTime(d.getDeploymentTime());
        } catch (Exception ignored) {
            return "";
        }
    }

    @Override
    public String getProcessDefinitionXml(String processDefinitionId) {
        var processDefinition = repositoryService.getProcessDefinition(processDefinitionId);
        if (processDefinition == null) {
            throw new RuntimeException("流程定义不存在: " + processDefinitionId);
        }

        var deploymentId = processDefinition.getDeploymentId();
        var resourceName = processDefinition.getResourceName();

        try (var inputStream = repositoryService.getResourceAsStream(deploymentId, resourceName)) {
            if (inputStream == null) {
                throw new RuntimeException("无法获取流程定义资源");
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("读取流程定义失败", e);
        }
    }

    @Override
    public void deleteProcessDefinition(String processDefinitionId, boolean cascade) {
        var processDefinition = repositoryService.getProcessDefinition(processDefinitionId);
        if (processDefinition == null) {
            throw new RuntimeException("流程定义不存在: " + processDefinitionId);
        }

        // 通过流程定义获取部署ID，然后删除整个部署
        var deploymentId = processDefinition.getDeploymentId();
        repositoryService.deleteDeployment(deploymentId, cascade);
    }

    @Override
    public boolean validateBpmnXml(String bpmnXml) {
        try {
            // 使用 BpmnValidationHelper 进行验证
            BpmnValidationHelper.BpmnValidationResult result = bpmnValidationHelper.validateBpmnXml(bpmnXml);

            if (!result.isValid()) {
                // 记录验证错误
                System.err.println("BPMN 验证失败: " + result.getMessage());
                if (result.getError() != null) {
                    result.getError().printStackTrace();
                }
                return false;
            }

            // 输出警告信息
            if (!result.getWarnings().isEmpty()) {
                System.out.println("BPMN 验证警告:");
                for (String warning : result.getWarnings()) {
                    System.out.println("  - " + warning);
                }
            }

            return true;
        } catch (Exception e) {
            System.err.println("BPMN 验证异常: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}