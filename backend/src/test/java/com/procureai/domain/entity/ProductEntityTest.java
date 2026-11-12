package com.procureai.domain.entity;

import java.math.BigDecimal;
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
class ProductEntityTest {

    @Autowired
    private TestEntityManager entityManager;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validProductCanBePersisted() {
        Product product = new Product(
                "MOUSE-001", "Wireless Mouse", null, "EACH", new BigDecimal("0.001250"), true);

        Product saved = entityManager.persistAndFlush(product);

        assertNotNull(saved.getId());
        assertEquals("MOUSE-001", saved.getSku());
        assertEquals("EACH", saved.getUnitOfMeasure());
        assertEquals(new BigDecimal("0.001250"), saved.getUnitVolumeM3());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void skuMustBeUnique() {
        entityManager.persistAndFlush(product("MOUSE-001"));

        Product duplicate = product("MOUSE-001");
        assertThrows(RuntimeException.class, () -> entityManager.persistAndFlush(duplicate));
    }

    @Test
    void requiredFieldsAreValidated() {
        Product product = product("MOUSE-002");
        product.setName(" ");
        product.setUnitOfMeasure(null);

        Set<ConstraintViolation<Product>> violations = validator.validate(product);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("name")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("unitOfMeasure")));
    }

    @Test
    void zeroAndNegativeUnitVolumeAreRejectedWhenProvided() {
        Product zeroVolume = product("MOUSE-003");
        zeroVolume.setUnitVolumeM3(BigDecimal.ZERO);
        Product negativeVolume = product("MOUSE-004");
        negativeVolume.setUnitVolumeM3(new BigDecimal("-0.1"));

        assertTrue(validator.validate(zeroVolume).stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("unitVolumeM3")));
        assertTrue(validator.validate(negativeVolume).stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("unitVolumeM3")));
    }

    private Product product(String sku) {
        return new Product(sku, "Wireless Mouse", null, "EACH", null, true);
    }
}
