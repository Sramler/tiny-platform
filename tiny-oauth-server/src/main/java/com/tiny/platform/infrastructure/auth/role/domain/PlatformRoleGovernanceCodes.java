package com.tiny.platform.infrastructure.auth.role.domain;

/**
 * 平台角色治理与审批请求常量（不引入 role_usage）。
 */
public final class PlatformRoleGovernanceCodes {

    public static final String APPROVAL_MODE_NONE = "NONE";
    public static final String APPROVAL_MODE_ONE_STEP = "ONE_STEP";

    public static final String ACTION_GRANT = "GRANT";
    public static final String ACTION_REVOKE = "REVOKE";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCELED = "CANCELED";
    public static final String STATUS_APPLIED = "APPLIED";
    public static final String STATUS_FAILED = "FAILED";

    private PlatformRoleGovernanceCodes() {
    }
}
