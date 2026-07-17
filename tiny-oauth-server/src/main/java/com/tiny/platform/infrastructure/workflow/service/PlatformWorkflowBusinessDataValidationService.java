package com.tiny.platform.infrastructure.workflow.service;

import com.tiny.platform.application.oauth.workflow.WorkflowTaskContext;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class PlatformWorkflowBusinessDataValidationService {

    private static final Set<String> PLATFORM_WORKFLOW_KEYS = Set.of(
        "platform_tenant_onboarding",
        "platform_tenant_plan_change",
        "platform_tenant_suspend_restore",
        "platform_permission_publish",
        "platform_role_baseline_change",
        "platform_connector_publish",
        "platform_template_publish",
        "platform_config_change"
    );
    private static final Pattern TENANT_CODE = Pattern.compile("[a-z][a-z0-9-]{2,63}");
    private static final Pattern PERMISSION_CODE = Pattern.compile("([a-z][a-z0-9-]*:)+[a-z][a-z0-9-]*");
    private static final Pattern ROLE_CODE = Pattern.compile("ROLE_PLATFORM_[A-Z0-9_]{2,64}");
    private static final Pattern CONNECTOR_KEY = Pattern.compile("[a-z][A-Za-z0-9]*Connector");
    private static final Pattern TEMPLATE_KEY = Pattern.compile("platform_[a-z0-9_]{3,96}");
    private static final Pattern CONFIG_KEY = Pattern.compile("[A-Za-z0-9_.:-]{3,128}");

    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;

    public PlatformWorkflowBusinessDataValidationService(
        TenantRepository tenantRepository,
        RoleRepository roleRepository
    ) {
        this.tenantRepository = tenantRepository;
        this.roleRepository = roleRepository;
    }

    public Map<String, Object> prepareStartVariables(
        String processKey,
        Map<String, Object> variables,
        Authentication authentication
    ) {
        Map<String, Object> normalizedVariables = mutableCopy(variables);
        if (!isPlatformWorkflow(processKey)) {
            return normalizedVariables;
        }

        List<String> errors = new ArrayList<>();
        requireText(normalizedVariables, "requestId", errors);
        requireText(normalizedVariables, "requestTitle", errors);
        requireText(normalizedVariables, "requestReason", errors);
        validateStartPayload(processKey, normalizedVariables, errors);
        throwIfInvalid(errors);

        normalizedVariables.putIfAbsent("tpProcessKey", processKey);
        normalizedVariables.putIfAbsent("tpWorkflowStatus", "SUBMITTED");
        normalizedVariables.putIfAbsent("tpStartedBy", actor(authentication));
        return normalizedVariables;
    }

    public Map<String, Object> prepareTaskCompleteVariables(
        WorkflowTaskContext taskContext,
        Map<String, Object> variables,
        Authentication authentication
    ) {
        Map<String, Object> normalizedVariables = mutableCopy(variables);
        if (taskContext == null || !isPlatformWorkflow(taskContext.processDefinitionKey())) {
            return normalizedVariables;
        }

        List<String> errors = new ArrayList<>();
        String decision = firstText(normalizedVariables, "decision", "approvalResult");
        String normalizedDecision = normalizeDecision(decision);
        if (normalizedDecision == null) {
            errors.add("审批任务必须提交 decision/approvalResult，取值为 APPROVE 或 REJECT");
        }
        String comment = firstText(normalizedVariables, "comment", "approvalComment");
        if (comment == null) {
            errors.add("审批任务必须提交 comment/approvalComment");
        }
        throwIfInvalid(errors);

        normalizedVariables.put("tpLastTaskId", taskContext.taskId());
        normalizedVariables.put("tpLastTaskKey", taskContext.taskDefinitionKey());
        normalizedVariables.put("tpLastTaskName", taskContext.taskName());
        normalizedVariables.put("tpLastDecision", normalizedDecision);
        normalizedVariables.put("tpLastActionBy", actor(authentication));
        normalizedVariables.put("tpWorkflowStatus", "REJECT".equals(normalizedDecision) ? "REJECTED" : "APPROVED_IN_STEP");
        return normalizedVariables;
    }

    public boolean isPlatformWorkflow(String processKey) {
        return processKey != null && PLATFORM_WORKFLOW_KEYS.contains(processKey);
    }

    private void validateStartPayload(String processKey, Map<String, Object> variables, List<String> errors) {
        switch (processKey) {
            case "platform_tenant_onboarding" -> validateTenantOnboarding(variables, errors);
            case "platform_tenant_plan_change" -> validateTenantPlanChange(variables, errors);
            case "platform_tenant_suspend_restore" -> validateTenantSuspendRestore(variables, errors);
            case "platform_permission_publish" -> validatePermissionPublish(variables, errors);
            case "platform_role_baseline_change" -> validateRoleBaselineChange(variables, errors);
            case "platform_connector_publish" -> validateConnectorPublish(variables, errors);
            case "platform_template_publish" -> validateTemplatePublish(variables, errors);
            case "platform_config_change" -> validateConfigChange(variables, errors);
            default -> {
            }
        }
    }

    private void validateTenantOnboarding(Map<String, Object> variables, List<String> errors) {
        String tenantCode = requireText(variables, "tenantCode", errors);
        requireText(variables, "tenantName", errors);
        requireText(variables, "planCode", errors);
        validatePattern("tenantCode", tenantCode, TENANT_CODE, errors);
        if (tenantCode != null && tenantRepository.findByCode(tenantCode).isPresent()) {
            errors.add("租户编码已存在，不能重复发起开通审批: " + tenantCode);
        }
    }

    private void validateTenantPlanChange(Map<String, Object> variables, List<String> errors) {
        Tenant tenant = requireTenant(variables, errors);
        String targetPlanCode = requireText(variables, "targetPlanCode", errors);
        requireText(variables, "changeReason", errors);
        if (tenant != null) {
            if (!tenant.isEnabled() || tenant.getDeletedAt() != null) {
                errors.add("租户不可用，不能发起套餐变更: " + tenant.getCode());
            }
            if ("DECOMMISSIONED".equalsIgnoreCase(tenant.getLifecycleStatus())) {
                errors.add("已注销租户不能发起套餐变更: " + tenant.getCode());
            }
            if (targetPlanCode != null && targetPlanCode.equalsIgnoreCase(nullToEmpty(tenant.getPlanCode()))) {
                errors.add("目标套餐与当前套餐一致，无需发起变更: " + targetPlanCode);
            }
        }
    }

    private void validateTenantSuspendRestore(Map<String, Object> variables, List<String> errors) {
        Tenant tenant = requireTenant(variables, errors);
        String action = requireOneOf(variables, "lifecycleAction", Set.of("SUSPEND", "RESTORE"), errors);
        requireText(variables, "riskReason", errors);
        if (tenant == null || action == null) {
            return;
        }
        String status = nullToEmpty(tenant.getLifecycleStatus()).toUpperCase(Locale.ROOT);
        if ("DECOMMISSIONED".equals(status)) {
            errors.add("已注销租户不能执行停用/恢复审批: " + tenant.getCode());
        } else if ("SUSPEND".equals(action) && !"ACTIVE".equals(status)) {
            errors.add("只有 ACTIVE 租户允许发起停用审批，当前状态: " + status);
        } else if ("RESTORE".equals(action) && !"FROZEN".equals(status)) {
            errors.add("只有 FROZEN 租户允许发起恢复审批，当前状态: " + status);
        }
    }

    private void validatePermissionPublish(Map<String, Object> variables, List<String> errors) {
        String permissionCode = requireText(variables, "permissionCode", errors);
        String changeType = requireOneOf(variables, "changeType", Set.of("CREATE", "UPDATE", "DEPRECATE"), errors);
        requireText(variables, "impactScope", errors);
        validatePattern("permissionCode", permissionCode, PERMISSION_CODE, errors);
        boolean exists = enabledPermissionExists(permissionCode);
        if ("CREATE".equals(changeType) && exists) {
            errors.add("权限码已存在，不能按新增发布: " + permissionCode);
        } else if (changeType != null && !"CREATE".equals(changeType) && !exists) {
            errors.add("权限码不存在或未启用，不能按变更/废弃发布: " + permissionCode);
        }
    }

    private void validateRoleBaselineChange(Map<String, Object> variables, List<String> errors) {
        String roleCode = requireText(variables, "roleCode", errors);
        String changeType = requireOneOf(variables, "changeType", Set.of("CREATE", "UPDATE", "DEPRECATE"), errors);
        requireText(variables, "baselineReason", errors);
        validatePattern("roleCode", roleCode, ROLE_CODE, errors);
        boolean exists = enabledRoleExists(roleCode);
        if ("CREATE".equals(changeType) && exists) {
            errors.add("平台角色已存在，不能按新增基线发布: " + roleCode);
        } else if (changeType != null && !"CREATE".equals(changeType) && !exists) {
            errors.add("平台角色不存在或未启用，不能按变更/废弃发布: " + roleCode);
        }
    }

    private void validateConnectorPublish(Map<String, Object> variables, List<String> errors) {
        String connectorKey = requireText(variables, "connectorKey", errors);
        requireOneOf(variables, "connectorAction", Set.of("PUBLISH", "UPDATE", "RETIRE"), errors);
        requireText(variables, "ownerTeam", errors);
        requireText(variables, "securityReviewNo", errors);
        validatePattern("connectorKey", connectorKey, CONNECTOR_KEY, errors);
    }

    private void validateTemplatePublish(Map<String, Object> variables, List<String> errors) {
        String templateKey = requireText(variables, "templateKey", errors);
        requireText(variables, "templateVersion", errors);
        requireOneOf(variables, "publishAction", Set.of("PUBLISH", "ARCHIVE", "ROLLBACK"), errors);
        validatePattern("templateKey", templateKey, TEMPLATE_KEY, errors);
    }

    private void validateConfigChange(Map<String, Object> variables, List<String> errors) {
        String configKey = requireText(variables, "configKey", errors);
        requireOneOf(variables, "changeType", Set.of("CREATE", "UPDATE", "DELETE", "TOGGLE"), errors);
        requireText(variables, "rollbackPlan", errors);
        validatePattern("configKey", configKey, CONFIG_KEY, errors);
    }

    private Tenant requireTenant(Map<String, Object> variables, List<String> errors) {
        Long tenantId = longValue(variables.get("tenantId"));
        String tenantCode = textValue(variables.get("tenantCode"));
        Optional<Tenant> tenant = Optional.empty();
        if (tenantId != null) {
            tenant = tenantRepository.findById(tenantId);
        } else if (tenantCode != null) {
            tenant = tenantRepository.findByCode(tenantCode);
        } else {
            errors.add("必须提交 tenantId 或 tenantCode");
            return null;
        }
        if (tenant.isEmpty()) {
            errors.add("租户不存在: " + (tenantId != null ? tenantId : tenantCode));
            return null;
        }
        return tenant.get();
    }

    private boolean enabledPermissionExists(String permissionCode) {
        if (permissionCode == null) {
            return false;
        }
        return roleRepository.findEnabledPermissionCodesByTenantIdAndCodes(null, List.of(permissionCode))
            .contains(permissionCode);
    }

    private boolean enabledRoleExists(String roleCode) {
        if (roleCode == null) {
            return false;
        }
        return roleRepository.findEnabledRoleCodesByTenantIdAndCodes(null, List.of(roleCode))
            .contains(roleCode);
    }

    private static Map<String, Object> mutableCopy(Map<String, Object> variables) {
        return variables == null ? new LinkedHashMap<>() : new LinkedHashMap<>(variables);
    }

    private static String requireText(Map<String, Object> variables, String field, List<String> errors) {
        String value = textValue(variables.get(field));
        if (value == null) {
            errors.add("缺少必填业务字段: " + field);
        }
        return value;
    }

    private static String requireOneOf(
        Map<String, Object> variables,
        String field,
        Set<String> allowedValues,
        List<String> errors
    ) {
        String value = textValue(variables.get(field));
        if (value == null) {
            errors.add("缺少必填业务字段: " + field);
            return null;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        if (!allowedValues.contains(normalized)) {
            errors.add(field + " 取值非法: " + value + "，允许值: " + allowedValues);
            return null;
        }
        return normalized;
    }

    private static void validatePattern(String field, String value, Pattern pattern, List<String> errors) {
        if (value != null && !pattern.matcher(value).matches()) {
            errors.add(field + " 格式非法: " + value);
        }
    }

    private static String firstText(Map<String, Object> variables, String firstField, String secondField) {
        String first = textValue(variables.get(firstField));
        return first != null ? first : textValue(variables.get(secondField));
    }

    private static String normalizeDecision(String value) {
        String normalized = textValue(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if ("APPROVED".equals(normalized)) {
            return "APPROVE";
        }
        if ("REJECTED".equals(normalized)) {
            return "REJECT";
        }
        return Set.of("APPROVE", "REJECT").contains(normalized) ? normalized : null;
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = textValue(value);
        if (text == null) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String actor(Authentication authentication) {
        return authentication != null && authentication.getName() != null ? authentication.getName() : "system";
    }

    private static void throwIfInvalid(List<String> errors) {
        if (!errors.isEmpty()) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "平台流程业务数据校验失败: " + String.join("；", errors)
            );
        }
    }
}
