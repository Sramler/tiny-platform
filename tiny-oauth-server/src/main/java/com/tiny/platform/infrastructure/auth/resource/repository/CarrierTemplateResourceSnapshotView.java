package com.tiny.platform.infrastructure.auth.resource.repository;

/**
 * Bootstrap 模板主读专用的 carrier 统一快照视图。
 *
 * <p>该视图将 menu / ui_action / api_endpoint 组装成与兼容总表 {@code resource}
 * 等价的最小复制输入，供 TenantBootstrapServiceImpl 迁移模板主读使用。</p>
 */
public interface CarrierTemplateResourceSnapshotView {

    Long getId();

    Long getTenantId();

    String getResourceLevel();

    String getName();

    String getUrl();

    String getUri();

    String getMethod();

    String getIcon();

    Long getShowIcon();

    Integer getSort();

    String getComponent();

    String getRedirect();

    Long getHidden();

    Long getKeepAlive();

    String getTitle();

    String getPermission();

    Long getRequiredPermissionId();

    Integer getTypeCode();

    Long getParentId();

    Long getEnabled();
}
