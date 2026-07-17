package com.tiny.platform.infrastructure.workflow.model;

public enum WorkflowBusinessRequestStatus {
    SUBMITTED,
    START_FAILED,
    APPROVED_IN_STEP,
    REJECTED,
    APPLYING,
    APPLIED,
    COMPLETED,
    FAILED
}
