package com.procureai.domain.entity;

import java.util.Set;

import com.procureai.domain.enums.SupplierStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class SupplierEntityTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validSupplierCanBePersistedAndStatusIsStoredAsText() {
        Supplier supplier = new Supplier("SUP-01", "Acme Supplies", SupplierStatus.ACTIVE);
        entityManager.persistAndFlush(supplier);

        assertNotNull(supplier.getId());
        assertNotNull(supplier.getCreatedAt());
        assertNotNull(supplier.getUpdatedAt());
        assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                "select status from suppliers where code = ?", String.class, "SUP-01"));
    }

    @Test
    void supplierCodeMustBeUnique() {
        entityManager.persistAndFlush(new Supplier("SUP-01", "Acme Supplies", SupplierStatus.ACTIVE));

        assertThrows(RuntimeException.class, () -> entityManager.persistAndFlush(
                new Supplier("SUP-01", "Duplicate Supplier", SupplierStatus.INACTIVE)));
    }

    @Test
    void requiredFieldsAreValidated() {
        Supplier supplier = new Supplier("SUP-02", "Acme Supplies", SupplierStatus.ACTIVE);
        supplier.setName(" ");
        supplier.setStatus(null);

        Set<ConstraintViolation<Supplier>> violations = validator.validate(supplier);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("name")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("status")));
    }
}
