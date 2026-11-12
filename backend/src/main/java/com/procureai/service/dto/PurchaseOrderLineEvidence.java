package com.procureai.service.dto;

import java.time.LocalDate;

import com.procureai.domain.enums.PurchaseOrderStatus;

public record PurchaseOrderLineEvidence(
        String poNumber,
        PurchaseOrderStatus status,
        String supplierCode,
        LocalDate expectedDeliveryDate,
        boolean dueWithinHorizon,
        boolean eligibleForIncoming,
        long orderedUnits,
        Long confirmedUnits,
        long receivedUnits,
        long confirmedOutstandingUnits,
        long unconfirmedPotentialUnits,
        String warning
) {
}
