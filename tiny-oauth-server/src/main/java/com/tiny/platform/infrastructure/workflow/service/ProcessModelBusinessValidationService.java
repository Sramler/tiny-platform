package com.tiny.platform.infrastructure.workflow.service;

import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelScopeType;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ProcessModelBusinessValidationService {

    private static final String CAMUNDA_NS = "http://camunda.org/schema/1.0/bpmn";
    private static final String TP_BUSINESS_MODULE = "tp:businessModule";
    private static final String TP_START_PERMISSION = "tp:startPermission";
    private static final String TP_APPROVE_PERMISSION = "tp:approvePermission";
    private static final String TP_MANAGE_PERMISSION = "tp:managePermission";
    private static final String TP_ROLE_CODES = "tp:roleCodes";
    private static final Pattern FORM_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_./:-]+");
    private static final Pattern CONNECTOR_EXPRESSION_PATTERN = Pattern.compile("\\$\\{[a-z][A-Za-z0-9]*Connector}");

    private final RoleRepository roleRepository;

    public ProcessModelBusinessValidationService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public BusinessValidationResult validate(String bpmnXml, ProcessModelScopeType scopeType, Long tenantId) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return new BusinessValidationResult(List.of("BPMN XML 不能为空"), List.of());
        }

        try {
            Document document = parseXml(bpmnXml);
            Map<String, String> processProperties = readCamundaProperties(document);
            List<UserTaskReference> userTasks = readUserTasks(document);
            List<ServiceTaskReference> serviceTasks = readServiceTasks(document);
            return validateReferences(processProperties, userTasks, serviceTasks, scopeType, tenantId);
        } catch (Exception ex) {
            return new BusinessValidationResult(
                List.of("业务引用校验无法解析 BPMN XML: " + ex.getMessage()),
                List.of()
            );
        }
    }

    public BusinessMetadata readBusinessMetadata(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return BusinessMetadata.empty();
        }
        try {
            return BusinessMetadata.from(readCamundaProperties(parseXml(bpmnXml)));
        } catch (Exception ignored) {
            return BusinessMetadata.empty();
        }
    }

    private BusinessValidationResult validateReferences(
        Map<String, String> processProperties,
        List<UserTaskReference> userTasks,
        List<ServiceTaskReference> serviceTasks,
        ProcessModelScopeType scopeType,
        Long tenantId
    ) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean hasTpMetadata = processProperties.keySet().stream().anyMatch(key -> key.startsWith("tp:"));
        Long lookupTenantId = ProcessModelScopeType.PLATFORM.equals(scopeType) ? null : tenantId;

        if (hasTpMetadata) {
            requireProperty(processProperties, TP_BUSINESS_MODULE, errors);
            requireProperty(processProperties, TP_START_PERMISSION, errors);
            requireProperty(processProperties, TP_APPROVE_PERMISSION, errors);
            requireProperty(processProperties, TP_MANAGE_PERMISSION, errors);
            requireProperty(processProperties, TP_ROLE_CODES, errors);
        } else {
            warnings.add("流程未声明 tp:* 业务元数据，当前仅完成 BPMN 结构校验与节点基础检查");
        }

        Set<String> permissionCodes = new LinkedHashSet<>();
        addIfPresent(permissionCodes, processProperties.get(TP_START_PERMISSION));
        addIfPresent(permissionCodes, processProperties.get(TP_APPROVE_PERMISSION));
        addIfPresent(permissionCodes, processProperties.get(TP_MANAGE_PERMISSION));
        validatePermissions(permissionCodes, lookupTenantId, errors);

        Set<String> declaredRoleCodes = new LinkedHashSet<>(splitCsv(processProperties.get(TP_ROLE_CODES)));
        Set<String> candidateRoleCodes = new LinkedHashSet<>();
        for (UserTaskReference task : userTasks) {
            if (task.candidateGroups().isEmpty()) {
                if (hasTpMetadata) {
                    errors.add("用户任务 " + task.label() + " 未配置 camunda:candidateGroups");
                } else {
                    warnings.add("用户任务 " + task.label() + " 未配置候选组");
                }
            }
            if (task.formKey() == null || task.formKey().isBlank()) {
                if (hasTpMetadata) {
                    errors.add("用户任务 " + task.label() + " 未配置 camunda:formKey");
                } else {
                    warnings.add("用户任务 " + task.label() + " 未配置表单 Key");
                }
            } else if (!FORM_KEY_PATTERN.matcher(task.formKey()).matches()) {
                errors.add("用户任务 " + task.label() + " 的 formKey 格式非法: " + task.formKey());
            }
            for (String group : task.candidateGroups()) {
                String roleCode = toRoleCode(group);
                candidateRoleCodes.add(roleCode);
                if (!declaredRoleCodes.isEmpty() && !declaredRoleCodes.contains(roleCode)) {
                    errors.add("用户任务 " + task.label() + " 的候选组 " + group
                        + " 未包含在 tp:roleCodes 中");
                }
            }
        }

        Set<String> roleCodesToValidate = new LinkedHashSet<>(declaredRoleCodes);
        roleCodesToValidate.addAll(candidateRoleCodes);
        validateRoleCodes(roleCodesToValidate, lookupTenantId, errors);

        for (ServiceTaskReference task : serviceTasks) {
            if (task.delegateExpression() == null || task.delegateExpression().isBlank()) {
                if (hasTpMetadata) {
                    errors.add("服务任务 " + task.label() + " 未配置 camunda:delegateExpression");
                } else {
                    warnings.add("服务任务 " + task.label() + " 未配置 delegateExpression");
                }
            } else if (!CONNECTOR_EXPRESSION_PATTERN.matcher(task.delegateExpression()).matches()) {
                errors.add("服务任务 " + task.label() + " 的 delegateExpression 必须形如 ${xxxConnector}: "
                    + task.delegateExpression());
            }
        }

        if (!userTasks.isEmpty()) {
            warnings.add("表单注册中心尚未接入当前校验链，本次仅校验 formKey 非空与格式");
        }
        if (!serviceTasks.isEmpty()) {
            warnings.add("连接器注册中心尚未接入当前校验链，本次仅校验 delegateExpression 格式");
        }
        return new BusinessValidationResult(List.copyOf(errors), List.copyOf(warnings));
    }

    private void validatePermissions(Set<String> permissionCodes, Long tenantId, List<String> errors) {
        if (permissionCodes.isEmpty()) {
            return;
        }
        Set<String> found = new LinkedHashSet<>(
            roleRepository.findEnabledPermissionCodesByTenantIdAndCodes(tenantId, List.copyOf(permissionCodes))
        );
        permissionCodes.stream()
            .filter(code -> !found.contains(code))
            .forEach(code -> errors.add("权限码不存在、未启用或不属于当前 scope: " + code));
    }

    private void validateRoleCodes(Set<String> roleCodes, Long tenantId, List<String> errors) {
        if (roleCodes.isEmpty()) {
            return;
        }
        Set<String> found = new LinkedHashSet<>(
            roleRepository.findEnabledRoleCodesByTenantIdAndCodes(tenantId, List.copyOf(roleCodes))
        );
        roleCodes.stream()
            .filter(code -> !found.contains(code))
            .forEach(code -> errors.add("角色不存在、未启用或不属于当前 scope: " + code));
    }

    private static void requireProperty(Map<String, String> properties, String name, List<String> errors) {
        String value = properties.get(name);
        if (value == null || value.isBlank()) {
            errors.add("流程缺少业务属性 " + name);
        }
    }

    private static Document parseXml(String bpmnXml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        disableXmlExternalEntities(factory);
        InputSource source = new InputSource(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
        return factory.newDocumentBuilder().parse(source);
    }

    private static Map<String, String> readCamundaProperties(Document document) {
        Map<String, String> properties = new LinkedHashMap<>();
        NodeList propertyNodes = document.getElementsByTagNameNS("*", "property");
        for (int index = 0; index < propertyNodes.getLength(); index++) {
            Node node = propertyNodes.item(index);
            if (node instanceof Element property) {
                String name = blankToNull(property.getAttribute("name"));
                String value = blankToNull(property.getAttribute("value"));
                if (name != null && name.startsWith("tp:")) {
                    properties.put(name, value);
                }
            }
        }
        return properties;
    }

    private static List<UserTaskReference> readUserTasks(Document document) {
        List<UserTaskReference> tasks = new ArrayList<>();
        NodeList taskNodes = document.getElementsByTagNameNS("*", "userTask");
        for (int index = 0; index < taskNodes.getLength(); index++) {
            Node node = taskNodes.item(index);
            if (node instanceof Element task) {
                tasks.add(new UserTaskReference(
                    blankToNull(task.getAttribute("id")),
                    blankToNull(task.getAttribute("name")),
                    splitCsv(attribute(task, "candidateGroups")),
                    blankToNull(attribute(task, "formKey"))
                ));
            }
        }
        return tasks;
    }

    private static List<ServiceTaskReference> readServiceTasks(Document document) {
        List<ServiceTaskReference> tasks = new ArrayList<>();
        NodeList taskNodes = document.getElementsByTagNameNS("*", "serviceTask");
        for (int index = 0; index < taskNodes.getLength(); index++) {
            Node node = taskNodes.item(index);
            if (node instanceof Element task) {
                tasks.add(new ServiceTaskReference(
                    blankToNull(task.getAttribute("id")),
                    blankToNull(task.getAttribute("name")),
                    blankToNull(attribute(task, "delegateExpression"))
                ));
            }
        }
        return tasks;
    }

    private static String attribute(Element element, String localName) {
        String namespaced = blankToNull(element.getAttributeNS(CAMUNDA_NS, localName));
        if (namespaced != null) {
            return namespaced;
        }
        String prefixed = blankToNull(element.getAttribute("camunda:" + localName));
        if (prefixed != null) {
            return prefixed;
        }
        return blankToNull(element.getAttribute(localName));
    }

    private static List<String> splitCsv(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String item : normalized.split("[,;]")) {
            String trimmed = blankToNull(item);
            if (trimmed != null && !values.contains(trimmed)) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private static String toRoleCode(String candidateGroup) {
        String normalized = candidateGroup.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }

    private static void addIfPresent(Set<String> values, String value) {
        String normalized = blankToNull(value);
        if (normalized != null) {
            values.add(normalized);
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void disableXmlExternalEntities(DocumentBuilderFactory factory) {
        trySetFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        trySetFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        trySetAttribute(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        trySetAttribute(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    }

    private static void trySetFeature(DocumentBuilderFactory factory, String feature, boolean enabled) {
        try {
            factory.setFeature(feature, enabled);
        } catch (Exception ignored) {
        }
    }

    private static void trySetAttribute(DocumentBuilderFactory factory, String attribute, String value) {
        try {
            factory.setAttribute(attribute, value);
        } catch (IllegalArgumentException ignored) {
        }
    }

    public record BusinessValidationResult(
        List<String> errors,
        List<String> warnings
    ) {
        public static BusinessValidationResult empty() {
            return new BusinessValidationResult(List.of(), List.of());
        }

        public boolean valid() {
            return errors == null || errors.isEmpty();
        }
    }

    public record BusinessMetadata(
        String businessModule,
        String startPermission,
        String approvePermission,
        String managePermission,
        List<String> roleCodes
    ) {
        public static BusinessMetadata empty() {
            return new BusinessMetadata(null, null, null, null, List.of());
        }

        static BusinessMetadata from(Map<String, String> properties) {
            if (properties == null || properties.isEmpty()) {
                return empty();
            }
            return new BusinessMetadata(
                blankToNull(properties.get(TP_BUSINESS_MODULE)),
                blankToNull(properties.get(TP_START_PERMISSION)),
                blankToNull(properties.get(TP_APPROVE_PERMISSION)),
                blankToNull(properties.get(TP_MANAGE_PERMISSION)),
                splitCsv(properties.get(TP_ROLE_CODES))
            );
        }

        public boolean hasAnyBusinessMetadata() {
            return businessModule != null
                || startPermission != null
                || approvePermission != null
                || managePermission != null
                || !roleCodes.isEmpty();
        }
    }

    private record UserTaskReference(
        String id,
        String name,
        List<String> candidateGroups,
        String formKey
    ) {
        String label() {
            return name == null ? id : name + "(" + id + ")";
        }
    }

    private record ServiceTaskReference(
        String id,
        String name,
        String delegateExpression
    ) {
        String label() {
            return name == null ? id : name + "(" + id + ")";
        }
    }
}
