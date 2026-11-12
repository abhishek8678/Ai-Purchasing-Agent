package com.procureai.service.dto;

import java.time.LocalDate;
import java.util.Objects;

public record InvestigationRequest(
        String productSku,
        String warehouseCode,
        PlanningHorizon planningHorizon,
        LocalDate budgetDate,
        Long recommendedPurchaseUnits
) {
    public InvestigationRequest {
        if (productSku == null || productSku.isBlank()) {
            throw new IllegalArgumentException("Product SKU is required");
        }
        if (warehouseCode == null || warehouseCode.isBlank()) {
            throw new IllegalArgumentException("Warehouse code is required");
        }
        Objects.requireNonNull(planningHorizon, "Planning horizon is required");
        Objects.requireNonNull(budgetDate, "Budget date is required");
        if (recommendedPurchaseUnits != null && recommendedPurchaseUnits <= 0) {
            throw new IllegalArgumentException("Recommended purchase units must be positive when supplied");
        }
    }
}
