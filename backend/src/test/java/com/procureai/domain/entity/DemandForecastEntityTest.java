package com.procureai.domain.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class DemandForecastEntityTest {

    @Autowired
    private TestEntityManager entityManager;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void dailyForecastPersistsWithItsProductWarehouseAndRunId() {
        Product product = persistProduct();
        Warehouse warehouse = persistWarehouse();
        UUID runId = UUID.randomUUID();
        DemandForecast forecast = entityManager.persistAndFlush(
                forecast(product, warehouse, runId, 24L));
        Long forecastId = forecast.getId();

        entityManager.clear();
        DemandForecast found = entityManager.find(DemandForecast.class, forecastId);

        assertNotNull(found);
        assertEquals(product.getId(), found.getProduct().getId());
        assertEquals(warehouse.getId(), found.getWarehouse().getId());
        assertEquals(runId, found.getForecastRunId());
        assertEquals(LocalDate.of(2026, 12, 1), found.getForecastDate());
        assertEquals(24L, found.getExpectedUnits());
    }

    @Test
    void separateForecastGenerationsForSameDateArePreserved() {
        Product product = persistProduct();
        Warehouse warehouse = persistWarehouse();
        LocalDate date = LocalDate.of(2026, 12, 1);

        DemandForecast first = entityManager.persistAndFlush(forecast(
                product, warehouse, UUID.randomUUID(), 24L));
        DemandForecast second = entityManager.persistAndFlush(forecast(
                product, warehouse, UUID.randomUUID(), 31L));

        assertNotNull(first.getId());
        assertNotNull(second.getId());
        assertEquals(date, first.getForecastDate());
        assertEquals(date, second.getForecastDate());
    }

    @Test
    void duplicateRowWithinSameForecastRunAndDateIsRejected() {
        Product product = persistProduct();
        Warehouse warehouse = persistWarehouse();
        UUID runId = UUID.randomUUID();
        entityManager.persistAndFlush(forecast(product, warehouse, runId, 24L));

        assertThrows(RuntimeException.class,
                () -> entityManager.persistAndFlush(forecast(product, warehouse, runId, 31L)));
    }

    @Test
    void negativeExpectedUnitsAreRejected() {
        Product product = persistProduct();
        Warehouse warehouse = persistWarehouse();
        DemandForecast forecast = forecast(product, warehouse, UUID.randomUUID(), -1L);

        Set<ConstraintViolation<DemandForecast>> violations = validator.validate(forecast);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("expectedUnits")));
    }

    private Product persistProduct() {
        return entityManager.persistAndFlush(
                new Product("MOUSE-FC", "Wireless Mouse", null, "EACH", null, true));
    }

    private Warehouse persistWarehouse() {
        return entityManager.persistAndFlush(
                new Warehouse("DEL-FC", "Delhi Fulfillment Center", "Delhi", "IN", true));
    }

    private DemandForecast forecast(Product product, Warehouse warehouse, UUID runId, Long units) {
        return new DemandForecast(
                product,
                warehouse,
                LocalDate.of(2026, 12, 1),
                units,
                runId,
                LocalDateTime.of(2026, 11, 30, 12, 0),
                "model-v1"
        );
    }
}
