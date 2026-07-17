package com.tiny.platform.infrastructure.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.application.oauth.workflow.WorkflowTaskContext;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.dto.TenantCreateUpdateDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantResponseDto;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import com.tiny.platform.infrastructure.tenant.service.TenantService;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelScopeType;
import com.tiny.platform.infrastructure.workflow.model.WorkflowBusinessRequestEntity;
import com.tiny.platform.infrastructure.workflow.model.WorkflowBusinessRequestStatus;
import com.tiny.platform.infrastructure.workflow.repository.WorkflowBusinessRequestRepository;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class WorkflowBusinessRequestService {

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

    private static final Set<String> SENSITIVE_FIELDS = Set.of(
        "initialAdminPassword",
        "initialAdminConfirmPassword",
        "password",
        "secret",
        "clientSecret"
    );

    private final WorkflowBusinessRequestRepository repository;
    private final TenantRepository tenantRepository;
    private final TenantService tenantService;
    private final ObjectMapper objectMapper;
    private final PlatformPermissionPublishService platformPermissionPublishService;
    private final PlatformRoleBaselineService platformRoleBaselineService;
    private final WorkflowGovernanceAssetService governanceAssetService;

    public WorkflowBusinessRequestService(
        WorkflowBusinessRequestRepository repository,
        TenantRepository tenantRepository,
        TenantService tenantService,
        ObjectMapper objectMapper
    ) {
        this(repository, tenantRepository, tenantService, objectMapper, null, null, null);
    }

    @Autowired
    public WorkflowBusinessRequestService(
        WorkflowBusinessRequestRepository repository,
        TenantRepository tenantRepository,
        TenantService tenantService,
        ObjectMapper objectMapper,
        PlatformPermissionPublishService platformPermissionPublishService,
        PlatformRoleBaselineService platformRoleBaselineService,
        WorkflowGovernanceAssetService governanceAssetService
    ) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
        this.tenantService = tenantService;
        this.objectMapper = objectMapper;
        this.platformPermissionPublishService = platformPermissionPublishService;
        this.platformRoleBaselineService = platformRoleBaselineService;
        this.governanceAssetService = governanceAssetService;
    }

    @Transactional
    public Optional<Long> createSubmittedRequest(
        String processKey,
        String activeTenantId,
        Map<String, Object> variables,
        Authentication authentication
    ) {
        if (!isPlatformWorkflow(processKey)) {
            return Optional.empty();
        }

        ProcessModelScopeType scopeType = scopeType(activeTenantId);
        Long tenantId = tenantId(activeTenantId);
        String requestId = requiredText(variables, "requestId");
        String requestTitle = requiredText(variables, "requestTitle");
        String requestReason = requiredText(variables, "requestReason");

        repository.findByScopeAndRequestId(scopeType, tenantId, requestId)
            .ifPresent(existing -> {
                if (!WorkflowBusinessRequestStatus.START_FAILED.equals(existing.getStatus())
                    && !WorkflowBusinessRequestStatus.REJECTED.equals(existing.getStatus())
                    && !WorkflowBusinessRequestStatus.FAILED.equals(existing.getStatus())) {
                    throw BusinessException.conflict("业务申请单已存在: " + requestId);
                }
            });

        WorkflowBusinessRequestEntity entity = new WorkflowBusinessRequestEntity();
        entity.setScopeType(scopeType);
        entity.setTenantId(tenantId);
        entity.setRequestId(requestId);
        entity.setRequestTitle(requestTitle);
        entity.setRequestReason(requestReason);
        entity.setProcessKey(processKey);
        entity.setStatus(WorkflowBusinessRequestStatus.SUBMITTED);
        entity.setCreatedBy(actor(authentication));
        entity.setUpdatedBy(actor(authentication));
        entity.setPayloadJson(writeJson(maskSensitiveValues(variables)));
        repository.save(entity);
        return Optional.of(entity.getId());
    }

    @Transactional
    public void attachProcessInstance(Long requestPk, String processInstanceId, String processDefinitionId, Authentication authentication) {
        if (requestPk == null || requestPk <= 0) {
            return;
        }
        WorkflowBusinessRequestEntity entity = repository.findById(requestPk).orElse(null);
        if (entity == null) {
            return;
        }
        entity.setProcessInstanceId(processInstanceId);
        entity.setProcessDefinitionId(processDefinitionId);
        entity.setUpdatedBy(actor(authentication));
        repository.save(entity);
    }

    @Transactional
    public void markStartFailed(Long requestPk, Exception exception, Authentication authentication) {
        if (requestPk == null || requestPk <= 0) {
            return;
        }
        WorkflowBusinessRequestEntity entity = repository.findById(requestPk).orElse(null);
        if (entity == null) {
            return;
        }
        entity.setStatus(WorkflowBusinessRequestStatus.START_FAILED);
        entity.setErrorMessage(exception == null ? null : exception.getMessage());
        entity.setUpdatedBy(actor(authentication));
        entity.setResultJson(writeJson(Map.of(
            "action", "START_FAILED",
            "error", exception == null ? "" : exception.getMessage()
        )));
        repository.save(entity);
    }

    @Transactional
    public void rejectTask(WorkflowTaskContext taskContext, Map<String, Object> variables, Authentication authentication) {
        WorkflowBusinessRequestEntity entity = findByProcessInstanceId(taskContext.processInstanceId()).orElse(null);
        if (entity == null) {
            return;
        }
        String decision = normalizeDecision(firstText(variables, "decision", "approvalResult"));
        entity.setLastTaskId(taskContext.taskId());
        entity.setLastTaskKey(taskContext.taskDefinitionKey());
        entity.setLastDecision(decision);
        entity.setLastActionBy(actor(authentication));
        entity.setStatus(WorkflowBusinessRequestStatus.REJECTED);
        entity.setCompletedAt(LocalDateTime.now());
        entity.setDomainStatus("REJECTED");
        entity.setUpdatedBy(actor(authentication));
        entity.setResultJson(mergeResultJson(entity.getResultJson(), Map.of(
            "decision", decision,
            "domainStatus", "REJECTED",
            "workflowStatus", "REJECTED",
            "taskId", taskContext.taskId(),
            "taskKey", taskContext.taskDefinitionKey(),
            "taskName", taskContext.taskName(),
            "payload", maskSensitiveValues(variables)
        )));
        repository.save(entity);
    }

    @Transactional
    public void finishTask(
        WorkflowTaskContext taskContext,
        Map<String, Object> variables,
        Authentication authentication,
        boolean processHasOpenTasks
    ) {
        WorkflowBusinessRequestEntity entity = findByProcessInstanceId(taskContext.processInstanceId()).orElse(null);
        if (entity == null) {
            return;
        }
        String decision = normalizeDecision(firstText(variables, "decision", "approvalResult"));
        entity.setLastTaskId(taskContext.taskId());
        entity.setLastTaskKey(taskContext.taskDefinitionKey());
        entity.setLastDecision(decision);
        entity.setLastActionBy(actor(authentication));
        entity.setUpdatedBy(actor(authentication));
        entity.setResultJson(mergeResultJson(entity.getResultJson(), Map.of(
            "decision",
            decision,
            "taskId",
            taskContext.taskId(),
            "taskKey",
            taskContext.taskDefinitionKey(),
            "taskName",
            taskContext.taskName(),
            "workflowStatus",
            processHasOpenTasks ? "APPROVED_IN_STEP" : "COMPLETED",
            "payload",
            maskSensitiveValues(variables)
        )));

        if ("REJECT".equals(decision)) {
            entity.setStatus(WorkflowBusinessRequestStatus.REJECTED);
            entity.setCompletedAt(LocalDateTime.now());
            entity.setDomainStatus("REJECTED");
            repository.save(entity);
            return;
        }

        if (WorkflowBusinessRequestStatus.APPLIED.equals(entity.getStatus())) {
            if (!processHasOpenTasks) {
                entity.setStatus(WorkflowBusinessRequestStatus.COMPLETED);
                entity.setCompletedAt(LocalDateTime.now());
                if (entity.getDomainStatus() == null) {
                    entity.setDomainStatus("COMPLETED");
                }
            }
        } else if (processHasOpenTasks) {
            entity.setStatus(WorkflowBusinessRequestStatus.APPROVED_IN_STEP);
        } else {
            entity.setStatus(WorkflowBusinessRequestStatus.COMPLETED);
            entity.setCompletedAt(LocalDateTime.now());
            entity.setDomainStatus("COMPLETED");
        }
        repository.save(entity);
    }

    @Transactional
    public void applyConnector(DelegateExecution execution, String connectorKey) {
        if (execution == null) {
            return;
        }
        String processInstanceId = execution.getProcessInstanceId();
        WorkflowBusinessRequestEntity entity = findByProcessInstanceId(processInstanceId).orElse(null);
        if (entity == null) {
            return;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connectorKey", connectorKey);
        result.put("processKey", entity.getProcessKey());
        result.put("requestId", entity.getRequestId());

        execution.setVariable("tpBusinessRequestId", entity.getId());
        execution.setVariable("tpBusinessRequestStatus", entity.getStatus().name());
        execution.setVariable("tpBusinessRequestTitle", entity.getRequestTitle());

        entity.setStatus(WorkflowBusinessRequestStatus.APPLYING);
        entity.setUpdatedBy(connectorKey);

        switch (entity.getProcessKey()) {
            case "platform_tenant_onboarding" -> result.putAll(applyTenantOnboarding(execution, entity));
            case "platform_tenant_plan_change" -> result.putAll(applyTenantPlanChange(execution, entity));
            case "platform_tenant_suspend_restore" -> result.putAll(applyTenantLifecycle(execution, entity));
            case "platform_permission_publish" -> result.putAll(applyPermissionPublish(execution, entity));
            case "platform_role_baseline_change" -> result.putAll(applyRoleBaseline(execution, entity));
            case "platform_connector_publish" -> result.putAll(applyConnectorPublish(execution, entity));
            case "platform_template_publish" -> result.putAll(applyTemplatePublish(execution, entity));
            case "platform_config_change" -> result.putAll(applyConfigChange(execution, entity));
            default -> result.put("domainStatus", "IGNORED");
        }

        entity.setStatus(WorkflowBusinessRequestStatus.APPLIED);
        entity.setAppliedAt(LocalDateTime.now());
        entity.setResultJson(writeJson(result));
        repository.save(entity);

        execution.setVariable("tpBusinessRequestStatus", entity.getStatus().name());
        execution.setVariable("tpBusinessResourceType", entity.getDomainResourceType());
        execution.setVariable("tpBusinessResourceId", entity.getDomainResourceId());
        execution.setVariable("tpBusinessResourceStatus", entity.getDomainStatus());
        execution.setVariable("tpBusinessConnectorKey", connectorKey);
    }

    public boolean isPlatformWorkflow(String processKey) {
        return processKey != null && PLATFORM_WORKFLOW_KEYS.contains(processKey);
    }

    public boolean isRejectDecision(Map<String, Object> variables) {
        return "REJECT".equals(normalizeDecision(firstText(variables, "decision", "approvalResult")));
    }

    private Map<String, Object> applyTenantOnboarding(DelegateExecution execution, WorkflowBusinessRequestEntity entity) {
        Map<String, Object> variables = execution.getVariables();
        TenantCreateUpdateDto dto = new TenantCreateUpdateDto();
        dto.setCode(requiredText(variables, "tenantCode"));
        dto.setName(requiredText(variables, "tenantName"));
        dto.setPlanCode(requiredText(variables, "planCode"));
        dto.setDomain(optionalText(variables, "tenantDomain"));
        dto.setEnabled(true);
        dto.setRemark(optionalText(variables, "requestReason"));
        dto.setInitialAdminUsername(optionalText(variables, "initialAdminUsername"));
        dto.setInitialAdminNickname(optionalText(variables, "initialAdminNickname"));
        dto.setInitialAdminEmail(optionalText(variables, "initialAdminEmail"));
        dto.setInitialAdminPhone(optionalText(variables, "initialAdminPhone"));
        dto.setInitialAdminPassword(optionalText(variables, "initialAdminPassword"));
        dto.setInitialAdminConfirmPassword(optionalText(variables, "initialAdminConfirmPassword"));

        boolean hasAdminCredential = dto.getInitialAdminUsername() != null
            && dto.getInitialAdminPassword() != null
            && dto.getInitialAdminConfirmPassword() != null;
        if (hasAdminCredential) {
            TenantResponseDto response = tenantService.create(dto);
            entity.setDomainResourceType("TENANT");
            entity.setDomainResourceId(String.valueOf(response.getId()));
            entity.setDomainStatus("TENANT_CREATED");
            return Map.of(
                "domainStatus", "TENANT_CREATED",
                "tenantId", response.getId(),
                "tenantCode", response.getCode(),
                "tenantName", response.getName()
            );
        }

        entity.setDomainResourceType("TENANT");
        entity.setDomainStatus("AWAITING_TENANT_CREDENTIALS");
        return Map.of("domainStatus", "AWAITING_TENANT_CREDENTIALS");
    }

    private Map<String, Object> applyTenantPlanChange(DelegateExecution execution, WorkflowBusinessRequestEntity entity) {
        Map<String, Object> variables = execution.getVariables();
        Long tenantId = resolveTenantId(variables);
        String targetPlanCode = requiredText(variables, "targetPlanCode");
        TenantCreateUpdateDto dto = new TenantCreateUpdateDto();
        dto.setPlanCode(targetPlanCode);
        TenantResponseDto response = tenantService.update(tenantId, dto);
        entity.setDomainResourceType("TENANT");
        entity.setDomainResourceId(String.valueOf(response.getId()));
        entity.setDomainStatus("TENANT_PLAN_UPDATED");
        return Map.of(
            "domainStatus", "TENANT_PLAN_UPDATED",
            "tenantId", response.getId(),
            "tenantCode", response.getCode(),
            "planCode", response.getPlanCode()
        );
    }

    private Map<String, Object> applyTenantLifecycle(DelegateExecution execution, WorkflowBusinessRequestEntity entity) {
        Map<String, Object> variables = execution.getVariables();
        Long tenantId = resolveTenantId(variables);
        String action = requiredText(variables, "lifecycleAction").toUpperCase(Locale.ROOT);
        TenantResponseDto response = "RESTORE".equals(action)
            ? tenantService.unfreeze(tenantId)
            : tenantService.freeze(tenantId);
        entity.setDomainResourceType("TENANT");
        entity.setDomainResourceId(String.valueOf(response.getId()));
        entity.setDomainStatus("RESTORE".equals(action) ? "TENANT_RESTORED" : "TENANT_SUSPENDED");
        return Map.of(
            "domainStatus", entity.getDomainStatus(),
            "tenantId", response.getId(),
            "tenantCode", response.getCode(),
            "lifecycleStatus", response.getLifecycleStatus()
        );
    }

    private Map<String, Object> applyPermissionPublish(DelegateExecution execution, WorkflowBusinessRequestEntity entity) {
        if (platformPermissionPublishService == null) {
            return recordGovernanceClosure("PERMISSION", entity);
        }
        Map<String, Object> result = new LinkedHashMap<>(platformPermissionPublishService.publish(
            execution.getVariables(),
            null,
            entity.getScopeType(),
            entity.getTenantId()
        ));
        String changeType = optionalText(execution.getVariables(), "changeType");
        entity.setDomainResourceType("PERMISSION");
        entity.setDomainResourceId(stringValue(result.get("permissionId"), optionalText(execution.getVariables(), "permissionCode")));
        entity.setDomainStatus("DEPRECATE".equalsIgnoreCase(nullToEmpty(changeType))
            ? "PERMISSION_DEPRECATED"
            : "PERMISSION_PUBLISHED");
        result.put("domainStatus", entity.getDomainStatus());
        return result;
    }

    private Map<String, Object> applyRoleBaseline(DelegateExecution execution, WorkflowBusinessRequestEntity entity) {
        if (platformRoleBaselineService == null) {
            return recordGovernanceClosure("ROLE", entity);
        }
        Map<String, Object> result = new LinkedHashMap<>(platformRoleBaselineService.publish(
            execution.getVariables(),
            null,
            entity.getScopeType(),
            entity.getTenantId()
        ));
        String changeType = optionalText(execution.getVariables(), "changeType");
        entity.setDomainResourceType("ROLE");
        entity.setDomainResourceId(stringValue(result.get("roleId"), optionalText(execution.getVariables(), "roleCode")));
        entity.setDomainStatus("DEPRECATE".equalsIgnoreCase(nullToEmpty(changeType))
            ? "ROLE_DEPRECATED"
            : "ROLE_BASELINE_APPLIED");
        result.put("domainStatus", entity.getDomainStatus());
        return result;
    }

    private Map<String, Object> applyConnectorPublish(DelegateExecution execution, WorkflowBusinessRequestEntity entity) {
        if (governanceAssetService == null) {
            return recordGovernanceClosure("CONNECTOR", entity);
        }
        Map<String, Object> result = new LinkedHashMap<>(governanceAssetService.publishConnector(
            execution.getVariables(),
            null,
            entity.getScopeType(),
            entity.getTenantId()
        ));
        setGovernanceDomain(entity, result, "CONNECTOR");
        return result;
    }

    private Map<String, Object> applyTemplatePublish(DelegateExecution execution, WorkflowBusinessRequestEntity entity) {
        if (governanceAssetService == null) {
            return recordGovernanceClosure("TEMPLATE", entity);
        }
        Map<String, Object> result = new LinkedHashMap<>(governanceAssetService.publishTemplate(
            execution.getVariables(),
            null,
            entity.getScopeType(),
            entity.getTenantId()
        ));
        setGovernanceDomain(entity, result, "TEMPLATE");
        return result;
    }

    private Map<String, Object> applyConfigChange(DelegateExecution execution, WorkflowBusinessRequestEntity entity) {
        if (governanceAssetService == null) {
            return recordGovernanceClosure("CONFIG", entity);
        }
        Map<String, Object> result = new LinkedHashMap<>(governanceAssetService.publishConfig(
            execution.getVariables(),
            null,
            entity.getScopeType(),
            entity.getTenantId()
        ));
        setGovernanceDomain(entity, result, "CONFIG");
        return result;
    }

    private void setGovernanceDomain(WorkflowBusinessRequestEntity entity, Map<String, Object> result, String resourceType) {
        entity.setDomainResourceType(resourceType);
        entity.setDomainResourceId(stringValue(result.get("governanceAssetId"), stringValue(result.get("assetKey"), entity.getRequestId())));
        entity.setDomainStatus(resourceType + "_" + nullToEmpty(stringValue(result.get("status"), "APPLIED")));
        result.put("domainStatus", entity.getDomainStatus());
    }

    private Map<String, Object> recordGovernanceClosure(String resourceType, WorkflowBusinessRequestEntity entity) {
        entity.setDomainResourceType(resourceType);
        entity.setDomainResourceId(entity.getRequestId());
        entity.setDomainStatus("RECORDED");
        return Map.of(
            "domainStatus", "RECORDED",
            "resourceType", resourceType,
            "requestId", entity.getRequestId()
        );
    }

    private Optional<WorkflowBusinessRequestEntity> findByProcessInstanceId(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return Optional.empty();
        }
        return repository.findByProcessInstanceId(processInstanceId);
    }

    private Long resolveTenantId(Map<String, Object> variables) {
        Long tenantId = longValue(variables.get("tenantId"));
        if (tenantId != null) {
            return tenantId;
        }
        String tenantCode = optionalText(variables, "tenantCode");
        if (tenantCode != null) {
            Tenant tenant = tenantRepository.findByCode(tenantCode)
                .orElseThrow(() -> BusinessException.notFound("租户不存在: " + tenantCode));
            return tenant.getId();
        }
        throw new BusinessException(ErrorCode.INVALID_PARAMETER, "必须提交 tenantId 或 tenantCode");
    }

    private ProcessModelScopeType scopeType(String activeTenantId) {
        return activeTenantId == null || activeTenantId.isBlank()
            ? ProcessModelScopeType.PLATFORM
            : ProcessModelScopeType.TENANT;
    }

    private Long tenantId(String activeTenantId) {
        if (activeTenantId == null || activeTenantId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(activeTenantId.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "租户上下文不合法: " + activeTenantId, ex);
        }
    }

    private static String actor(Authentication authentication) {
        return authentication != null && authentication.getName() != null ? authentication.getName() : "system";
    }

    private static String requiredText(Map<String, Object> variables, String field) {
        String value = optionalText(variables, field);
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "缺少必填业务字段: " + field);
        }
        return value;
    }

    private static String optionalText(Map<String, Object> variables, String field) {
        if (variables == null) {
            return null;
        }
        Object value = variables.get(field);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String firstText(Map<String, Object> variables, String firstField, String secondField) {
        String first = optionalText(variables, firstField);
        return first != null ? first : optionalText(variables, secondField);
    }

    private static String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String normalizeDecision(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "APPROVED" -> "APPROVE";
            case "REJECTED" -> "REJECT";
            case "APPROVE", "REJECT" -> normalized;
            default -> null;
        };
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "序列化流程业务申请数据失败: " + ex.getMessage(), ex);
        }
    }

    private String mergeResultJson(String existingJson, Map<String, Object> updates) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (existingJson != null && !existingJson.isBlank()) {
            try {
                Map<String, Object> existing = objectMapper.readValue(existingJson, LinkedHashMap.class);
                if (existing != null) {
                    merged.putAll(existing);
                }
            } catch (Exception ignored) {
                merged.put("existingResultJson", existingJson);
            }
        }
        if (updates != null) {
            merged.putAll(updates);
        }
        return writeJson(merged);
    }

    private Map<String, Object> maskSensitiveValues(Map<String, Object> source) {
        Map<String, Object> masked = new LinkedHashMap<>();
        if (source == null) {
            return masked;
        }
        source.forEach((key, value) -> {
            if (SENSITIVE_FIELDS.contains(key)) {
                masked.put(key, "***");
            } else {
                masked.put(key, value);
            }
        });
        return masked;
    }
}
