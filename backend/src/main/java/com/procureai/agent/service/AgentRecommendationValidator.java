package com.procureai.agent.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.procureai.agent.dto.AgentRunRequest;
import com.procureai.agent.dto.PurchasingRecommendation;
import com.procureai.domain.enums.PurchaseDecision;
import org.springframework.stereotype.Component;

@Component
public class AgentRecommendationValidator {

    public List<String> validate(PurchasingRecommendation recommendation,
                                 AgentRunRequest request,
                                 Set<String> returnedEvidenceReferences,
                                 Set<String> returnedConstraintReferences,
                                 Map<Long, Long> returnedSupplierOffers) {
        List<String> errors = new ArrayList<>();
        if (recommendation == null) return List.of("Recommendation is missing.");
        if (recommendation.decision() == null) errors.add("Decision must be one of ACCEPT, MODIFY, REJECT, or INVESTIGATE.");
        if (recommendation.originalRecommendedQuantity() == null
                || !recommendation.originalRecommendedQuantity().equals(request.recommendedPurchaseUnits())) {
            errors.add("Original recommended quantity must match the run request.");
        }
        if (recommendation.decision() == PurchaseDecision.ACCEPT) {
            if (!request.recommendedPurchaseUnits().equals(recommendation.proposedQuantity())) {
                errors.add("ACCEPT requires the proposed quantity to equal the original recommendation.");
            }
        } else if (recommendation.decision() == PurchaseDecision.MODIFY) {
            if (recommendation.proposedQuantity() == null || recommendation.proposedQuantity() <= 0
                    || recommendation.proposedQuantity().equals(request.recommendedPurchaseUnits())) {
                errors.add("MODIFY requires a positive quantity different from the original recommendation.");
            }
        } else if (recommendation.proposedQuantity() != null) {
            errors.add("REJECT and INVESTIGATE must not claim a proposed purchase quantity.");
        }
        if (recommendation.recommendationSummary() == null || recommendation.recommendationSummary().isBlank()
                || recommendation.recommendationSummary().length() > 1200) {
            errors.add("A concise recommendation summary of at most 1200 characters is required.");
        }
        validateStrings(recommendation.evidenceReferences(), "evidenceReferences", errors);
        validateStrings(recommendation.keySupportingFacts(), "keySupportingFacts", errors);
        validateStrings(recommendation.risks(), "risks", errors);
        validateStrings(recommendation.unresolvedQuestions(), "unresolvedQuestions", errors);
        validateStrings(recommendation.constraintEvaluationReferences(), "constraintEvaluationReferences", errors);
        if (recommendation.evidenceReferences() != null
                && !returnedEvidenceReferences.containsAll(recommendation.evidenceReferences())) {
            errors.add("One or more evidence references were not returned by a tool.");
        }
        if (recommendation.constraintEvaluationReferences() != null
                && !returnedConstraintReferences.containsAll(recommendation.constraintEvaluationReferences())) {
            errors.add("One or more constraint references were not returned by evaluate_purchase_constraints.");
        }
        if ((recommendation.selectedSupplierId() == null) != (recommendation.selectedSupplierProductId() == null)) {
            errors.add("Selected supplier and offer identifiers must be supplied together.");
        } else if (recommendation.selectedSupplierProductId() != null) {
            Long actualSupplier = returnedSupplierOffers.get(recommendation.selectedSupplierProductId());
            if (actualSupplier == null || !actualSupplier.equals(recommendation.selectedSupplierId())) {
                errors.add("Selected supplier or offer identifier was not returned by supplier evidence.");
            }
        }
        if (recommendation.confidence() != null && (recommendation.confidence().compareTo(BigDecimal.ZERO) < 0
                || recommendation.confidence().compareTo(BigDecimal.ONE) > 0)) {
            errors.add("Confidence must be between 0 and 1.");
        }
        return List.copyOf(errors);
    }

    private void validateStrings(List<String> values, String field, List<String> errors) {
        if (values == null) {
            errors.add(field + " must be present as an array.");
            return;
        }
        if (values.size() > 20 || values.stream().anyMatch(value -> value == null || value.isBlank() || value.length() > 600)) {
            errors.add(field + " may contain at most 20 non-blank strings of at most 600 characters.");
        }
        if (new HashSet<>(values).size() != values.size()) {
            errors.add(field + " must not contain duplicates.");
        }
    }
}
