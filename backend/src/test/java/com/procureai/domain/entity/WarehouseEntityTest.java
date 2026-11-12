package com.procureai.domain.entity;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class WarehouseEntityTest {

    @Autowired
    private TestEntityManager entityManager;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validWarehouseCanBePersisted() {
        Warehouse warehouse = warehouse("DEL-01");

        Warehouse saved = entityManager.persistAndFlush(warehouse);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void warehouseCodeMustBeUnique() {
        entityManager.persistAndFlush(warehouse("DEL-01"));

        Warehouse duplicate = warehouse("DEL-01");
        assertThrows(RuntimeException.class, () -> entityManager.persistAndFlush(duplicate));
    }

    @Test
    void requiredFieldsAreValidated() {
        Warehouse warehouse = warehouse("DEL-02");
        warehouse.setName("");
        warehouse.setCity(null);

        Set<ConstraintViolation<Warehouse>> violations = validator.validate(warehouse);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("name")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("city")));
    }

    private Warehouse warehouse(String code) {
        return new Warehouse(code, "Delhi Fulfillment Center", "Delhi", "IN", true);
    }
}
