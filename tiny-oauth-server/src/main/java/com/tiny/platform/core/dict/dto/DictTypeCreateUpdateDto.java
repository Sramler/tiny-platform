package com.tiny.platform.core.dict.dto;

import jakarta.validation.constraints.*;

/**
 * 字典类型创建和更新DTO
 */
public class DictTypeCreateUpdateDto {

    private Long id;

    @NotBlank(message = "字典编码不能为空")
    @Size(max = 64, message = "字典编码长度不能超过64个字符")
    @Pattern(regexp = "^[A-Z_][A-Z0-9_]*$", message = "字典编码只能包含大写字母、数字和下划线，且必须以字母或下划线开头")
    private String dictCode;

    @NotBlank(message = "字典名称不能为空")
    @Size(max = 128, message = "字典名称长度不能超过128个字符")
    private String dictName;

    @Size(max = 255, message = "字典描述长度不能超过255个字符")
    private String description;

    private Long tenantId = 0L;

    private Long categoryId;

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

