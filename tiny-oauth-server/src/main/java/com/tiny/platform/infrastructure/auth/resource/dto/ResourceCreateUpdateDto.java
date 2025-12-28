

package com.tiny.platform.infrastructure.auth.resource.dto;

import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import jakarta.validation.constraints.*;

/**
 * 资源创建和更新DTO
 * 用于接收前端创建和更新资源的请求数据
 */
public class ResourceCreateUpdateDto {
    
    /**
     * 资源ID（更新时使用）
     */
    private Long id;
    
    /**
     * 资源名称（后端内部识别名）
     */
    @NotBlank(message = "资源名称不能为空")
    @Size(max = 100, message = "资源名称长度不能超过100个字符")
    private String name;
    
    /**
     * 显示标题
     */
    @NotBlank(message = "显示标题不能为空")
    @Size(max = 100, message = "显示标题长度不能超过100个字符")
    private String title;
    
    /**
     * 前端路由路径
     */
    @Size(max = 200, message = "前端路径长度不能超过200个字符")
    private String url;
    
    /**
     * 后端API路径
     */
    @Size(max = 200, message = "API路径长度不能超过200个字符")
    private String uri;
    
    /**
     * HTTP方法
     */
    @Size(max = 10, message = "HTTP方法长度不能超过10个字符")
    private String method = "GET";
    
    /**
     * 图标名称
     */
    @Size(max = 200, message = "图标名称长度不能超过200个字符")
    private String icon;
    
    /**
     * 是否显示图标
     */
    private boolean showIcon = true;
    
    /**
     * 排序权重
     */
    @Min(value = 0, message = "排序权重不能小于0")
    @Max(value = 9999, message = "排序权重不能大于9999")
    private Integer sort = 0;
    
    /**
     * Vue组件路径
     */
    @Size(max = 200, message = "组件路径长度不能超过200个字符")
    private String component;
    
    /**
     * 重定向地址
     */
    @Size(max = 200, message = "重定向地址长度不能超过200个字符")
    private String redirect;
    
    /**
     * 是否隐藏
     */
    private boolean hidden = false;
    
    /**
     * 是否缓存页面
     */
    private boolean keepAlive = false;
    
    /**
     * 权限标识
     */
    @Size(max = 100, message = "权限标识长度不能超过100个字符")
    private String permission;
    
    /**
     * 资源类型
     */
    @NotNull(message = "资源类型不能为空")
    private Integer type = 1; // 默认为菜单类型
    
    /**
     * 父级资源ID
     */
    private Long parentId;
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public String getUri() {
        return uri;
    }
    
    public void setUri(String uri) {
        this.uri = uri;
    }
    
    public String getMethod() {
        return method;
    }
    
    public void setMethod(String method) {
        this.method = method;
    }
    
    public String getIcon() {
        return icon;
    }
    
    public void setIcon(String icon) {
        this.icon = icon;
    }
    
    public boolean isShowIcon() {
        return showIcon;
    }
    
    public void setShowIcon(boolean showIcon) {
        this.showIcon = showIcon;
    }
    
    public Integer getSort() {
        return sort;
    }
    
    public void setSort(Integer sort) {
        this.sort = sort;
    }
    
    public String getComponent() {
        return component;
    }
    
    public void setComponent(String component) {
        this.component = component;
    }
    
    public String getRedirect() {
        return redirect;
    }
    
    public void setRedirect(String redirect) {
        this.redirect = redirect;
    }
    
    public boolean isHidden() {
        return hidden;
    }
    
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
    
    public boolean isKeepAlive() {
        return keepAlive;
    }
    
    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }
    
    public String getPermission() {
        return permission;
    }
    
    public void setPermission(String permission) {
        this.permission = permission;
    }
    
    public Integer getType() {
        return type;
    }
    
    public void setType(Integer type) {
        this.type = type;
    }
    
    public Long getParentId() {
        return parentId;
    }
    
    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }
} 