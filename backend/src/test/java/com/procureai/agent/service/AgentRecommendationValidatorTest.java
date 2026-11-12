package com.procureai.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.procureai.agent.dto.AgentRunRequest;
import com.procureai.agent.dto.PurchasingRecommendation;
import com.procureai.domain.enums.PurchaseDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentRecommendationValidatorTest {

    private final AgentRecommendationValidator validator = new AgentRecommendationValidator();
    private final AgentRunRequest request = new AgentRunRequest("WM-001", "DEL-01", 800L,
            LocalDate.parse("2026-11-12"), LocalDate.parse("2026-11-19"), null);

    @Test
    void acceptsAllFourAllowedDecisionsWhenTheirQuantityRulesAreMet() {
        assertThat(validate(recommendation(PurchaseDecision.ACCEPT, 800L, List.of("inventory:1"),
                List.of(), null, null))).isEmpty();
        assertThat(validate(recommendation(PurchaseDecision.MODIFY, 600L, List.of("inventory:1"),
                List.of(), null, null))).isEmpty();
        assertThat(validate(recommendation(PurchaseDecision.REJECT, null, List.of("inventory:1"),
                List.of(), null, null))).isEmpty();
        assertThat(validate(recommendation(PurchaseDecision.INVESTIGATE, null, List.of(),
                List.of(), null, null))).isEmpty();
    }

    @Test
    void rejectsUnsupportedQuantityAndReferences() {
        PurchasingRecommendation wrongOriginal = recommendation(PurchaseDecision.ACCEPT, 800L,
                List.of("inventory:invented"), List.of(), null, null);
        wrongOriginal = new PurchasingRecommendation(wrongOriginal.decision(), 10L, wrongOriginal.proposedQuantity(),
                null, null, wrongOriginal.recommendationSummary(), wrongOriginal.evidenceReferences(),
                wrongOriginal.keySupportingFacts(), wrongOriginal.risks(), wrongOriginal.unresolvedQuestions(),
                wrongOriginal.constraintEvaluationReferences(), wrongOriginal.confidence());

        assertThat(validate(wrongOriginal)).anyMatch(message -> message.contains("match the run request"));
        assertThat(validate(recommendation(PurchaseDecision.MODIFY, 800L, List.of("inventory:1"),
                List.of(), null, null))).anyMatch(message -> message.contains("different from the original"));
        assertThat(validate(recommendation(PurchaseDecision.ACCEPT, 800L, List.of("inventory:invented"),
                List.of(), null, null))).anyMatch(message -> message.contains("not returned by a tool"));
        assertThat(validate(recommendation(PurchaseDecision.ACCEPT, 800L, List.of("inventory:1"),
                List.of(), 55L, 99L))).anyMatch(message -> message.contains("not returned by supplier evidence"));
    }

    @Test
    void validatesConstraintReferencesAndConfidence() {
        PurchasingRecommendation recommendation = recommendation(PurchaseDecision.ACCEPT, 800L,
                List.of("inventory:1"), List.of("constraints:fake"), null, null);
        recommendation = new PurchasingRecommendation(recommendation.decision(), recommendation.originalRecommendedQuantity(),
                recommendation.proposedQuantity(), null, null, recommendation.recommendationSummary(),
                recommendation.evidenceReferences(), recommendation.keySupportingFacts(), recommendation.risks(),
                recommendation.unresolvedQuestions(), recommendation.constraintEvaluationReferences(),
                new BigDecimal("1.1"));

        assertThat(validate(recommendation)).anyMatch(message -> message.contains("evaluate_purchase_constraints"));
        assertThat(validate(recommendation)).anyMatch(message -> message.contains("between 0 and 1"));
    }

    private List<String> validate(PurchasingRecommendation recommendation) {
        return validator.validate(recommendation, request, Set.of("inventory:1"), Set.of("constraints:ok"),
                Map.of(55L, 66L));
    }

    private PurchasingRecommendation recommendation(PurchaseDecision decision, Long proposed,
                                                    List<String> evidence, List<String> constraints,
                                                    Long supplier, Long offer) {
        return new PurchasingRecommendation(decision, 800L, proposed, supplier, offer, "Evidence-based recommendation.",
                evidence, List.of("Fact"), List.of("Risk"), List.of("Question"), constraints,
                new BigDecimal("0.75"));
    }
}
