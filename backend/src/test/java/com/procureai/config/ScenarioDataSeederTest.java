package com.procureai.config;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import com.procureai.repository.PurchaseOrderRepository;
import com.procureai.service.ConstraintValidationService;
import com.procureai.service.PurchasingEvidenceSnapshotService;
import com.procureai.service.dto.ConstraintEvaluation;
import com.procureai.service.dto.ConstraintResult;
import com.procureai.service.dto.ConstraintStatus;
import com.procureai.service.dto.InvestigationRequest;
import com.procureai.service.dto.PlanningHorizon;
import com.procureai.service.dto.PurchasingEvidenceSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that each additional scenario dataset produces the intended business situation through the real
 * investigation services. These tests check evidence, not a particular AI decision.
 */
@SpringBootTest
class ScenarioDataSeederTest {

    @Autowired private PurchasingEvidenceSnapshotService snapshotService;
    @Autowired private ConstraintValidationService constraintService;
    @Autowired private ScenarioDataSeeder seeder;
    @Autowired private PurchaseOrderRepository orders;

    private PurchasingEvidenceSnapshot snapshot(String sku, String warehouse, long units) {
        LocalDate today = LocalDate.now(Clock.systemUTC());
        return snapshotService.investigate(new InvestigationRequest(sku, warehouse,
                new PlanningHorizon(today.plusDays(1), today.plusDays(30)), today, units));
    }

    @Test
    @Transactional
    void scenario2SupplierShortfallCountsOnlyConfirmedUnitsAndOffersAlternatives() {
        PurchasingEvidenceSnapshot s = snapshot(ScenarioDataSeeder.SHORTFALL_SKU,
                ScenarioDataSeeder.SHORTFALL_WAREHOUSE, 250L);

        assertThat(s.purchaseOrders().confirmedIncomingUnits()).isEqualTo(250L);
        assertThat(s.purchaseOrders().uncertainPotentialUnits()).isEqualTo(250L);
        assertThat(s.suppliers().offers()).hasSize(3);
        assertThat(s.baselineRequirement().baselineNetRequirementUnits()).isPositive();
        assertThat(s.issues()).isEmpty();
    }

    @Test
    @Transactional
    void scenario3SelectsNewerDemandSensingForecastOverNormalDemandRun() {
        PurchasingEvidenceSnapshot s = snapshot(ScenarioDataSeeder.DEMAND_SKU,
                ScenarioDataSeeder.DEMAND_WAREHOUSE, 150L);

        assertThat(s.forecast().forecastRunId()).isEqualTo(ScenarioDataSeeder.SPIKE_FORECAST_RUN);
        assertThat(s.forecast().observedDemandUnits()).isEqualTo(900L); // vs 360 in the superseded run
        assertThat(s.forecast().complete()).isTrue();
        assertThat(s.purchaseOrders().confirmedIncomingUnits()).isEqualTo(200L);
        // Existing stock plus the confirmed PO no longer covers the revised demand.
        assertThat(s.baselineRequirement().baselineNetRequirementUnits()).isGreaterThan(150L);
    }

    @Test
    @Transactional
    void scenario4BlocksOversizedPurchaseOnBudgetAndStorageButAllowsSmallerQuantity() {
        PurchasingEvidenceSnapshot s = snapshot(ScenarioDataSeeder.CONSTRAINT_SKU,
                ScenarioDataSeeder.CONSTRAINT_WAREHOUSE, 300L);
        Long northstarOffer = s.suppliers().offers().stream()
                .filter(offer -> offer.supplierCode().equals(ScenarioDataSeeder.NORTHSTAR))
                .map(offer -> offer.supplierProductId()).findFirst().orElseThrow();

        assertThat(failed(constraintService.evaluate(s, northstarOffer, 300L)))
                .contains("purchasing_budget", "storage_capacity");
        assertThat(failed(constraintService.evaluate(s, northstarOffer, 120L))).isEmpty();
    }

    @Test
    @Transactional
    void reseedingIsIdempotent() throws Exception {
        long before = orders.count();
        seeder.run();
        assertThat(orders.count()).isEqualTo(before);
        assertThat(snapshot(ScenarioDataSeeder.DEMAND_SKU, ScenarioDataSeeder.DEMAND_WAREHOUSE, 150L)
                .forecast().observedDemandUnits()).isEqualTo(900L);
    }

    private static Set<String> failed(ConstraintEvaluation evaluation) {
        return evaluation.results().stream().filter(result -> result.status() != ConstraintStatus.PASS)
                .map(ConstraintResult::constraintName).collect(Collectors.toSet());
    }
}
