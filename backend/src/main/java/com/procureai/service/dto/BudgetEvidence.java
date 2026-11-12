package com.procureai.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BudgetEvidence(
        String warehouseCode,
        LocalDate periodStart,
        LocalDate periodEnd,
        String currencyCode,
        BigDecimal allocatedAmount,
        BigDecimal spentAmount,
        BigDecimal committedAmount,
        BigDecimal availableAmount,
        boolean complete,
        List<String> currencyMismatches,
        List<String> warnings
) {
}
