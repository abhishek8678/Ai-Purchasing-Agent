package com.procureai.agent.dto;

import java.math.BigDecimal;
import java.util.List;

import com.procureai.domain.enums.PurchaseDecision;

public record PurchasingRecommendation(
        PurchaseDecision decision,
        Long originalRecommendedQuantity,
        Long proposedQuantity,
        Long selectedSupplierId,
        Long selectedSupplierProductId,
        String recommendationSummary,
        List<String> evidenceReferences,
        List<String> keySupportingFacts,
        List<String> risks,
        List<String> unresolvedQuestions,
        List<String> constraintEvaluationReferences,
        BigDecimal confidence
) {
}
