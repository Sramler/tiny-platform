package com.tiny.platform.core.dict.model;

import com.tiny.platform.infrastructure.core.converter.JsonStringConverter;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 字典项实体
 */
@Entity
@Table(name = "dict_item", indexes = {
    @Index(name = "idx_dict_type_id", columnList = "dict_type_id"),
    @Index(name = "idx_tenant_id", columnList = "tenant_id"),
    @Index(name = "idx_value", columnList = "value"),
    @Index(name = "idx_enabled", columnList = "enabled"),
    @Index(name = "idx_sort_order", columnList = "sort_order")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_dict_type_value_tenant", columnNames = {"dict_type_id", "value", "tenant_id"})
})
public class DictItem implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dict_type_id", nullable = false)
    private Long dictTypeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dict_type_id", insertable = false, updatable = false)
    private DictType dictType;

    @Column(name = "value", nullable = false, length = 64)
    private String value;

    @Column(name = "label", nullable = false, length = 128)
    private String label;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId = 0L; // 0表示平台字典项，>0表示租户自定义字典项

    @Column(name = "is_builtin", nullable = false)
    private Boolean isBuiltin = false; // 是否平台内置（1=内置）

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

    public DictItem() {
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

    public Long getDictTypeId() {
        return dictTypeId;
    }

    public void setDictTypeId(Long dictTypeId) {
        this.dictTypeId = dictTypeId;
    }

    public DictType getDictType() {
        return dictType;
    }

    public void setDictType(DictType dictType) {
        this.dictType = dictType;
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

