package com.tiny.platform.infrastructure.scheduling.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.infrastructure.scheduling.exception.SchedulingExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 轻量级 JSON Schema 校验服务。
 * 仅支持 required/ type / enum / minimum / maximum / minLength / maxLength / pattern / properties。
 */
@Service
public class JsonSchemaValidationService {

    private static final Logger logger = LoggerFactory.getLogger(JsonSchemaValidationService.class);

    private final ObjectMapper objectMapper;
    private final Map<String, JsonNode> schemaCache = new ConcurrentHashMap<>();

    public JsonSchemaValidationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validate(String schemaJson, Map<String, Object> params) {
        if (schemaJson == null || schemaJson.trim().isEmpty() || params == null) {
            return;
        }
        try {
            JsonNode schemaNode = schemaCache.computeIfAbsent(schemaJson, key -> {
                try {
                    return objectMapper.readTree(key);
                } catch (Exception e) {
                throw SchedulingExceptions.validation("解析 JSON Schema 失败: %s", e.getMessage());
                }
            });
            doValidate(schemaNode, params, "$");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw SchedulingExceptions.validation("参数校验失败: %s", e.getMessage());
        }
    }

    private void doValidate(JsonNode schemaNode, Map<String, Object> params, String path) {
        List<String> errors = new ArrayList<>();

        // required
        JsonNode requiredNode = schemaNode.get("required");
        if (requiredNode != null && requiredNode.isArray()) {
            for (JsonNode field : requiredNode) {
                String name = field.asText();
                if (!params.containsKey(name) || params.get(name) == null) {
                    errors.add(path + "." + name + " 为必填字段");
                }
            }
        }

        JsonNode propertiesNode = schemaNode.get("properties");
        if (propertiesNode != null && propertiesNode.isObject()) {
            Iterator<String> fieldNames = propertiesNode.fieldNames();
            while (fieldNames.hasNext()) {
                String key = fieldNames.next();
                JsonNode propSchema = propertiesNode.get(key);
                Object value = params.get(key);
                if (value == null) {
                    continue;
                }
                validateProperty(path + "." + key, propSchema, value, errors);
            }
        }

        if (!errors.isEmpty()) {
            String message = String.join("; ", errors);
            logger.warn("参数校验失败: {}", message);
            throw new IllegalArgumentException("参数校验失败: " + message);
        }
    }

    @SuppressWarnings("unchecked")
    private void validateProperty(String path, JsonNode schema, Object value, List<String> errors) {
        String type = schema.has("type") ? schema.get("type").asText() : null;

        if (type != null) {
            switch (type) {
                case "string":
                    if (!(value instanceof String)) {
                        errors.add(path + " 应为字符串");
                        return;
                    }
                    String str = (String) value;
                    if (schema.has("minLength") && str.length() < schema.get("minLength").asInt()) {
                        errors.add(path + " 长度不能小于 " + schema.get("minLength").asInt());
                    }
                    if (schema.has("maxLength") && str.length() > schema.get("maxLength").asInt()) {
                        errors.add(path + " 长度不能大于 " + schema.get("maxLength").asInt());
                    }
                    if (schema.has("pattern")) {
                        Pattern pattern = Pattern.compile(schema.get("pattern").asText());
                        if (!pattern.matcher(str).matches()) {
                            errors.add(path + " 不匹配正则表达式 " + schema.get("pattern").asText());
                        }
                    }
                    break;
                case "number":
                case "integer":
                    if (!(value instanceof Number)) {
                        errors.add(path + " 应为数字");
                        return;
                    }
                    BigDecimal number = new BigDecimal(value.toString());
                    if (schema.has("minimum") && number.compareTo(schema.get("minimum").decimalValue()) < 0) {
                        errors.add(path + " 不能小于 " + schema.get("minimum").asText());
                    }
                    if (schema.has("maximum") && number.compareTo(schema.get("maximum").decimalValue()) > 0) {
                        errors.add(path + " 不能大于 " + schema.get("maximum").asText());
                    }
                    break;
                case "boolean":
                    if (!(value instanceof Boolean)) {
                        errors.add(path + " 应为布尔值");
                    }
                    break;
                case "object":
                    if (!(value instanceof Map)) {
                        errors.add(path + " 应为对象");
                        return;
                    }
                    doValidate(schema, (Map<String, Object>) value, path);
                    break;
                case "array":
                    if (!(value instanceof List<?> listValue)) {
                        errors.add(path + " 应为数组");
                        return;
                    }
                    JsonNode items = schema.get("items");
                    if (items != null && items.isObject()) {
                        for (int i = 0; i < listValue.size(); i++) {
                            Object element = listValue.get(i);
                            validateProperty(path + "[" + i + "]", items, element, errors);
                        }
                    }
                    break;
                default:
                    break;
            }
        }

        // enum
        if (schema.has("enum") && schema.get("enum").isArray()) {
            boolean match = false;
            for (JsonNode enumNode : schema.get("enum")) {
                if (Objects.equals(convertJsonNode(enumNode), value)) {
                    match = true;
                    break;
                }
            }
            if (!match) {
                errors.add(path + " 值不在允许范围内");
            }
        }
    }

    private Object convertJsonNode(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNull()) {
            return null;
        }
        return node.toString();
    }
}

