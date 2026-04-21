package com.tiny.platform.infrastructure.tenant.dto;

public class TenantInitializationSummaryDto {
    private String tenantCode;
    private String tenantName;
    private String initialAdminUsername;
    private boolean platformTemplateReady;
    private long defaultRoleCount;
    private long defaultMenuCount;
    private long defaultPermissionCount;
    private long defaultUiActionCount;
    private long defaultApiEndpointCount;

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getInitialAdminUsername() {
        return initialAdminUsername;
    }

    public void setInitialAdminUsername(String initialAdminUsername) {
        this.initialAdminUsername = initialAdminUsername;
    }

    public boolean isPlatformTemplateReady() {
        return platformTemplateReady;
    }

    public void setPlatformTemplateReady(boolean platformTemplateReady) {
        this.platformTemplateReady = platformTemplateReady;
    }

    public long getDefaultRoleCount() {
        return defaultRoleCount;
    }

    public void setDefaultRoleCount(long defaultRoleCount) {
        this.defaultRoleCount = defaultRoleCount;
    }

    public long getDefaultMenuCount() {
        return defaultMenuCount;
    }

    public void setDefaultMenuCount(long defaultMenuCount) {
        this.defaultMenuCount = defaultMenuCount;
    }

    public long getDefaultPermissionCount() {
        return defaultPermissionCount;
    }

    public void setDefaultPermissionCount(long defaultPermissionCount) {
        this.defaultPermissionCount = defaultPermissionCount;
    }

    public long getDefaultUiActionCount() {
        return defaultUiActionCount;
    }

    public void setDefaultUiActionCount(long defaultUiActionCount) {
        this.defaultUiActionCount = defaultUiActionCount;
    }

    public long getDefaultApiEndpointCount() {
        return defaultApiEndpointCount;
    }

    public void setDefaultApiEndpointCount(long defaultApiEndpointCount) {
        this.defaultApiEndpointCount = defaultApiEndpointCount;
    }
}
