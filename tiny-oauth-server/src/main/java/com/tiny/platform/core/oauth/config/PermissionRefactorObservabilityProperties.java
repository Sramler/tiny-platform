package com.tiny.platform.core.oauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "permission-refactor")
public class PermissionRefactorObservabilityProperties {

    private boolean authorityDiffLogEnabled = true;
    private boolean permissionVersionDebugEnabled = false;
    private boolean failClosedStrictEnabled = true;
    private List<Long> grayTenantAllowList = new ArrayList<>();
    private List<String> grayScopeTypeAllowList = new ArrayList<>(List.of("PLATFORM", "TENANT", "ORG", "DEPT"));
    private double diffSampleRate = 1.0d;
    private boolean menuCanaryReadEnabled = false;
    private List<Long> menuCanaryTenantAllowList = new ArrayList<>();
    private double menuCanarySampleRate = 0.0d;

    public boolean isAuthorityDiffLogEnabled() {
        return authorityDiffLogEnabled;
    }

    public void setAuthorityDiffLogEnabled(boolean authorityDiffLogEnabled) {
        this.authorityDiffLogEnabled = authorityDiffLogEnabled;
    }

    public boolean isPermissionVersionDebugEnabled() {
        return permissionVersionDebugEnabled;
    }

    public void setPermissionVersionDebugEnabled(boolean permissionVersionDebugEnabled) {
        this.permissionVersionDebugEnabled = permissionVersionDebugEnabled;
    }

    public boolean isFailClosedStrictEnabled() {
        return failClosedStrictEnabled;
    }

    public void setFailClosedStrictEnabled(boolean failClosedStrictEnabled) {
        this.failClosedStrictEnabled = failClosedStrictEnabled;
    }

    public List<Long> getGrayTenantAllowList() {
        return grayTenantAllowList;
    }

    public void setGrayTenantAllowList(List<Long> grayTenantAllowList) {
        this.grayTenantAllowList = grayTenantAllowList;
    }

    public List<String> getGrayScopeTypeAllowList() {
        return grayScopeTypeAllowList;
    }

    public void setGrayScopeTypeAllowList(List<String> grayScopeTypeAllowList) {
        this.grayScopeTypeAllowList = grayScopeTypeAllowList;
    }

    public double getDiffSampleRate() {
        return diffSampleRate;
    }

    public void setDiffSampleRate(double diffSampleRate) {
        this.diffSampleRate = diffSampleRate;
    }

    public boolean isMenuCanaryReadEnabled() {
        return menuCanaryReadEnabled;
    }

    public void setMenuCanaryReadEnabled(boolean menuCanaryReadEnabled) {
        this.menuCanaryReadEnabled = menuCanaryReadEnabled;
    }

    public List<Long> getMenuCanaryTenantAllowList() {
        return menuCanaryTenantAllowList;
    }

    public void setMenuCanaryTenantAllowList(List<Long> menuCanaryTenantAllowList) {
        this.menuCanaryTenantAllowList = menuCanaryTenantAllowList;
    }

    public double getMenuCanarySampleRate() {
        return menuCanarySampleRate;
    }

    public void setMenuCanarySampleRate(double menuCanarySampleRate) {
        this.menuCanarySampleRate = menuCanarySampleRate;
    }

    public boolean isContextInGrayWindow(Long tenantId, String scopeType) {
        boolean tenantAllowed = grayTenantAllowList == null || grayTenantAllowList.isEmpty() || grayTenantAllowList.contains(tenantId);
        boolean scopeAllowed = grayScopeTypeAllowList == null || grayScopeTypeAllowList.isEmpty()
                || grayScopeTypeAllowList.stream().anyMatch(item -> item != null && item.equalsIgnoreCase(scopeType));
        return tenantAllowed && scopeAllowed;
    }

    public boolean shouldSample() {
        if (diffSampleRate <= 0d) {
            return false;
        }
        if (diffSampleRate >= 1d) {
            return true;
        }
        return Math.random() <= diffSampleRate;
    }

    public boolean shouldMenuCanaryRead(Long tenantId, String scopeType) {
        if (!menuCanaryReadEnabled) {
            return false;
        }
        if (!isContextInGrayWindow(tenantId, scopeType)) {
            return false;
        }
        boolean tenantAllowed = menuCanaryTenantAllowList == null
            || menuCanaryTenantAllowList.isEmpty()
            || menuCanaryTenantAllowList.contains(tenantId);
        if (!tenantAllowed) {
            return false;
        }
        if (menuCanarySampleRate <= 0d) {
            return false;
        }
        if (menuCanarySampleRate >= 1d) {
            return true;
        }
        return Math.random() <= menuCanarySampleRate;
    }
}
