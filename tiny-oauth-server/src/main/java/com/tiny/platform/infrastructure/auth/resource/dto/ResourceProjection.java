package com.tiny.platform.infrastructure.auth.resource.dto;

public interface ResourceProjection {
    Long getId();
    String getName();
    String getTitle();
    String getUrl();
    String getIcon();
    Boolean getShowIcon();
    Integer getSort();
    String getComponent();
    String getRedirect();
    Boolean getHidden();
    Boolean getKeepAlive();
    String getPermission();
    Integer getType();
    Long getParentId();
    Integer getLeaf(); // 叶子节点（无子菜单）
}