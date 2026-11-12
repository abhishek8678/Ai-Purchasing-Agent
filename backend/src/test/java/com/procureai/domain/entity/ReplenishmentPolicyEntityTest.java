package com.procureai.domain.entity;

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
class ReplenishmentPolicyEntityTest {

    @Autowired
    private TestEntityManager entityManager;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validPolicyPersistsWithProductAndWarehouse() {
        Product product = persistProduct();
        Warehouse warehouse = persistWarehouse();
        ReplenishmentPolicy policy = entityManager.persistAndFlush(
                new ReplenishmentPolicy(product, warehouse, 10L, 14));

        assertNotNull(policy.getId());
        assertEquals(product.getId(), policy.getProduct().getId());
        assertEquals(warehouse.getId(), policy.getWarehouse().getId());
        assertEquals(10L, policy.getSafetyStockUnits());
        assertEquals(14, policy.getReviewPeriodDays());
        assertNotNull(policy.getCreatedAt());
        assertNotNull(policy.getUpdatedAt());
    }

    @Test
    void productAndWarehousePairMustBeUnique() {
        Product product = persistProduct();
        Warehouse warehouse = persistWarehouse();
        entityManager.persistAndFlush(new ReplenishmentPolicy(product, warehouse, 10L, 14));

        assertThrows(RuntimeException.class, () -> entityManager.persistAndFlush(
                new ReplenishmentPolicy(product, warehouse, 15L, 21)));
    }

    @Test
    void negativeSafetyStockAndNonPositiveReviewPeriodsAreRejected() {
        Product product = persistProduct();
        Warehouse warehouse = persistWarehouse();
        ReplenishmentPolicy policy = new ReplenishmentPolicy(product, warehouse, -1L, 0);

        Set<ConstraintViolation<ReplenishmentPolicy>> violations = validator.validate(policy);

        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("safetyStockUnits")));
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("reviewPeriodDays")));

        policy.setSafetyStockUnits(1L);
        policy.setReviewPeriodDays(-2);
        assertTrue(validator.validate(policy).stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("reviewPeriodDays")));
    }

    private Product persistProduct() {
        return entityManager.persistAndFlush(
                new Product("MOUSE-RP", "Wireless Mouse", null, "EACH", null, true));
    }

    private Warehouse persistWarehouse() {
        return entityManager.persistAndFlush(
                new Warehouse("DEL-RP", "Delhi Fulfillment Center", "Delhi", "IN", true));
    }
}
