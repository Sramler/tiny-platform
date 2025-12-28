package com.tiny.platform.application.oauth.model;

import com.tiny.platform.application.oauth.enums.ResourceType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false) // 建议只手动标注使用
public class ResourceTypeConverter implements AttributeConverter<ResourceType, Integer> {

    @Override
    public Integer convertToDatabaseColumn(ResourceType attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public ResourceType convertToEntityAttribute(Integer dbData) {
        return ResourceType.fromCode(dbData);
    }
}