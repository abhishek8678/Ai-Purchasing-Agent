package com.procureai.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

import com.procureai.config.InvestigationPolicyProperties;
import com.procureai.domain.entity.DemandForecast;
import com.procureai.domain.entity.Inventory;
import com.procureai.domain.entity.Product;
import com.procureai.domain.entity.Warehouse;
import com.procureai.repository.DemandForecastRepository;
import com.procureai.repository.InventoryRepository;
import com.procureai.service.dto.ForecastEvidence;
import com.procureai.service.dto.InventoryEvidence;
import com.procureai.service.dto.PlanningHorizon;
import com.procureai.service.exception.InvestigationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryAndForecastInvestigationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 12, 0);

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private DemandForecastRepository forecastRepository;

    private final Product product = new Product("WM-001", "Wireless Mouse", null, "EACH", null, true);
    private final Warehouse warehouse = new Warehouse("DEL-01", "Delhi FC", "Delhi", "IN", true);
    private final InvestigationPolicyProperties policy = new InvestigationPolicyProperties();
    private final Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Test
    void inventoryAvailableUnitsAreSubtractedWithoutClamping() {
        InventoryInvestigationService service = new InventoryInvestigationService(inventoryRepository, policy, clock);
        when(inventoryRepository.findByProductAndWarehouse(product, warehouse)).thenReturn(Optional.of(
                new Inventory(product, warehouse, 25L, 8L, NOW.minusHours(1))));

        InventoryEvidence evidence = service.investigate(product, warehouse);

        assertEquals(17L, evidence.availableUnits());
        assertTrue(evidence.fresh());
        assertTrue(evidence.warnings().isEmpty());
    }

    @Test
    void reservedExceedingOnHandProducesNegativeAvailableUnitsAndWarning() {
        InventoryInvestigationService service = new InventoryInvestigationService(inventoryRepository, policy, clock);
        when(inventoryRepository.findByProductAndWarehouse(product, warehouse)).thenReturn(Optional.of(
                new Inventory(product, warehouse, 3L, 8L, NOW.minusHours(1))));

        InventoryEvidence evidence = service.investigate(product, warehouse);

        assertEquals(-5L, evidence.availableUnits());
        assertTrue(evidence.warnings().stream().anyMatch(warning -> warning.contains("exceeds on-hand")));
    }

    @Test
    void missingOrInvalidInventoryIsNotReturnedAsZero() {
        InventoryInvestigationService service = new InventoryInvestigationService(inventoryRepository, policy, clock);
        when(inventoryRepository.findByProductAndWarehouse(product, warehouse)).thenReturn(Optional.empty());
        assertEquals("MISSING_INVENTORY", assertThrows(InvestigationException.class,
                () -> service.investigate(product, warehouse)).getCode());

        when(inventoryRepository.findByProductAndWarehouse(product, warehouse)).thenReturn(Optional.of(
                new Inventory(product, warehouse, -1L, 0L, NOW)));
        assertEquals("INVALID_INVENTORY", assertThrows(InvestigationException.class,
                () -> service.investigate(product, warehouse)).getCode());
    }

    @Test
    void forecastSelectionUsesOneLatestCompleteRunAndNeverCombinesGenerations() {
        DemandForecastInvestigationService service = new DemandForecastInvestigationService(
                forecastRepository, policy, clock);
        LocalDate first = LocalDate.of(2026, 11, 13);
        LocalDate second = first.plusDays(1);
        PlanningHorizon horizon = new PlanningHorizon(first, second);
        UUID completeRun = UUID.randomUUID();
        UUID newerIncompleteRun = UUID.randomUUID();
        when(forecastRepository.findByProductAndWarehouse(product, warehouse)).thenReturn(List.of(
                forecast(first, 10L, completeRun, NOW.minusHours(20), "model-old"),
                forecast(second, 15L, completeRun, NOW.minusHours(20), "model-old"),
                forecast(first, 500L, newerIncompleteRun, NOW.minusHours(1), "model-new")));

        ForecastEvidence evidence = service.investigate(product, warehouse, horizon);

        assertEquals(completeRun, evidence.forecastRunId());
        assertEquals("model-old", evidence.modelVersion());
        assertEquals(25L, evidence.observedDemandUnits());
        assertTrue(evidence.complete());
        assertTrue(evidence.fresh());
        assertTrue(evidence.missingDates().isEmpty());
        assertTrue(evidence.selectionPolicy().contains("LATEST_COMPLETE_RUN"));
    }

    @Test
    void incompleteStaleForecastReportsMissingDatesAndFreshnessPolicy() {
        DemandForecastInvestigationService service = new DemandForecastInvestigationService(
                forecastRepository, policy, clock);
        LocalDate first = LocalDate.of(2026, 11, 13);
        PlanningHorizon horizon = new PlanningHorizon(first, first.plusDays(1));
        UUID run = UUID.randomUUID();
        when(forecastRepository.findByProductAndWarehouse(product, warehouse)).thenReturn(List.of(
                forecast(first, 10L, run, NOW.minusDays(5), "model-v1")));

        ForecastEvidence evidence = service.investigate(product, warehouse, horizon);

        assertFalse(evidence.complete());
        assertFalse(evidence.fresh());
        assertEquals(List.of(first.plusDays(1)), evidence.missingDates());
        assertEquals("model-v1", evidence.modelVersion());
        assertTrue(evidence.warnings().stream().anyMatch(warning -> warning.contains("No complete forecast")));
    }

    private DemandForecast forecast(LocalDate date, long units, UUID runId, LocalDateTime generatedAt,
                                    String modelVersion) {
        return new DemandForecast(product, warehouse, date, units, runId, generatedAt, modelVersion);
    }
}
