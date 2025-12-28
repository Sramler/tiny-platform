

package com.tiny.platform.infrastructure.auth.resource.dto;

/**
 * 资源排序DTO
 * 用于批量更新资源排序
 */
public class ResourceSortDto {
    
    /**
     * 资源ID
     */
    private Long id;
    
    /**
     * 排序值
     */
    private Integer sort;
    
    /**
     * 父级资源ID
     */
    private Long parentId;
    
    // 构造函数
    public ResourceSortDto() {}
    
    public ResourceSortDto(Long id, Integer sort) {
        this.id = id;
        this.sort = sort;
    }
    
    public ResourceSortDto(Long id, Integer sort, Long parentId) {
        this.id = id;
        this.sort = sort;
        this.parentId = parentId;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Integer getSort() {
        return sort;
    }
    
    public void setSort(Integer sort) {
        this.sort = sort;
    }
    
    public Long getParentId() {
        return parentId;
    }
    
    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }
} 