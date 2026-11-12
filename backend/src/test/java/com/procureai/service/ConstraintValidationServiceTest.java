package com.procureai.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.procureai.domain.enums.AgentRunStatus;
import com.procureai.domain.enums.PurchaseOrderStatus;
import com.procureai.domain.enums.SupplierOfferStatus;
import com.procureai.domain.enums.SupplierStatus;
import com.procureai.service.dto.BaselineRequirementEvidence;
import com.procureai.service.dto.BudgetEvidence;
import com.procureai.service.dto.ConstraintEvaluation;
import com.procureai.service.dto.ConstraintStatus;
import com.procureai.service.dto.ForecastEvidence;
import com.procureai.service.dto.InventoryEvidence;
import com.procureai.service.dto.PlanningHorizon;
import com.procureai.service.dto.ProductEvidence;
import com.procureai.service.dto.PurchaseOrderEvidence;
import com.procureai.service.dto.PurchasingEvidenceSnapshot;
import com.procureai.service.dto.StorageEvidence;
import com.procureai.service.dto.SupplierEvidence;
import com.procureai.service.dto.SupplierOfferEvidence;
import com.procureai.service.dto.WarehouseEvidence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstraintValidationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 12, 0);
    private final ConstraintValidationService service = new ConstraintValidationService();

    @Test
    void fittingProposalPassesExplicitHardConstraintsAndRemainsUnchanged() {
        PurchasingEvidenceSnapshot snapshot = snapshot(offer(1L, SupplierOfferStatus.AVAILABLE,
                SupplierStatus.ACTIVE, 50L, 25L, "2.00", "INR"),
                new BigDecimal("10000.00"), "INR", new BigDecimal("20.000000"), true, true);

        ConstraintEvaluation evaluation = service.evaluate(snapshot, 7L, 100L);

        assertEquals(100L, evaluation.proposedUnits());
        assertEquals(7L, evaluation.selectedSupplierProductId());
        assertTrue(evaluation.results().stream().allMatch(result -> result.status() == ConstraintStatus.PASS));
    }

    @Test
    void mOQAndPackSizeFailuresDoNotRoundOrChangeProposedQuantity() {
        PurchasingEvidenceSnapshot snapshot = snapshot(offer(1L, SupplierOfferStatus.AVAILABLE,
                SupplierStatus.ACTIVE, 100L, 50L, "2.00", "INR"),
                new BigDecimal("10000.00"), "INR", new BigDecimal("20.000000"), true, true);

        ConstraintEvaluation evaluation = service.evaluate(snapshot, 7L, 75L);

        assertEquals(75L, evaluation.proposedUnits());
        assertEquals(ConstraintStatus.FAIL, status(evaluation, "supplier_moq"));
        assertEquals(ConstraintStatus.FAIL, status(evaluation, "supplier_pack_size"));
    }

    @Test
    void unavailableSupplierAndCapacityOrBudgetLimitFailuresAreIndependent() {
        SupplierOfferEvidence unavailable = offer(1L, SupplierOfferStatus.TEMPORARILY_UNAVAILABLE,
                SupplierStatus.ACTIVE, 50L, 25L, "2.00", "INR");
        PurchasingEvidenceSnapshot unavailableSnapshot = snapshot(unavailable,
                new BigDecimal("10000.00"), "INR", new BigDecimal("20.000000"), true, true);
        assertEquals(ConstraintStatus.FAIL,
                status(service.evaluate(unavailableSnapshot, 7L, 100L), "supplier_availability"));

        PurchasingEvidenceSnapshot lowBudget = snapshot(offer(1L, SupplierOfferStatus.AVAILABLE,
                SupplierStatus.ACTIVE, 50L, 25L, "2.00", "INR"),
                new BigDecimal("10.00"), "INR", new BigDecimal("20.000000"), true, true);
        assertEquals(ConstraintStatus.FAIL,
                status(service.evaluate(lowBudget, 7L, 100L), "purchasing_budget"));

        PurchasingEvidenceSnapshot lowStorage = snapshot(offer(1L, SupplierOfferStatus.AVAILABLE,
                SupplierStatus.ACTIVE, 50L, 25L, "2.00", "INR"),
                new BigDecimal("10000.00"), "INR", new BigDecimal("0.050000"), true, true);
        assertEquals(ConstraintStatus.FAIL,
                status(service.evaluate(lowStorage, 7L, 100L), "storage_capacity"));
    }

    @Test
    void missingStaleOrCurrencyMismatchedEvidenceNeedsInvestigation() {
        PurchasingEvidenceSnapshot missingInventory = snapshot(offer(1L, SupplierOfferStatus.AVAILABLE,
                SupplierStatus.ACTIVE, 50L, 25L, "2.00", "INR"),
                null, "INR", new BigDecimal("20.000000"), true, true);
        assertEquals(ConstraintStatus.NEEDS_INVESTIGATION,
                status(service.evaluate(missingInventory, 7L, 100L), "purchasing_budget"));

        PurchasingEvidenceSnapshot inventoryMissing = snapshotWithOffers(
                List.of(offer(1L, SupplierOfferStatus.AVAILABLE, SupplierStatus.ACTIVE,
                        50L, 25L, "2.00", "INR")), new BigDecimal("10000.00"), "INR",
                new BigDecimal("20.000000"), true, true, false);
        assertEquals(ConstraintStatus.NEEDS_INVESTIGATION,
                status(service.evaluate(inventoryMissing, 7L, 100L), "inventory_evidence"));

        PurchasingEvidenceSnapshot stale = snapshot(offer(1L, SupplierOfferStatus.AVAILABLE,
                SupplierStatus.ACTIVE, 50L, 25L, "2.00", "INR"),
                new BigDecimal("10000.00"), "INR", new BigDecimal("20.000000"), false, false);
        assertEquals(ConstraintStatus.NEEDS_INVESTIGATION,
                status(service.evaluate(stale, 7L, 100L), "inventory_evidence"));
        assertEquals(ConstraintStatus.NEEDS_INVESTIGATION,
                status(service.evaluate(stale, 7L, 100L), "forecast_evidence"));

        PurchasingEvidenceSnapshot missingSupplier = snapshot(null,
                new BigDecimal("10000.00"), "INR", new BigDecimal("20.000000"), true, true);
        assertEquals(ConstraintStatus.NEEDS_INVESTIGATION,
                status(service.evaluate(missingSupplier, 7L, 100L), "supplier_offer"));

        PurchasingEvidenceSnapshot wrongCurrency = snapshot(offer(1L, SupplierOfferStatus.AVAILABLE,
                SupplierStatus.ACTIVE, 50L, 25L, "2.00", "USD"),
                new BigDecimal("10000.00"), "INR", new BigDecimal("20.000000"), true, true);
        assertEquals(ConstraintStatus.NEEDS_INVESTIGATION,
                status(service.evaluate(wrongCurrency, 7L, 100L), "purchasing_budget"));
    }

    @Test
    void commercialPriceDifferenceDoesNotBecomeAnAutomaticConstraintFailure() {
        SupplierOfferEvidence moreExpensiveOffer = offer(1L, SupplierOfferStatus.AVAILABLE,
                SupplierStatus.ACTIVE, 50L, 25L, "99.00", "INR");
        SupplierOfferEvidence cheaperAlternative = offer(2L, SupplierOfferStatus.AVAILABLE,
                SupplierStatus.ACTIVE, 50L, 25L, "1.00", "INR");
        PurchasingEvidenceSnapshot snapshot = snapshotWithOffers(List.of(moreExpensiveOffer, cheaperAlternative),
                new BigDecimal("100000.00"), "INR", new BigDecimal("20.000000"), true, true);

        ConstraintEvaluation evaluation = service.evaluate(snapshot, 7L, 100L);

        assertEquals(ConstraintStatus.PASS, status(evaluation, "purchasing_budget"));
        assertEquals(100L, evaluation.proposedUnits());
    }

    private ConstraintStatus status(ConstraintEvaluation evaluation, String name) {
        return evaluation.results().stream().filter(result -> result.constraintName().equals(name))
                .findFirst().orElseThrow().status();
    }

    private PurchasingEvidenceSnapshot snapshot(SupplierOfferEvidence offer, BigDecimal availableBudget,
                                                 String budgetCurrency, BigDecimal availableStorage,
                                                 boolean inventoryFresh, boolean forecastFresh) {
        return snapshotWithOffers(offer == null ? List.of() : List.of(offer), availableBudget,
                budgetCurrency, availableStorage, inventoryFresh, forecastFresh, true);
    }

    private PurchasingEvidenceSnapshot snapshotWithOffers(List<SupplierOfferEvidence> offers,
                                                          BigDecimal availableBudget, String budgetCurrency,
                                                          BigDecimal availableStorage,
                                                          boolean inventoryFresh, boolean forecastFresh) {
        return snapshotWithOffers(offers, availableBudget, budgetCurrency, availableStorage,
                inventoryFresh, forecastFresh, true);
    }

    private PurchasingEvidenceSnapshot snapshotWithOffers(List<SupplierOfferEvidence> offers,
                                                          BigDecimal availableBudget, String budgetCurrency,
                                                          BigDecimal availableStorage,
                                                          boolean inventoryFresh, boolean forecastFresh,
                                                          boolean inventoryPresent) {
        PlanningHorizon horizon = new PlanningHorizon(LocalDate.of(2026, 11, 13),
                LocalDate.of(2026, 11, 20));
        InventoryEvidence inventory = !inventoryPresent ? null : new InventoryEvidence(
                "WM-001", "DEL-01", 500L, 0L, 500L, NOW, inventoryFresh, Duration.ofHours(24), List.of());
        ForecastEvidence forecast = new ForecastEvidence("WM-001", "DEL-01", UUID.randomUUID(), "m1", NOW,
                horizon, List.of(), List.of(), 0L, true, forecastFresh, Duration.ofDays(3),
                "LATEST_COMPLETE_RUN", forecastFresh ? List.of() : List.of("Forecast is stale."));
        PurchaseOrderEvidence purchaseOrders = new PurchaseOrderEvidence("WM-001", "DEL-01", horizon,
                List.of(), 0L, 0L, true, List.of());
        BudgetEvidence budget = availableBudget == null ? null : new BudgetEvidence("DEL-01",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), budgetCurrency,
                new BigDecimal("100000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                availableBudget, true, List.of(), List.of());
        StorageEvidence storage = new StorageEvidence("DEL-01", new BigDecimal("30.000000"),
                new BigDecimal("30.000000").subtract(availableStorage), availableStorage, NOW,
                true, true, Duration.ofDays(7), List.of());
        BaselineRequirementEvidence baseline = new BaselineRequirementEvidence(0L, 0L, 500L,
                0L, 0L, 0L, true, List.of());
        return new PurchasingEvidenceSnapshot(
                new ProductEvidence(1L, "WM-001", "Wireless Mouse", "EACH", new BigDecimal("0.001000"), true),
                new WarehouseEvidence(2L, "DEL-01", "Delhi FC", "Delhi", "IN", true),
                horizon, NOW, inventory, forecast, purchaseOrders,
                new SupplierEvidence("WM-001", "DEL-01", 100L, offers, List.of()),
                budget, storage, 0L, 7, baseline, List.of());
    }

    private SupplierOfferEvidence offer(Long supplierId, SupplierOfferStatus offerStatus,
                                        SupplierStatus supplierStatus, Long moq, Long pack,
                                        String price, String currency) {
        return new SupplierOfferEvidence(6L + supplierId, supplierId, "SUP-" + supplierId, "Supplier",
                supplierStatus, 1L, 2L, offerStatus, moq, pack, 3, new BigDecimal(price), currency,
                100L, 100L, 0L, List.of());
    }
}
