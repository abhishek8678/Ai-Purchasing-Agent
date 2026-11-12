package com.procureai.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import com.procureai.domain.enums.PurchaseOrderStatus;
import com.procureai.service.dto.ConstraintEvaluation;
import com.procureai.service.dto.ConstraintStatus;
import com.procureai.service.dto.InvestigationRequest;
import com.procureai.service.dto.PlanningHorizon;
import com.procureai.service.dto.PurchasingEvidenceSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class PurchasingEvidenceIntegrationTest {

    @Autowired
    private PurchasingEvidenceSnapshotService snapshotService;

    @Autowired
    private ConstraintValidationService constraintValidationService;

    @Test
    @Transactional
    void seededScenarioProducesCoherentEvidenceAndExplicitConstraintEvaluation() {
        LocalDate today = LocalDate.now(Clock.systemUTC());
        PlanningHorizon horizon = new PlanningHorizon(today.plusDays(1), today.plusDays(30));
        PurchasingEvidenceSnapshot snapshot = snapshotService.investigate(new InvestigationRequest(
                "WM-001", "DEL-01", horizon, today, 800L));

        assertEquals(215L, snapshot.inventory().availableUnits());
        assertEquals(30, snapshot.forecast().dailyForecasts().size());
        assertTrue(snapshot.forecast().complete());
        assertEquals(600L, snapshot.forecast().observedDemandUnits());
        assertEquals(2, snapshot.suppliers().offers().size());
        assertEquals(200L, snapshot.purchaseOrders().confirmedIncomingUnits());
        assertEquals(50L, snapshot.purchaseOrders().uncertainPotentialUnits());
        assertEquals(2, snapshot.purchaseOrders().lines().size());
        assertTrue(snapshot.purchaseOrders().lines().stream()
                .anyMatch(line -> line.status() == PurchaseOrderStatus.ACKNOWLEDGED));
        assertTrue(snapshot.purchaseOrders().lines().stream()
                .filter(line -> line.status() == PurchaseOrderStatus.DRAFT)
                .allMatch(line -> line.confirmedOutstandingUnits() == 0 && line.unconfirmedPotentialUnits() == 0));
        assertEquals(275L, snapshot.baselineRequirement().baselineNetRequirementUnits());
        assertTrue(snapshot.budget().availableAmount().signum() >= 0);
        assertTrue(snapshot.storage().availableVolumeM3().signum() > 0);

        Long primaryOfferId = snapshot.suppliers().offers().stream()
                .filter(offer -> offer.supplierCode().equals("SUP-NORTHSTAR"))
                .map(offer -> offer.supplierProductId()).findFirst().orElseThrow();
        ConstraintEvaluation evaluation = constraintValidationService.evaluate(snapshot, primaryOfferId, 800L);
        assertEquals(800L, evaluation.proposedUnits());
        assertNotNull(evaluation.results());
        assertFalse(evaluation.results().isEmpty());
        assertTrue(evaluation.results().stream().allMatch(result -> result.status() == ConstraintStatus.PASS));
        assertFalse(snapshot.issues().stream().anyMatch(issue -> issue.code().equals("MISSING_OR_INVALID_INVENTORY")));
    }
}
