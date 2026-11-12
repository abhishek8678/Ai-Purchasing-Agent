package com.procureai.service.dto;

import java.util.List;

public record PurchaseOrderEvidence(
        String productSku,
        String warehouseCode,
        PlanningHorizon planningHorizon,
        List<PurchaseOrderLineEvidence> lines,
        long confirmedIncomingUnits,
        long uncertainPotentialUnits,
        boolean complete,
        List<String> warnings
) {
}
