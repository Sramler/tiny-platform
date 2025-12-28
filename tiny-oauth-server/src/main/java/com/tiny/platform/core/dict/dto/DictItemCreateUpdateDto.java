package com.tiny.platform.core.dict.dto;

import jakarta.validation.constraints.*;

/**
 * 字典项创建和更新DTO
 */
public class DictItemCreateUpdateDto {

    private Long id;

    @NotNull(message = "字典类型ID不能为空")
    private Long dictTypeId;

    @NotBlank(message = "字典值不能为空")
    @Size(max = 64, message = "字典值长度不能超过64个字符")
    private String value;

    @NotBlank(message = "字典标签不能为空")
    @Size(max = 128, message = "字典标签长度不能超过128个字符")
    private String label;

    @Size(max = 255, message = "字典项描述长度不能超过255个字符")
    private String description;

    private Long tenantId = 0L;

    private Boolean enabled = true;

    @Min(value = 0, message = "排序顺序不能小于0")
    @Max(value = 9999, message = "排序顺序不能大于9999")
    private Integer sortOrder = 0;

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
}

