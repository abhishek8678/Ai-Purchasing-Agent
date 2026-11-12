package com.procureai.domain.entity;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;

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
class StorageCapacityEntityTest {

    @Autowired
    private TestEntityManager entityManager;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validCapacityPersistsWithWarehouse() {
        Warehouse warehouse = persistWarehouse("DEL-CAPACITY");
        StorageCapacity capacity = entityManager.persistAndFlush(capacity(
                warehouse, new BigDecimal("100.000000"), new BigDecimal("35.250000")));
        Long id = capacity.getId();

        entityManager.clear();
        StorageCapacity found = entityManager.find(StorageCapacity.class, id);
        assertNotNull(found);
        assertEquals(warehouse.getId(), found.getWarehouse().getId());
        assertEquals(new BigDecimal("100.000000"), found.getTotalVolumeM3());
        assertEquals(new BigDecimal("35.250000"), found.getAllocatedVolumeM3());
        assertNotNull(found.getMeasuredAt());
    }

    @Test
    void warehouseCanHaveOnlyOneCapacityRecordInDatabase() {
        Warehouse warehouse = persistWarehouse("DEL-DUP-CAPACITY");
        entityManager.persistAndFlush(capacity(warehouse, new BigDecimal("100"), new BigDecimal("30")));

        assertThrows(RuntimeException.class, () -> entityManager.persistAndFlush(
                capacity(warehouse, new BigDecimal("120"), new BigDecimal("40"))));
    }

    @Test
    void totalVolumeMustBePositiveAndAllocatedVolumeCannotBeNegative() {
        StorageCapacity capacity = validUnpersistedCapacity();
        capacity.setTotalVolumeM3(BigDecimal.ZERO);
        assertViolation(capacity, "totalVolumeM3");
        capacity.setTotalVolumeM3(BigDecimal.ONE);
        capacity.setAllocatedVolumeM3(new BigDecimal("-0.01"));
        assertViolation(capacity, "allocatedVolumeM3");
    }

    @Test
    void allocatedVolumeAboveCapacityIsPreservedForInvestigation() {
        Warehouse warehouse = persistWarehouse("DEL-OVER-CAPACITY");
        StorageCapacity capacity = entityManager.persistAndFlush(capacity(
                warehouse, new BigDecimal("10.000000"), new BigDecimal("12.500000")));
        Long id = capacity.getId();

        entityManager.clear();
        StorageCapacity found = entityManager.find(StorageCapacity.class, id);
        assertEquals(new BigDecimal("10.000000"), found.getTotalVolumeM3());
        assertEquals(new BigDecimal("12.500000"), found.getAllocatedVolumeM3());
    }

    @Test
    void optimisticLockVersionAdvancesWhenCapacityIsUpdated() {
        StorageCapacity capacity = entityManager.persistAndFlush(capacity(
                persistWarehouse("DEL-VERSION"), new BigDecimal("100"), new BigDecimal("30")));
        Long initialVersion = capacity.getVersion();

        capacity.setAllocatedVolumeM3(new BigDecimal("31"));
        entityManager.flush();

        assertTrue(capacity.getVersion() > initialVersion);
    }

    private void assertViolation(StorageCapacity capacity, String field) {
        Set<ConstraintViolation<StorageCapacity>> violations = validator.validate(capacity);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(field)),
                "Expected validation error for " + field);
    }

    private StorageCapacity validUnpersistedCapacity() {
        return capacity(persistWarehouse("DEL-VALID-CAPACITY"), BigDecimal.ONE, BigDecimal.ZERO);
    }

    private StorageCapacity capacity(Warehouse warehouse, BigDecimal total, BigDecimal allocated) {
        return new StorageCapacity(warehouse, total, allocated, LocalDateTime.now(Clock.systemUTC()));
    }

    private Warehouse persistWarehouse(String code) {
        return entityManager.persistAndFlush(new Warehouse(
                code, "Fulfillment Center", "Delhi", "IN", true));
    }
}
