package com.tiny.platform.core.dict.model;

import com.tiny.platform.infrastructure.core.converter.JsonStringConverter;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 字典类型实体
 */
@Entity
@Table(name = "dict_type", indexes = {
    @Index(name = "idx_tenant_id", columnList = "tenant_id"),
    @Index(name = "idx_dict_code", columnList = "dict_code"),
    @Index(name = "idx_category_id", columnList = "category_id"),
    @Index(name = "idx_enabled", columnList = "enabled")
})
public class DictType implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dict_code", nullable = false, unique = true, length = 64)
    private String dictCode;

    @Column(name = "dict_name", nullable = false, length = 128)
    private String dictName;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId = 0L; // 0表示平台字典，>0表示租户自定义字典

    @Column(name = "is_builtin", nullable = false)
    private Boolean isBuiltin = false; // 是否平台内置（1=内置）

    @Column(name = "builtin_locked", nullable = false)
    private Boolean builtinLocked = false; // 是否锁定（1=锁定不可删除/不可改关键字段）

    @Column(name = "category_id")
    private Long categoryId; // 分类ID，用于字典分组

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "ext_attrs", columnDefinition = "JSON")
    @Convert(converter = JsonStringConverter.class)
    private Map<String, Object> extAttrs;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    public DictType() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getter and Setter methods

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

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
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

