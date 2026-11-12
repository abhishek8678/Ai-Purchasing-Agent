package com.procureai.domain.enums;

/** Outcome of attempting the approved internal demo purchase-order creation. */
public enum ExecutionStatus {
    NOT_EXECUTED,
    IN_PROGRESS,
    BLOCKED,
    SUCCEEDED,
    VERIFICATION_FAILED,
    FAILED
}
