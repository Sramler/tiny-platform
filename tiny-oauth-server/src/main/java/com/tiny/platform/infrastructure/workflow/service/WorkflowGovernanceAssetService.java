package com.tiny.platform.infrastructure.workflow.service;

import tools.jackson.databind.ObjectMapper;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.runtime.version.RuntimeVersionKey;
import com.tiny.platform.infrastructure.runtime.version.RuntimeVersionStore;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelScopeType;
import com.tiny.platform.infrastructure.workflow.model.WorkflowGovernanceAssetEntity;
import com.tiny.platform.infrastructure.workflow.repository.WorkflowGovernanceAssetRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class WorkflowGovernanceAssetService {

    private static final String ASSET_TYPE_CONNECTOR = "CONNECTOR";
    private static final String ASSET_TYPE_TEMPLATE = "TEMPLATE";
    private static final String ASSET_TYPE_CONFIG = "CONFIG";

    private final WorkflowGovernanceAssetRepository repository;
    private final RuntimeVersionStore runtimeVersionStore;
    private final ObjectMapper objectMapper;

    public WorkflowGovernanceAssetService(
        WorkflowGovernanceAssetRepository repository,
        RuntimeVersionStore runtimeVersionStore,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.runtimeVersionStore = runtimeVersionStore;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> publishConnector(Map<String, Object> variables, Authentication authentication) {
        ScopeContext scope = currentScope();
        return publishConnector(variables, authentication, scope.scopeType(), scope.tenantId());
    }

    @Transactional
    public Map<String, Object> publishConnector(
        Map<String, Object> variables,
        Authentication authentication,
        ProcessModelScopeType scopeType,
        Long tenantId
    ) {
        return publish(
            new ScopeContext(scopeType, tenantId),
            ASSET_TYPE_CONNECTOR,
            requiredText(variables, "connectorKey"),
            firstText(variables, "connectorName", "connectorKey"),
            requiredText(variables, "connectorAction"),
            connectorStatus(requiredText(variables, "connectorAction")),
            variables,
            authentication,
            "workflow_connector_publish"
        );
    }

    @Transactional
    public Map<String, Object> publishTemplate(Map<String, Object> variables, Authentication authentication) {
        ScopeContext scope = currentScope();
        return publishTemplate(variables, authentication, scope.scopeType(), scope.tenantId());
    }

    @Transactional
    public Map<String, Object> publishTemplate(
        Map<String, Object> variables,
        Authentication authentication,
        ProcessModelScopeType scopeType,
        Long tenantId
    ) {
        return publish(
            new ScopeContext(scopeType, tenantId),
            ASSET_TYPE_TEMPLATE,
            requiredText(variables, "templateKey"),
            firstText(variables, "templateName", "templateKey"),
            requiredText(variables, "publishAction"),
            templateStatus(requiredText(variables, "publishAction")),
            variables,
            authentication,
            "workflow_template_publish"
        );
    }

    @Transactional
    public Map<String, Object> publishConfig(Map<String, Object> variables, Authentication authentication) {
        ScopeContext scope = currentScope();
        return publishConfig(variables, authentication, scope.scopeType(), scope.tenantId());
    }

    @Transactional
    public Map<String, Object> publishConfig(
        Map<String, Object> variables,
        Authentication authentication,
        ProcessModelScopeType scopeType,
        Long tenantId
    ) {
        return publish(
            new ScopeContext(scopeType, tenantId),
            ASSET_TYPE_CONFIG,
            requiredText(variables, "configKey"),
            firstText(variables, "configName", "configKey"),
            requiredText(variables, "changeType"),
            configStatus(requiredText(variables, "changeType")),
            variables,
            authentication,
            "workflow_config_change"
        );
    }

    private Map<String, Object> publish(
        ScopeContext scope,
        String assetType,
        String assetKey,
        String assetName,
        String actionCode,
        String status,
        Map<String, Object> variables,
        Authentication authentication,
        String versionDomain
    ) {
        Integer currentVersion = repository.findMaxVersionInScope(scope.scopeType(), scope.tenantId(), assetType, assetKey);
        int nextVersion = currentVersion == null ? 1 : currentVersion + 1;

        WorkflowGovernanceAssetEntity entity = new WorkflowGovernanceAssetEntity();
        entity.setScopeType(scope.scopeType());
        entity.setTenantId(scope.tenantId());
        entity.setAssetType(assetType);
        entity.setAssetKey(assetKey);
        entity.setAssetName(assetName);
        entity.setActionCode(actionCode);
        entity.setVersion(nextVersion);
        entity.setStatus(status);
        entity.setPayloadJson(writeJson(maskSensitiveValues(variables)));
        entity.setCreatedBy(actor(authentication));
        entity.setUpdatedBy(actor(authentication));
        entity.setPublishedBy(actor(authentication));
        entity.setPublishedAt(LocalDateTime.now());
        repository.save(entity);

        RuntimeVersionKey key = RuntimeVersionKey.of(
            versionDomain,
            scope.tenantId(),
            scope.scopeType().name(),
            scope.tenantId()
        );
        runtimeVersionStore.bump(key, assetType.toLowerCase(Locale.ROOT) + ":" + assetKey, null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assetType", assetType);
        result.put("assetKey", assetKey);
        result.put("assetName", assetName);
        result.put("actionCode", actionCode);
        result.put("status", status);
        result.put("version", nextVersion);
        result.put("governanceAssetId", entity.getId());
        entity.setResultJson(writeJson(result));
        repository.save(entity);
        return result;
    }

    private static String connectorStatus(String actionCode) {
        String normalized = normalize(actionCode);
        return switch (normalized) {
            case "RETIRE" -> "RETIRED";
            case "UPDATE", "PUBLISH" -> "PUBLISHED";
            default -> "PUBLISHED";
        };
    }

    private static String templateStatus(String actionCode) {
        String normalized = normalize(actionCode);
        return switch (normalized) {
            case "ARCHIVE" -> "ARCHIVED";
            case "ROLLBACK" -> "ROLLED_BACK";
            case "PUBLISH" -> "PUBLISHED";
            default -> "PUBLISHED";
        };
    }

    private static String configStatus(String actionCode) {
        String normalized = normalize(actionCode);
        return switch (normalized) {
            case "DELETE" -> "RETIRED";
            case "TOGGLE" -> "TOGGLED";
            case "CREATE", "UPDATE" -> "APPLIED";
            default -> "APPLIED";
        };
    }

    private ScopeContext currentScope() {
        if (TenantContext.isPlatformScope()) {
            return new ScopeContext(ProcessModelScopeType.PLATFORM, null);
        }
        Long activeTenantId = TenantContext.getActiveTenantId();
        if (activeTenantId == null || activeTenantId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "当前请求未解析到有效租户上下文");
        }
        return new ScopeContext(ProcessModelScopeType.TENANT, activeTenantId);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "序列化治理资产失败: " + ex.getMessage(), ex);
        }
    }

    private Map<String, Object> maskSensitiveValues(Map<String, Object> source) {
        Map<String, Object> masked = new LinkedHashMap<>();
        if (source == null) {
            return masked;
        }
        source.forEach((key, value) -> masked.put(key, value));
        return masked;
    }

    private static String requiredText(Map<String, Object> variables, String field) {
        String value = optionalText(variables, field);
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "缺少必填业务字段: " + field);
        }
        return value;
    }

    private static String firstText(Map<String, Object> variables, String firstField, String secondField) {
        String first = optionalText(variables, firstField);
        return first != null ? first : optionalText(variables, secondField);
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

    private static String actor(Authentication authentication) {
        return authentication != null && authentication.getName() != null ? authentication.getName() : "system";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record ScopeContext(ProcessModelScopeType scopeType, Long tenantId) {
    }
}
