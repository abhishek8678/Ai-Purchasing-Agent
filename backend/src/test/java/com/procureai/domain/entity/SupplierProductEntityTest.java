package com.procureai.domain.entity;

import java.math.BigDecimal;
import java.util.Set;

import com.procureai.domain.enums.SupplierOfferStatus;
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
class SupplierProductEntityTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validOfferPersistsWithSupplierProductWarehouseAndStringStatus() {
        Supplier supplier = persistSupplier();
        Product product = persistProduct();
        Warehouse warehouse = persistWarehouse("DEL-OFFER");
        SupplierProduct offer = entityManager.persistAndFlush(offer(
                supplier, product, warehouse, 20L, 5L, 7, new BigDecimal("123.4500"), "INR"));
        Long id = offer.getId();

        entityManager.clear();
        SupplierProduct found = entityManager.find(SupplierProduct.class, id);
        assertNotNull(found);
        assertEquals(supplier.getId(), found.getSupplier().getId());
        assertEquals(product.getId(), found.getProduct().getId());
        assertEquals(warehouse.getId(), found.getWarehouse().getId());
        assertEquals(new BigDecimal("123.4500"), found.getUnitPrice());
        assertEquals("AVAILABLE", jdbcTemplate.queryForObject(
                "select status from supplier_products where id = ?", String.class, id));
        assertNotNull(found.getCreatedAt());
        assertNotNull(found.getUpdatedAt());
    }

    @Test
    void supplierProductWarehouseCombinationMustBeUniqueInDatabase() {
        Supplier supplier = persistSupplier();
        Product product = persistProduct();
        Warehouse warehouse = persistWarehouse("DEL-DUP-OFFER");
        entityManager.persistAndFlush(offer(supplier, product, warehouse, 1L, 1L, 0,
                BigDecimal.ONE, "INR"));

        assertThrows(RuntimeException.class, () -> entityManager.persistAndFlush(
                offer(supplier, product, warehouse, 2L, 1L, 1, BigDecimal.TEN, "INR")));
    }

    @Test
    void sameSupplierAndProductCanHaveOffersForDifferentWarehouses() {
        Supplier supplier = persistSupplier();
        Product product = persistProduct();
        SupplierProduct first = entityManager.persistAndFlush(offer(
                supplier, product, persistWarehouse("DEL-MULTI"), 1L, 1L, 0, BigDecimal.ONE, "INR"));
        SupplierProduct second = entityManager.persistAndFlush(offer(
                supplier, product, persistWarehouse("MUM-MULTI"), 1L, 1L, 0, BigDecimal.ONE, "INR"));

        assertNotNull(first.getId());
        assertNotNull(second.getId());
    }

    @Test
    void minimumOrderQuantityAndPackSizeMustBePositive() {
        SupplierProduct offer = validUnpersistedOffer();
        offer.setMinimumOrderQuantity(0L);
        assertViolation(offer, "minimumOrderQuantity");
        offer.setMinimumOrderQuantity(1L);
        offer.setPackSize(0L);
        assertViolation(offer, "packSize");
    }

    @Test
    void leadTimeAndUnitPriceCannotBeNegative() {
        SupplierProduct offer = validUnpersistedOffer();
        offer.setLeadTimeDays(-1);
        assertViolation(offer, "leadTimeDays");
        offer.setLeadTimeDays(0);
        offer.setUnitPrice(new BigDecimal("-0.01"));
        assertViolation(offer, "unitPrice");
    }

    @Test
    void currencyCodeMustContainExactlyThreeNonBlankCharacters() {
        SupplierProduct offer = validUnpersistedOffer();
        offer.setCurrencyCode("IN");
        assertViolation(offer, "currencyCode");
        offer.setCurrencyCode("    ");
        assertViolation(offer, "currencyCode");
    }

    private void assertViolation(SupplierProduct offer, String field) {
        Set<ConstraintViolation<SupplierProduct>> violations = validator.validate(offer);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(field)),
                "Expected validation error for " + field);
    }

    private SupplierProduct validUnpersistedOffer() {
        return offer(persistSupplier(), persistProduct(), persistWarehouse("DEL-VALID"),
                1L, 1L, 0, BigDecimal.ONE, "INR");
    }

    private SupplierProduct offer(
            Supplier supplier, Product product, Warehouse warehouse, Long moq, Long pack,
            Integer leadTime, BigDecimal price, String currency
    ) {
        return new SupplierProduct(supplier, product, warehouse, "ACME-MOUSE", moq, pack,
                leadTime, price, currency, SupplierOfferStatus.AVAILABLE);
    }

    private Supplier persistSupplier() {
        return entityManager.persistAndFlush(new Supplier("SUP-OFFER", "Acme Supplies", SupplierStatus.ACTIVE));
    }

    private Product persistProduct() {
        return entityManager.persistAndFlush(new Product(
                "MOUSE-OFFER", "Wireless Mouse", null, "EACH", null, true));
    }

    private Warehouse persistWarehouse(String code) {
        return entityManager.persistAndFlush(new Warehouse(
                code, "Fulfillment Center", "Delhi", "IN", true));
    }
}
