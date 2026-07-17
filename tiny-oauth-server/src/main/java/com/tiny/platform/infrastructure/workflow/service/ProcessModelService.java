package com.tiny.platform.infrastructure.workflow.service;

import com.tiny.platform.application.oauth.workflow.BpmnValidationHelper;
import com.tiny.platform.application.oauth.workflow.ProcessEngineService;
import com.tiny.platform.application.oauth.workflow.model.ProcessModelDeployResponse;
import com.tiny.platform.application.oauth.workflow.model.ProcessModelDto;
import com.tiny.platform.application.oauth.workflow.model.ProcessModelGroupDto;
import com.tiny.platform.application.oauth.workflow.model.ProcessModelRequests;
import com.tiny.platform.application.oauth.workflow.model.ProcessModelValidationResponse;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelEntity;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelScopeType;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelStatus;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelValidationStatus;
import com.tiny.platform.infrastructure.workflow.repository.ProcessModelRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(prefix = "camunda.bpm", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProcessModelService {

    private final ProcessModelRepository repository;
    private final BpmnValidationHelper bpmnValidationHelper;
    private final ProcessModelBusinessValidationService businessValidationService;
    private final ProcessEngineService processEngineService;

    public ProcessModelService(
        ProcessModelRepository repository,
        BpmnValidationHelper bpmnValidationHelper,
        ProcessModelBusinessValidationService businessValidationService,
        ProcessEngineService processEngineService
    ) {
        this.repository = repository;
        this.bpmnValidationHelper = bpmnValidationHelper;
        this.businessValidationService = businessValidationService;
        this.processEngineService = processEngineService;
    }

    public List<ProcessModelDto> listCurrentScope() {
        ScopeContext scope = currentScope();
        return toDtosWithRuntimeState(repository.findAllInScope(scope.scopeType(), scope.tenantId()));
    }

    public List<ProcessModelGroupDto> listGroupsCurrentScope() {
        Map<String, List<ProcessModelDto>> groups = new LinkedHashMap<>();
        listCurrentScope().forEach(model -> groups
            .computeIfAbsent(groupKey(model), ignored -> new ArrayList<>())
            .add(model));
        return groups.values().stream()
            .map(ProcessModelGroupDto::from)
            .toList();
    }

    public ProcessModelDto getCurrentScope(Long id) {
        ProcessModelEntity entity = requireScopedModel(id);
        ScopeContext scope = currentScope();
        List<ProcessModelEntity> groupEntities = repository.findAllInScope(scope.scopeType(), scope.tenantId()).stream()
            .filter(model -> Objects.equals(model.getModelKey(), entity.getModelKey()))
            .toList();
        return toDtosWithRuntimeState(groupEntities).stream()
            .filter(model -> Objects.equals(model.id(), id))
            .findFirst()
            .orElseGet(() -> ProcessModelDto.from(entity));
    }

    @Transactional
    public ProcessModelDto create(ProcessModelRequests.Create request, String actor) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "请求体不能为空");
        }
        String bpmnXml = requireText(request.bpmnXml(), "bpmnXml 不能为空");
        BpmnMetadata metadata = extractMetadata(bpmnXml);
        String modelKey = firstText(request.modelKey(), metadata.processId());
        if (modelKey == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "modelKey 不能为空，且 BPMN XML 未包含 process id");
        }
        String name = firstText(request.name(), metadata.processName(), modelKey);
        ScopeContext scope = currentScope();
        int version = request.version() != null && request.version() > 0
            ? request.version()
            : nextVersion(scope, modelKey);

        if (repository.existsByScopeAndModelKeyAndVersion(scope.scopeType(), scope.tenantId(), modelKey, version)) {
            throw BusinessException.alreadyExists("同一作用域下流程模型版本已存在: " + modelKey + " v" + version);
        }

        ProcessModelEntity entity = new ProcessModelEntity();
        entity.setModelKey(modelKey);
        entity.setName(name);
        entity.setDescription(blankToNull(request.description()));
        entity.setScopeType(scope.scopeType());
        entity.setTenantId(scope.tenantId());
        entity.setVersion(version);
        entity.setBpmnXml(bpmnXml);
        entity.setSvg(blankToNull(request.svg()));
        entity.setStatus(ProcessModelStatus.DRAFT);
        entity.setValidationStatus(ProcessModelValidationStatus.NOT_VALIDATED);
        entity.setCreatedBy(actor);
        entity.setUpdatedBy(actor);

        return ProcessModelDto.from(saveModel(entity));
    }

    @Transactional
    public ProcessModelDto update(Long id, ProcessModelRequests.Update request, String actor) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "请求体不能为空");
        }
        ProcessModelEntity entity = requireScopedModel(id);
        if (ProcessModelStatus.DEPLOYED.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_INVALID, "已部署版本不可直接修改，请 fork 新草稿");
        }
        if (request.lockVersion() != null && !request.lockVersion().equals(entity.getLockVersion())) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "模型已被其他人更新，请刷新后重试");
        }

        String bpmnXml = requireText(request.bpmnXml(), "bpmnXml 不能为空");
        BpmnMetadata metadata = extractMetadata(bpmnXml);
        entity.setName(firstText(request.name(), metadata.processName(), entity.getName()));
        entity.setDescription(blankToNull(request.description()));
        entity.setBpmnXml(bpmnXml);
        entity.setSvg(blankToNull(request.svg()));
        entity.setUpdatedBy(actor);
        entity.setStatus(ProcessModelStatus.DRAFT);
        entity.setValidationStatus(ProcessModelValidationStatus.NOT_VALIDATED);
        entity.setValidationSummary(null);

        return ProcessModelDto.from(saveModel(entity));
    }

    @Transactional
    public ProcessModelValidationResponse validate(Long id, String actor) {
        ProcessModelEntity entity = requireScopedModel(id);
        ValidationOutcome outcome = validateModelXml(entity);
        entity.setValidationStatus(outcome.valid()
            ? ProcessModelValidationStatus.PASSED
            : ProcessModelValidationStatus.FAILED);
        entity.setValidationSummary(toValidationSummary(outcome));
        entity.setStatus(outcome.valid() ? ProcessModelStatus.VALIDATED : ProcessModelStatus.DRAFT);
        entity.setUpdatedBy(actor);
        saveModel(entity);

        return new ProcessModelValidationResponse(
            entity.getId(),
            outcome.valid(),
            outcome.message(),
            List.copyOf(outcome.warnings()),
            entity.getValidationStatus().name()
        );
    }

    @Transactional
    public ProcessModelDeployResponse deploy(Long id, String actor) {
        ProcessModelEntity entity = requireScopedModel(id);
        ValidationOutcome validation = validateModelXml(entity);
        if (!validation.valid()) {
            entity.setValidationStatus(ProcessModelValidationStatus.FAILED);
            entity.setValidationSummary(toValidationSummary(validation));
            entity.setUpdatedBy(actor);
            saveModel(entity);
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, validation.message());
        }

        ScopeContext scope = currentScope();
        try {
            String deploymentId = processEngineService.deployProcess(
                entity.getBpmnXml(),
                scope.scopeType() == ProcessModelScopeType.PLATFORM ? null : String.valueOf(scope.tenantId()),
                entity.getName(),
                actor,
                "process-modeler"
            );
            entity.setDeploymentId(deploymentId);
            entity.setProcessDefinitionKey(entity.getModelKey());
            entity.setStatus(ProcessModelStatus.DEPLOYED);
            entity.setValidationStatus(ProcessModelValidationStatus.PASSED);
            entity.setValidationSummary(toValidationSummary(validation));
            entity.setDeployedBy(actor);
            entity.setDeployedAt(LocalDateTime.now());
            entity.setUpdatedBy(actor);
            saveModel(entity);
            return new ProcessModelDeployResponse(
                entity.getId(),
                deploymentId,
                entity.getProcessDefinitionKey(),
                entity.getStatus().name(),
                "流程模型部署成功"
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "流程模型部署失败: " + ex.getMessage(), ex);
        }
    }

    @Transactional
    public void delete(Long id) {
        ProcessModelEntity entity = requireScopedModel(id);
        if (!canDeleteDraft(entity)) {
            throw new BusinessException(
                ErrorCode.RESOURCE_STATE_INVALID,
                "仅允许删除未部署的草稿或已校验草稿"
            );
        }
        repository.delete(entity);
    }

    private ProcessModelEntity requireScopedModel(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "模型 ID 必须为正整数");
        }
        ScopeContext scope = currentScope();
        return repository.findByIdInScope(id, scope.scopeType(), scope.tenantId())
            .orElseThrow(() -> BusinessException.notFound("流程模型不存在或不属于当前作用域"));
    }

    private ProcessModelEntity saveModel(ProcessModelEntity entity) {
        try {
            return repository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "同一作用域下流程模型版本已存在", ex);
        }
    }

    private List<ProcessModelDto> toDtosWithRuntimeState(List<ProcessModelEntity> entities) {
        Map<String, Long> currentRuntimeIds = new LinkedHashMap<>();
        Map<String, List<ProcessModelEntity>> groups = new LinkedHashMap<>();
        entities.forEach(entity -> groups
            .computeIfAbsent(groupKey(entity), ignored -> new ArrayList<>())
            .add(entity));

        groups.forEach((key, groupEntities) -> currentRuntimeModel(groupEntities)
            .ifPresent(model -> currentRuntimeIds.put(key, model.getId())));

        return entities.stream()
            .map(entity -> ProcessModelDto.from(entity, runtimeState(entity, currentRuntimeIds.get(groupKey(entity)))))
            .toList();
    }

    private Optional<ProcessModelEntity> currentRuntimeModel(List<ProcessModelEntity> entities) {
        return entities.stream()
            .filter(ProcessModelService::isCurrentRuntimeCandidate)
            .max(Comparator
                .comparing(ProcessModelEntity::getDeployedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(ProcessModelEntity::getProcessDefinitionVersion, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(ProcessModelEntity::getVersion, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(ProcessModelEntity::getId, Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    private String runtimeState(ProcessModelEntity entity, Long currentRuntimeId) {
        if (entity.getDeploymentId() == null || entity.getDeploymentId().isBlank()) {
            return "NOT_DEPLOYED";
        }
        return Objects.equals(entity.getId(), currentRuntimeId)
            ? "CURRENT_RUNTIME"
            : "HISTORICAL_DEPLOYED";
    }

    private static boolean isCurrentRuntimeCandidate(ProcessModelEntity entity) {
        return entity.getDeploymentId() != null
            && !entity.getDeploymentId().isBlank()
            && ProcessModelStatus.DEPLOYED.equals(entity.getStatus());
    }

    private static boolean canDeleteDraft(ProcessModelEntity entity) {
        return (ProcessModelStatus.DRAFT.equals(entity.getStatus())
            || ProcessModelStatus.VALIDATED.equals(entity.getStatus()))
            && isBlank(entity.getDeploymentId())
            && isBlank(entity.getProcessDefinitionId());
    }

    private int nextVersion(ScopeContext scope, String modelKey) {
        Integer maxVersion = repository.findMaxVersionInScope(scope.scopeType(), scope.tenantId(), modelKey);
        return maxVersion == null ? 1 : maxVersion + 1;
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

    private ValidationOutcome validateModelXml(ProcessModelEntity entity) {
        ScopeContext scope = new ScopeContext(entity.getScopeType(), entity.getTenantId());
        BpmnValidationHelper.BpmnValidationResult bpmnResult = bpmnValidationHelper.validateBpmnXml(entity.getBpmnXml());
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>(bpmnResult.getWarnings());
        if (bpmnResult.isValid()) {
            ProcessModelBusinessValidationService.BusinessValidationResult businessResult =
                businessValidationService.validate(entity.getBpmnXml(), scope.scopeType(), scope.tenantId());
            errors.addAll(businessResult.errors());
            warnings.addAll(businessResult.warnings());
        }
        boolean valid = bpmnResult.isValid() && errors.isEmpty();
        String message = validationMessage(bpmnResult, errors, warnings, valid);
        return new ValidationOutcome(valid, message, List.copyOf(errors), List.copyOf(warnings));
    }

    private static String validationMessage(
        BpmnValidationHelper.BpmnValidationResult bpmnResult,
        List<String> errors,
        List<String> warnings,
        boolean valid
    ) {
        if (!bpmnResult.isValid()) {
            return bpmnResult.getMessage();
        }
        if (!errors.isEmpty()) {
            return "流程模型业务校验失败: " + String.join("；", errors);
        }
        if (!warnings.isEmpty()) {
            return "流程模型校验通过，存在 " + warnings.size() + " 条警告";
        }
        return bpmnResult.getMessage() != null ? bpmnResult.getMessage() : "流程模型校验通过";
    }

    private static String toValidationSummary(ValidationOutcome result) {
        StringBuilder summary = new StringBuilder(result.message() != null ? result.message() : "");
        if (!result.errors().isEmpty()) {
            summary.append("\nErrors:\n");
            result.errors().forEach(error -> summary.append("- ").append(error).append('\n'));
        }
        if (!result.warnings().isEmpty()) {
            summary.append("\nWarnings:\n");
            result.warnings().forEach(warning -> summary.append("- ").append(warning).append('\n'));
        }
        return summary.toString().trim();
    }

    private static String requireText(String value, String message) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, message);
        }
        return normalized;
    }

    private static String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static BpmnMetadata extractMetadata(String bpmnXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            disableXmlExternalEntities(factory);
            InputSource source = new InputSource(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
            Element root = factory.newDocumentBuilder().parse(source).getDocumentElement();
            NodeList processNodes = root.getElementsByTagNameNS("*", "process");
            if (processNodes.getLength() == 0) {
                processNodes = root.getElementsByTagName("process");
            }
            if (processNodes.getLength() == 0 || !(processNodes.item(0) instanceof Element process)) {
                return new BpmnMetadata(null, null);
            }
            return new BpmnMetadata(blankToNull(process.getAttribute("id")), blankToNull(process.getAttribute("name")));
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "BPMN XML 解析失败: " + ex.getMessage(), ex);
        }
    }

    private static String groupKey(ProcessModelDto model) {
        return model.scopeType() + ":" + (model.recordTenantId() == null ? "0" : model.recordTenantId()) + ":" + model.modelKey();
    }

    private static String groupKey(ProcessModelEntity model) {
        return model.getScopeType() + ":" + (model.getTenantId() == null ? "0" : model.getTenantId()) + ":" + model.getModelKey();
    }

    private static void disableXmlExternalEntities(DocumentBuilderFactory factory) {
        trySetFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl");
        trySetFeature(factory, "http://xml.org/sax/features/external-general-entities");
        trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
    }

    private static void trySetFeature(DocumentBuilderFactory factory, String feature) {
        try {
            boolean enabled = !"http://xml.org/sax/features/external-general-entities".equals(feature)
                && !"http://xml.org/sax/features/external-parameter-entities".equals(feature);
            factory.setFeature(feature, enabled);
        } catch (Exception ignored) {
        }
    }

    private record ScopeContext(ProcessModelScopeType scopeType, Long tenantId) {
    }

    private record ValidationOutcome(boolean valid, String message, List<String> errors, List<String> warnings) {
    }

    private record BpmnMetadata(String processId, String processName) {
    }
}
