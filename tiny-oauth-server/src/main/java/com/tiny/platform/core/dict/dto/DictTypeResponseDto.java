package com.tiny.platform.core.dict.dto;

import com.tiny.platform.core.dict.model.DictType;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 字典类型响应DTO
 */
public class DictTypeResponseDto {

    private Long id;
    private String dictCode;
    private String dictName;
    private String description;
    private Long tenantId;
    private Long categoryId;
    private Boolean isBuiltin;
    private Boolean builtinLocked;
    private Boolean enabled;
    private Integer sortOrder;
    private Map<String, Object> extAttrs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    public DictTypeResponseDto() {
    }

    public DictTypeResponseDto(DictType dictType) {
        this.id = dictType.getId();
        this.dictCode = dictType.getDictCode();
        this.dictName = dictType.getDictName();
        this.description = dictType.getDescription();
        this.tenantId = dictType.getTenantId();
        this.categoryId = dictType.getCategoryId();
        this.isBuiltin = dictType.getIsBuiltin();
        this.builtinLocked = dictType.getBuiltinLocked();
        this.enabled = dictType.getEnabled();
        this.sortOrder = dictType.getSortOrder();
        this.extAttrs = dictType.getExtAttrs();
        this.createdAt = dictType.getCreatedAt();
        this.updatedAt = dictType.getUpdatedAt();
        this.createdBy = dictType.getCreatedBy();
        this.updatedBy = dictType.getUpdatedBy();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDictCode() {
        return dictCode;
    }

    public void setDictCode(String dictCode) {
        this.dictCode = dictCode;
    }

    public String getDictName() {
        return dictName;
    }

    public void setDictName(String dictName) {
        this.dictName = dictName;
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

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Boolean getIsBuiltin() {
        return isBuiltin;
    }

    public void setIsBuiltin(Boolean isBuiltin) {
        this.isBuiltin = isBuiltin;
    }

    public Boolean getBuiltinLocked() {
        return builtinLocked;
    }

    public void setBuiltinLocked(Boolean builtinLocked) {
        this.builtinLocked = builtinLocked;
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

