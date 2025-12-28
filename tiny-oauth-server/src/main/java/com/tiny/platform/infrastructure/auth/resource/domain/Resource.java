package com.tiny.platform.infrastructure.auth.resource.domain;

import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "resource")
public class Resource implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name = ""; // 权限资源名（后端内部识别名）

    @Column(nullable = false, length = 200)
    private String url = ""; // 前端路由路径

    @Column(nullable = false, length = 200)
    private String uri = ""; // 后端 API 路径

    @Column(nullable = false, length = 10)
    private String method = ""; // HTTP 方法

    @Column(nullable = false, length = 200)
    private String icon = ""; // 菜单图标

    @Column(name = "show_icon", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean showIcon = false; // 是否显示图标

    @Column(nullable = false)
    private Integer sort = 0; // 排序权重，越小越靠前

    @Column(nullable = false, length = 200)
    private String component = ""; // Vue 路由组件路径

    @Column(nullable = false, length = 200)
    private String redirect = ""; // 重定向地址（父菜单使用）

    @Column(nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean hidden = false; // 是否在侧边栏隐藏

    @Column(name = "keep_alive", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean keepAlive = false; // 是否缓存页面

    @Column(nullable = false, length = 100)
    private String title = ""; // 前端菜单显示标题

    @Column(nullable = false, length = 100)
    private String permission = ""; // 权限标识，用于前端控制

    @Convert(converter = com.tiny.platform.infrastructure.auth.resource.converter.ResourceTypeConverter.class)
    @Column(nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    private ResourceType type = ResourceType.DIRECTORY; // 资源类型：0-目录，1-菜单，2-按钮，3-接口

    @Column(name = "parent_id")
    private Long parentId; // 父资源ID

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private Boolean enabled = true; // 是否启用

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    @Transient
    private Set<Resource> children = new HashSet<>();

    @ManyToMany(mappedBy = "resources", fetch = FetchType.LAZY)
    private Set<Role> roles = new HashSet<>();

    // 构造函数
    public Resource() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getter和Setter方法
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public ResourceType getType() {
        return type;
    }

    public void setType(ResourceType type) {
        this.type = type;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
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

    public Set<Resource> getChildren() {
        return children;
    }

    public void setChildren(Set<Resource> children) {
        this.children = children;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "Resource{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", title='" + title + '\'' +
                ", type=" + type +
                ", parentId=" + parentId +
                '}';
    }
}