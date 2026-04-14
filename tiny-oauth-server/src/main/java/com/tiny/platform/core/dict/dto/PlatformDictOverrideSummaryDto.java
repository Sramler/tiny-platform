package com.tiny.platform.core.dict.dto;

/**
 * 平台字典租户覆盖摘要。
 */
public class PlatformDictOverrideSummaryDto {
    private Long tenantId;
    private String tenantCode;
    private String tenantName;
    private int baselineCount;
    private int overriddenCount;
    private int inheritedCount;
    private int orphanOverlayCount;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

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

    public int getBaselineCount() {
        return baselineCount;
    }

    public void setBaselineCount(int baselineCount) {
        this.baselineCount = baselineCount;
    }

    public int getOverriddenCount() {
        return overriddenCount;
    }

    public void setOverriddenCount(int overriddenCount) {
        this.overriddenCount = overriddenCount;
    }

    public int getInheritedCount() {
        return inheritedCount;
    }

    public void setInheritedCount(int inheritedCount) {
        this.inheritedCount = inheritedCount;
    }

    public int getOrphanOverlayCount() {
        return orphanOverlayCount;
    }

    public void setOrphanOverlayCount(int orphanOverlayCount) {
        this.orphanOverlayCount = orphanOverlayCount;
    }
}
