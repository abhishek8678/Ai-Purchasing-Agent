package com.procureai.service.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PurchasingEvidenceSnapshot(
        ProductEvidence product,
        WarehouseEvidence warehouse,
        PlanningHorizon planningHorizon,
        LocalDateTime collectedAt,
        InventoryEvidence inventory,
        ForecastEvidence forecast,
        PurchaseOrderEvidence purchaseOrders,
        SupplierEvidence suppliers,
        BudgetEvidence budget,
        StorageEvidence storage,
        Long safetyStockUnits,
        Integer reviewPeriodDays,
        BaselineRequirementEvidence baselineRequirement,
        List<EvidenceIssue> issues
) {
}
