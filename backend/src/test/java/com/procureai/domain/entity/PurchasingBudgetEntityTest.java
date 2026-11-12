package com.procureai.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
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
class PurchasingBudgetEntityTest {

    @Autowired
    private TestEntityManager entityManager;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validBudgetPersistsWithWarehouseAndExactBigDecimalAmount() {
        Warehouse warehouse = persistWarehouse("DEL-BUDGET");
        PurchasingBudget budget = entityManager.persistAndFlush(budget(warehouse,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), new BigDecimal("123456.7890"), "INR"));
        Long id = budget.getId();

        entityManager.clear();
        PurchasingBudget found = entityManager.find(PurchasingBudget.class, id);
        assertNotNull(found);
        assertEquals(warehouse.getId(), found.getWarehouse().getId());
        assertEquals(new BigDecimal("123456.7890"), found.getBudgetAmount());
        assertEquals("INR", found.getCurrencyCode());
        assertNotNull(found.getCreatedAt());
        assertNotNull(found.getUpdatedAt());
    }

    @Test
    void warehouseAndPeriodCombinationMustBeUniqueInDatabase() {
        Warehouse warehouse = persistWarehouse("DEL-DUP-BUDGET");
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);
        entityManager.persistAndFlush(budget(warehouse, start, end, BigDecimal.TEN, "INR"));

        assertThrows(RuntimeException.class, () -> entityManager.persistAndFlush(
                budget(warehouse, start, end, BigDecimal.ONE, "INR")));
    }

    @Test
    void negativeBudgetAmountIsRejected() {
        PurchasingBudget budget = validUnpersistedBudget();
        budget.setBudgetAmount(new BigDecimal("-0.01"));
        assertViolation(budget, "budgetAmount");
    }

    @Test
    void periodEndCannotBeBeforePeriodStart() {
        PurchasingBudget budget = validUnpersistedBudget();
        budget.setPeriodStart(LocalDate.of(2026, 2, 1));
        budget.setPeriodEnd(LocalDate.of(2026, 1, 31));
        assertViolation(budget, "periodRangeValid");
    }

    @Test
    void currencyCodeMustContainExactlyThreeNonBlankCharacters() {
        PurchasingBudget budget = validUnpersistedBudget();
        budget.setCurrencyCode("US");
        assertViolation(budget, "currencyCode");
        budget.setCurrencyCode("   ");
        assertViolation(budget, "currencyCode");
    }

    private void assertViolation(PurchasingBudget budget, String field) {
        Set<ConstraintViolation<PurchasingBudget>> violations = validator.validate(budget);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(field)),
                "Expected validation error for " + field);
    }

    private PurchasingBudget validUnpersistedBudget() {
        return budget(persistWarehouse("DEL-VALID-BUDGET"), LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31), BigDecimal.TEN, "INR");
    }

    private PurchasingBudget budget(
            Warehouse warehouse, LocalDate start, LocalDate end, BigDecimal amount, String currency
    ) {
        return new PurchasingBudget(warehouse, start, end, amount, currency);
    }

    private Warehouse persistWarehouse(String code) {
        return entityManager.persistAndFlush(new Warehouse(
                code, "Fulfillment Center", "Delhi", "IN", true));
    }
}
