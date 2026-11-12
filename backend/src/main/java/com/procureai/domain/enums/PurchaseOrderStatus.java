package com.procureai.domain.enums;

/** Current approval and fulfillment state of a purchase order. */
public enum PurchaseOrderStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    SUBMITTED,
    ACKNOWLEDGED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CANCELLED,
    FAILED
}
