package com.tiny.platform.infrastructure.tenant.dto;

public class TenantCreateUpdateDto {
    private String code;
    private String name;
    private String domain;
    private Boolean enabled;
    /**
     * 保留字段：生命周期流转需走 freeze/unfreeze/decommission 专用接口。
     * 普通 create/update 不应直接修改该字段。
     */
    private String lifecycleStatus;
    private String planCode;
    private String expiresAt;
    private Integer maxUsers;
    private Integer maxStorageGb;
    private String contactName;
    private String contactEmail;
    private String contactPhone;
    private String remark;
    private String initialAdminUsername;
    private String initialAdminNickname;
    private String initialAdminEmail;
    private String initialAdminPhone;
    private String initialAdminPassword;
    private String initialAdminConfirmPassword;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Integer getMaxUsers() {
        return maxUsers;
    }

    public void setMaxUsers(Integer maxUsers) {
        this.maxUsers = maxUsers;
    }

    public Integer getMaxStorageGb() {
        return maxStorageGb;
    }

    public void setMaxStorageGb(Integer maxStorageGb) {
        this.maxStorageGb = maxStorageGb;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getInitialAdminUsername() {
        return initialAdminUsername;
    }

    public void setInitialAdminUsername(String initialAdminUsername) {
        this.initialAdminUsername = initialAdminUsername;
    }

    public String getInitialAdminNickname() {
        return initialAdminNickname;
    }

    public void setInitialAdminNickname(String initialAdminNickname) {
        this.initialAdminNickname = initialAdminNickname;
    }

    public String getInitialAdminEmail() {
        return initialAdminEmail;
    }

    public void setInitialAdminEmail(String initialAdminEmail) {
        this.initialAdminEmail = initialAdminEmail;
    }

    public String getInitialAdminPhone() {
        return initialAdminPhone;
    }

    public void setInitialAdminPhone(String initialAdminPhone) {
        this.initialAdminPhone = initialAdminPhone;
    }

    public String getInitialAdminPassword() {
        return initialAdminPassword;
    }

    public void setInitialAdminPassword(String initialAdminPassword) {
        this.initialAdminPassword = initialAdminPassword;
    }

    public String getInitialAdminConfirmPassword() {
        return initialAdminConfirmPassword;
    }

    public void setInitialAdminConfirmPassword(String initialAdminConfirmPassword) {
        this.initialAdminConfirmPassword = initialAdminConfirmPassword;
    }
}
