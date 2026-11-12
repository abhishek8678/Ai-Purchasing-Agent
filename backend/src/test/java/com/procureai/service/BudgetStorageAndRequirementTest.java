package com.procureai.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.procureai.config.InvestigationPolicyProperties;
import com.procureai.domain.entity.PurchaseOrder;
import com.procureai.domain.entity.PurchaseOrderItem;
import com.procureai.domain.entity.PurchasingBudget;
import com.procureai.domain.entity.StorageCapacity;
import com.procureai.domain.entity.Warehouse;
import com.procureai.domain.enums.PurchaseOrderStatus;
import com.procureai.repository.PurchaseOrderItemRepository;
import com.procureai.repository.PurchaseOrderRepository;
import com.procureai.repository.PurchasingBudgetRepository;
import com.procureai.repository.StorageCapacityRepository;
import com.procureai.service.dto.BaselineRequirementEvidence;
import com.procureai.service.dto.DailyForecastEvidence;
import com.procureai.service.dto.ForecastEvidence;
import com.procureai.service.dto.InventoryEvidence;
import com.procureai.service.dto.PlanningHorizon;
import com.procureai.service.dto.PurchaseOrderEvidence;
import com.procureai.service.dto.StorageEvidence;
import com.procureai.service.exception.InvestigationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetStorageAndRequirementTest {

    private static final LocalDate PERIOD_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 12, 31);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 12, 0);

    @Mock
    private PurchasingBudgetRepository budgetRepository;
    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;
    @Mock
    private PurchaseOrderItemRepository itemRepository;
    @Mock
    private StorageCapacityRepository storageRepository;

    private final Warehouse warehouse = new Warehouse("DEL-01", "Delhi FC", "Delhi", "IN", true);
    private final Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Test
    void budgetUsesReceiptsAsSpentAndOnlyEligibleUnreceivedUnitsAsCommitted() {
        PurchasingBudget budget = new PurchasingBudget(warehouse, PERIOD_START, PERIOD_END,
                new BigDecimal("1000.0000"), "INR");
        when(budgetRepository.findByWarehouse(warehouse)).thenReturn(List.of(budget));
        PurchaseOrder acknowledged = mockOrder(1L, "INR", PurchaseOrderStatus.ACKNOWLEDGED);
        PurchaseOrder cancelled = mockOrder(2L, "INR", PurchaseOrderStatus.CANCELLED);
        PurchaseOrder submitted = mockOrder(3L, "INR", PurchaseOrderStatus.SUBMITTED);
        when(purchaseOrderRepository.findByWarehouseAndOrderDateBetween(warehouse, PERIOD_START, PERIOD_END))
                .thenReturn(List.of(acknowledged, cancelled, submitted));
        List<PurchaseOrderItem> items = List.of(
                mockItem(acknowledged, 10L, 4L, "2.0000"),
                mockItem(cancelled, 5L, 2L, "3.0000"),
                mockItem(submitted, 3L, 0L, "5.0000"));
        when(itemRepository.findByPurchaseOrderIn(any())).thenReturn(items);

        var evidence = new BudgetInvestigationService(budgetRepository, purchaseOrderRepository, itemRepository)
                .investigate(warehouse, LocalDate.of(2026, 6, 30));

        assertEquals(new BigDecimal("14.0000"), evidence.spentAmount());
        assertEquals(new BigDecimal("27.0000"), evidence.committedAmount());
        assertEquals(new BigDecimal("959.0000"), evidence.availableAmount());
        assertTrue(evidence.complete());
        verify(purchaseOrderRepository).findByWarehouseAndOrderDateBetween(
                warehouse, PERIOD_START, PERIOD_END);
    }

    @Test
    void currencyMismatchIsReportedAndNeverMixedIntoBudgetTotals() {
        PurchasingBudget budget = new PurchasingBudget(warehouse, PERIOD_START, PERIOD_END,
                new BigDecimal("1000.00"), "INR");
        PurchaseOrder usdOrder = mock(PurchaseOrder.class);
        when(usdOrder.getPoNumber()).thenReturn("USD-PO");
        when(usdOrder.getCurrencyCode()).thenReturn("USD");
        when(budgetRepository.findByWarehouse(warehouse)).thenReturn(List.of(budget));
        when(purchaseOrderRepository.findByWarehouseAndOrderDateBetween(warehouse, PERIOD_START, PERIOD_END))
                .thenReturn(List.of(usdOrder));
        when(itemRepository.findByPurchaseOrderIn(any())).thenReturn(List.of());

        var evidence = new BudgetInvestigationService(budgetRepository, purchaseOrderRepository, itemRepository)
                .investigate(warehouse, LocalDate.of(2026, 6, 30));

        assertFalse(evidence.complete());
        assertEquals(List.of("USD-PO: USD"), evidence.currencyMismatches());
        assertEquals(BigDecimal.ZERO, evidence.spentAmount());
        assertEquals(BigDecimal.ZERO, evidence.committedAmount());
        assertEquals(new BigDecimal("1000.00"), evidence.availableAmount());
    }

    @Test
    void missingBudgetAndOverlappingBudgetPeriodsAreExplicitErrors() {
        when(budgetRepository.findByWarehouse(warehouse)).thenReturn(List.of());
        BudgetInvestigationService service = new BudgetInvestigationService(
                budgetRepository, purchaseOrderRepository, itemRepository);
        assertEquals("MISSING_BUDGET", assertThrows(InvestigationException.class,
                () -> service.investigate(warehouse, LocalDate.of(2026, 6, 30))).getCode());

        PurchasingBudget first = new PurchasingBudget(warehouse, PERIOD_START, PERIOD_END,
                BigDecimal.TEN, "INR");
        PurchasingBudget second = new PurchasingBudget(warehouse, LocalDate.of(2026, 6, 1), PERIOD_END,
                BigDecimal.TEN, "INR");
        when(budgetRepository.findByWarehouse(warehouse)).thenReturn(List.of(first, second));
        assertEquals("OVERLAPPING_BUDGETS", assertThrows(InvestigationException.class,
                () -> service.investigate(warehouse, LocalDate.of(2026, 6, 30))).getCode());
    }

    @Test
    void storageExposesNegativeAvailableVolumeForOverCapacityAndFlagsStaleData() {
        InvestigationPolicyProperties policy = new InvestigationPolicyProperties();
        StorageCapacity capacity = new StorageCapacity(warehouse, new BigDecimal("10.000000"),
                new BigDecimal("12.000000"), NOW.minusDays(8));
        when(storageRepository.findByWarehouse(warehouse)).thenReturn(Optional.of(capacity));

        StorageEvidence evidence = new StorageInvestigationService(storageRepository, policy, clock)
                .investigate(warehouse);

        assertEquals(new BigDecimal("-2.000000"), evidence.availableVolumeM3());
        assertFalse(evidence.consistent());
        assertFalse(evidence.fresh());
        assertTrue(evidence.warnings().stream().anyMatch(warning -> warning.contains("exceeds total capacity")));
    }

    @Test
    void missingStorageCapacityIsAnExplicitError() {
        when(storageRepository.findByWarehouse(warehouse)).thenReturn(Optional.empty());
        assertEquals("MISSING_STORAGE_CAPACITY", assertThrows(InvestigationException.class,
                () -> new StorageInvestigationService(storageRepository,
                        new InvestigationPolicyProperties(), clock).investigate(warehouse)).getCode());
    }

    @Test
    void baselineRequirementIsTransparentAndDoesNotSubtractUncertainSupply() {
        PlanningHorizon horizon = new PlanningHorizon(LocalDate.of(2026, 11, 13),
                LocalDate.of(2026, 11, 13));
        ForecastEvidence forecast = new ForecastEvidence("WM-001", "DEL-01", UUID.randomUUID(), "m1",
                NOW, horizon, List.of(new DailyForecastEvidence(horizon.startDate(), 100L)), List.of(),
                100L, true, true, Duration.ofDays(3), "LATEST_COMPLETE_RUN", List.of());
        InventoryEvidence inventory = new InventoryEvidence("WM-001", "DEL-01", 60L, 10L, 50L,
                NOW, true, Duration.ofHours(24), List.of());
        PurchaseOrderEvidence purchaseOrders = new PurchaseOrderEvidence("WM-001", "DEL-01", horizon,
                List.of(), 30L, 8L, true, List.of());

        BaselineRequirementEvidence result = new PurchaseRequirementService()
                .calculate(inventory, forecast, 20L, purchaseOrders);

        assertEquals(100L, result.grossDemandUnits());
        assertEquals(20L, result.safetyStockUnits());
        assertEquals(50L, result.availableStockUnits());
        assertEquals(30L, result.confirmedIncomingUnits());
        assertEquals(8L, result.uncertainUnconfirmedSupplyUnits());
        assertEquals(40L, result.baselineNetRequirementUnits());
        assertTrue(result.calculable());
    }

    @Test
    void zeroBaselineAndMissingForecastRemainExplicit() {
        PlanningHorizon horizon = new PlanningHorizon(LocalDate.of(2026, 11, 13),
                LocalDate.of(2026, 11, 13));
        ForecastEvidence forecast = new ForecastEvidence("WM-001", "DEL-01", UUID.randomUUID(), "m1",
                NOW, horizon, List.of(new DailyForecastEvidence(horizon.startDate(), 10L)), List.of(),
                10L, true, true, Duration.ofDays(3), "LATEST_COMPLETE_RUN", List.of());
        InventoryEvidence inventory = new InventoryEvidence("WM-001", "DEL-01", 100L, 0L, 100L,
                NOW, true, Duration.ofHours(24), List.of());
        PurchaseOrderEvidence poEvidence = new PurchaseOrderEvidence("WM-001", "DEL-01", horizon,
                List.of(), 20L, 5L, true, List.of());
        PurchaseRequirementService service = new PurchaseRequirementService();

        BaselineRequirementEvidence zero = service.calculate(inventory, forecast, 5L, poEvidence);
        BaselineRequirementEvidence missingForecast = service.calculate(inventory, null, 5L, poEvidence);

        assertEquals(0L, zero.baselineNetRequirementUnits());
        assertTrue(zero.calculable());
        assertFalse(missingForecast.calculable());
        assertEquals(null, missingForecast.baselineNetRequirementUnits());
        assertTrue(missingForecast.warnings().stream().anyMatch(warning -> warning.contains("Forecast evidence is missing")));
    }

    private PurchaseOrder mockOrder(long id, String currency, PurchaseOrderStatus status) {
        PurchaseOrder order = mock(PurchaseOrder.class);
        when(order.getId()).thenReturn(id);
        when(order.getCurrencyCode()).thenReturn(currency);
        when(order.getStatus()).thenReturn(status);
        return order;
    }

    private PurchaseOrderItem mockItem(PurchaseOrder order, long ordered, long received, String unitPrice) {
        PurchaseOrderItem item = mock(PurchaseOrderItem.class);
        when(item.getPurchaseOrder()).thenReturn(order);
        when(item.getOrderedUnits()).thenReturn(ordered);
        when(item.getReceivedUnits()).thenReturn(received);
        when(item.getUnitPriceAtOrder()).thenReturn(new BigDecimal(unitPrice));
        return item;
    }
}
