package com.procureai.domain.entity;

import java.time.LocalDate;
import java.util.Set;

import com.procureai.domain.enums.PurchaseOrderStatus;
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
class PurchaseOrderEntityTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validPurchaseOrderPersistsWithSupplierWarehouseAndStringStatus() {
        Supplier supplier = persistSupplier();
        Warehouse warehouse = persistWarehouse("DEL-PO-REL");
        PurchaseOrder order = entityManager.persistAndFlush(purchaseOrder(
                "PO-REL-001", supplier, warehouse, "request-001"));
        Long id = order.getId();

        entityManager.clear();
        PurchaseOrder found = entityManager.find(PurchaseOrder.class, id);
        assertNotNull(found);
        assertEquals(supplier.getId(), found.getSupplier().getId());
        assertEquals(warehouse.getId(), found.getWarehouse().getId());
        assertEquals(PurchaseOrderStatus.DRAFT, found.getStatus());
        assertEquals("DRAFT", jdbcTemplate.queryForObject(
                "select status from purchase_orders where id = ?", String.class, id));
        assertNotNull(found.getCreatedAt());
        assertNotNull(found.getUpdatedAt());
    }

    @Test
    void poNumberMustBeUniqueInDatabase() {
        Supplier supplier = persistSupplier();
        Warehouse warehouse = persistWarehouse("DEL-PO-DUP-NUM");
        entityManager.persistAndFlush(purchaseOrder("PO-DUP-001", supplier, warehouse, null));

        assertThrows(RuntimeException.class, () -> entityManager.persistAndFlush(
                purchaseOrder("PO-DUP-001", supplier, warehouse, null)));
    }

    @Test
    void idempotencyKeyMustBeUniqueWhenPresent() {
        Supplier supplier = persistSupplier();
        Warehouse warehouse = persistWarehouse("DEL-PO-DUP-KEY");
        entityManager.persistAndFlush(purchaseOrder("PO-KEY-001", supplier, warehouse, "same-request"));

        assertThrows(RuntimeException.class, () -> entityManager.persistAndFlush(
                purchaseOrder("PO-KEY-002", supplier, warehouse, "same-request")));
    }

    @Test
    void multiplePurchaseOrdersCanOmitTheOptionalIdempotencyKey() {
        Supplier supplier = persistSupplier();
        Warehouse warehouse = persistWarehouse("DEL-PO-NULL-KEY");
        PurchaseOrder first = entityManager.persistAndFlush(
                purchaseOrder("PO-NULL-001", supplier, warehouse, null));
        PurchaseOrder second = entityManager.persistAndFlush(
                purchaseOrder("PO-NULL-002", supplier, warehouse, null));

        assertNotNull(first.getId());
        assertNotNull(second.getId());
        assertEquals(null, first.getIdempotencyKey());
        assertEquals(null, second.getIdempotencyKey());
    }

    @Test
    void requiredFieldsAndCurrencyCodeAreValidated() {
        PurchaseOrder order = validUnpersistedOrder();
        order.setPoNumber(" ");
        order.setSupplier(null);
        order.setWarehouse(null);
        order.setOrderDate(null);
        order.setStatus(null);
        order.setCurrencyCode("I R");

        Set<ConstraintViolation<PurchaseOrder>> violations = validator.validate(order);
        assertInvalid(violations, "poNumber");
        assertInvalid(violations, "supplier");
        assertInvalid(violations, "warehouse");
        assertInvalid(violations, "orderDate");
        assertInvalid(violations, "status");
        assertInvalid(violations, "currencyCode");

        order.setCurrencyCode("IN");
        assertTrue(validator.validate(order).stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("currencyCode")));
    }

    private void assertInvalid(Set<ConstraintViolation<PurchaseOrder>> violations, String field) {
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(field)),
                "Expected validation error for " + field);
    }

    private PurchaseOrder validUnpersistedOrder() {
        return purchaseOrder("PO-VALID", persistSupplier(), persistWarehouse("DEL-PO-VALID"), null);
    }

    private PurchaseOrder purchaseOrder(String number, Supplier supplier, Warehouse warehouse, String key) {
        return new PurchaseOrder(number, supplier, warehouse, LocalDate.of(2026, 11, 12),
                PurchaseOrderStatus.DRAFT, "INR", key);
    }

    private Supplier persistSupplier() {
        return entityManager.persistAndFlush(new Supplier("SUP-PO", "Acme Supplies", SupplierStatus.ACTIVE));
    }

    private Warehouse persistWarehouse(String code) {
        return entityManager.persistAndFlush(new Warehouse(code, "Fulfillment Center", "Delhi", "IN", true));
    }
}
