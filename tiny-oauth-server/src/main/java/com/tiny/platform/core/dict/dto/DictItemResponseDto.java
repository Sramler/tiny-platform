package com.tiny.platform.core.dict.dto;

import com.tiny.platform.core.dict.model.DictItem;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 字典项响应DTO
 */
public class DictItemResponseDto {

    private Long id;
    private Long dictTypeId;
    private String dictCode; // 字典编码（从关联的DictType获取）
    private String value;
    private String label;
    private String description;
    private Long tenantId;
    private Boolean isBuiltin;
    private Boolean enabled;
    private Integer sortOrder;
    private Map<String, Object> extAttrs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    public DictItemResponseDto() {
    }

    public DictItemResponseDto(DictItem dictItem) {
        this.id = dictItem.getId();
        this.dictTypeId = dictItem.getDictTypeId();
        // 访问 dictType（在 Service 层的 @Transactional 方法中已预加载）
        if (dictItem.getDictType() != null) {
            this.dictCode = dictItem.getDictType().getDictCode();
        }
        this.value = dictItem.getValue();
        this.label = dictItem.getLabel();
        this.description = dictItem.getDescription();
        this.tenantId = dictItem.getTenantId();
        this.isBuiltin = dictItem.getIsBuiltin();
        this.enabled = dictItem.getEnabled();
        this.sortOrder = dictItem.getSortOrder();
        this.extAttrs = dictItem.getExtAttrs();
        this.createdAt = dictItem.getCreatedAt();
        this.updatedAt = dictItem.getUpdatedAt();
        this.createdBy = dictItem.getCreatedBy();
        this.updatedBy = dictItem.getUpdatedBy();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDictTypeId() {
        return dictTypeId;
    }

    public void setDictTypeId(Long dictTypeId) {
        this.dictTypeId = dictTypeId;
    }

    public String getDictCode() {
        return dictCode;
    }

    public void setDictCode(String dictCode) {
        this.dictCode = dictCode;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Boolean getIsBuiltin() {
        return isBuiltin;
    }

    public void setIsBuiltin(Boolean isBuiltin) {
        this.isBuiltin = isBuiltin;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Map<String, Object> getExtAttrs() {
        return extAttrs;
    }

    public void setExtAttrs(Map<String, Object> extAttrs) {
        this.extAttrs = extAttrs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}

