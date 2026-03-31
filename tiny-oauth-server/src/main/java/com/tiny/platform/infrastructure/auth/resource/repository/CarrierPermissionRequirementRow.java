package com.tiny.platform.infrastructure.auth.resource.repository;

public interface CarrierPermissionRequirementRow {

    Long getCarrierId();

    Integer getRequirementGroup();

    Integer getSortOrder();

    String getPermissionCode();

    Boolean getNegated();

    /**
     * Backed by permission.enabled.
     * When false, the requirement must be treated as unsatisfied (fail-closed).
     */
    Boolean getPermissionEnabled();
}
