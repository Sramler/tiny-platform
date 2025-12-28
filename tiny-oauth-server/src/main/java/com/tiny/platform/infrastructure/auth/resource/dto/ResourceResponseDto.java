

package com.tiny.platform.infrastructure.auth.resource.dto;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;

import java.util.List;

/**
 * 资源响应DTO
 * 用于返回给前端的数据
 *
 * <p>注意：类型信息的禁用配置在 {@link com.tiny.platform.core.oauth.config.jackson.JacksonConfig} 中通过 MixIn 方式配置
 */
public class ResourceResponseDto {

    /**
     * 资源ID
     */
    private Long id;

    /**
     * 资源名称（后端内部识别名）
     */
    private String name;

    /**
     * 显示标题
     */
    private String title;

    /**
     * 前端路由路径
     */
    private String url;

    /**
     * 后端API路径
     */
    private String uri;

    /**
     * HTTP方法
     */
    private String method;

    /**
     * 图标名称
     */
    private String icon;

    /**
     * 是否显示图标
     */
    private Boolean showIcon;

    /**
     * 排序权重
     */
    private Integer sort;

    /**
     * Vue组件路径
     */
    private String component;

    /**
     * 重定向地址
     */
    private String redirect;

    /**
     * 是否隐藏
     */
    private Boolean hidden;

    /**
     * 是否缓存页面
     */
    private Boolean keepAlive;

    /**
     * 权限标识
     */
    private String permission;

    /**
     * 资源类型
     */
    private Integer type;

    /**
     * 资源类型名称
     */
    private String typeName;

    /**
     * 父级资源ID
     */
    private Long parentId;

    /**
     * 子资源列表
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    private List<ResourceResponseDto> children;
    /**
     * 是否叶子节点
     */
    private Boolean leaf;

    /**
     * 是否启用
     */
    private Boolean enabled;

    public ResourceResponseDto() {

    }

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

    public Boolean getShowIcon() {
        return showIcon;
    }

    public void setShowIcon(Boolean showIcon) {
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

    public Boolean getHidden() {
        return hidden;
    }

    public void setHidden(Boolean hidden) {
        this.hidden = hidden;
    }

    public Boolean getKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(Boolean keepAlive) {
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

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Boolean getLeaf() {
        return leaf;
    }

    public void setLeaf(Boolean leaf) {
        this.leaf = leaf;
    }

    public List<ResourceResponseDto> getChildren() {
        return children;
    }

    public void setChildren(List<ResourceResponseDto> children) {
        this.children = children;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    // 新增构造方法，便于SQL/JPQL投影
// 注意 type 用 ResourceType 枚举
    public ResourceResponseDto(
            Long id, String name, String title, String url, String icon,
            Boolean showIcon, Integer sort, String component, String redirect,
            Boolean hidden, Boolean keepAlive, String permission,
            ResourceType type, Long parentId, boolean leaf
    ) {
        this.id = id;
        this.name = name;
        this.title = title;
        this.url = url;
        this.icon = icon;
        this.showIcon = showIcon != null && showIcon;
        this.sort = sort;
        this.component = component;
        this.redirect = redirect;
        this.hidden = hidden != null && hidden;
        this.keepAlive = keepAlive != null && keepAlive;
        this.permission = permission;
        this.type = type != null ? type.getCode() : null;
        this.parentId = parentId;
        this.leaf = leaf;
    }
}