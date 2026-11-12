package com.procureai.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import com.procureai.domain.enums.PurchaseOrderStatus;
import com.procureai.domain.enums.SupplierOfferStatus;
import com.procureai.domain.enums.SupplierStatus;
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
class PurchaseOrderItemEntityTest {

    @Autowired
    private TestEntityManager entityManager;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void itemPersistsWithOrderOfferPriceAndOptionalDeliveryDate() {
        PurchaseOrder order = persistOrder("PO-ITEM-001");
        SupplierProduct offer = persistOffer("DEL-ITEM-001");
        PurchaseOrderItem item = entityManager.persistAndFlush(item(
                order, offer, 50L, null, 0L, new BigDecimal("42.3750"), null));
        Long id = item.getId();

        entityManager.clear();
        PurchaseOrderItem found = entityManager.find(PurchaseOrderItem.class, id);
        assertNotNull(found);
        assertEquals(order.getId(), found.getPurchaseOrder().getId());
        assertEquals(offer.getId(), found.getSupplierProduct().getId());
        assertEquals(50L, found.getOrderedUnits());
        assertEquals(null, found.getConfirmedUnits());
        assertEquals(0L, found.getReceivedUnits());
        assertEquals(new BigDecimal("42.3750"), found.getUnitPriceAtOrder());
        assertEquals(null, found.getExpectedDeliveryDate());
        assertNotNull(found.getCreatedAt());
        assertNotNull(found.getUpdatedAt());
    }

    @Test
    void purchaseOrderAndSupplierOfferCombinationMustBeUniqueInDatabase() {
        PurchaseOrder order = persistOrder("PO-ITEM-DUP");
        SupplierProduct offer = persistOffer("DEL-ITEM-DUP");
        entityManager.persistAndFlush(item(order, offer, 10L, null, 0L, BigDecimal.ONE, null));

        assertThrows(RuntimeException.class, () -> entityManager.persistAndFlush(
                item(order, offer, 20L, null, 0L, BigDecimal.TEN, null)));
    }

    @Test
    void orderedQuantityMustBePositiveAndReceivedCannotBeNegative() {
        PurchaseOrderItem item = validUnpersistedItem();
        item.setOrderedUnits(0L);
        assertViolation(item, "orderedUnits");
        item.setOrderedUnits(10L);
        item.setReceivedUnits(-1L);
        assertViolation(item, "receivedUnits");
        assertViolation(item, "receivedUnitsWithinOrderedUnits");
    }

    @Test
    void confirmedAndReceivedQuantitiesCannotExceedOrderedQuantity() {
        PurchaseOrderItem item = validUnpersistedItem();
        item.setOrderedUnits(10L);
        item.setConfirmedUnits(11L);
        assertViolation(item, "confirmedUnitsWithinOrderedUnits");
        item.setConfirmedUnits(10L);
        item.setReceivedUnits(11L);
        assertViolation(item, "receivedUnitsWithinOrderedUnits");
    }

    @Test
    void negativeConfirmedQuantityAndPriceAreRejected() {
        PurchaseOrderItem item = validUnpersistedItem();
        item.setConfirmedUnits(-1L);
        assertViolation(item, "confirmedUnits");
        item.setConfirmedUnits(null);
        item.setUnitPriceAtOrder(new BigDecimal("-0.01"));
        assertViolation(item, "unitPriceAtOrder");
    }

    private void assertViolation(PurchaseOrderItem item, String field) {
        Set<ConstraintViolation<PurchaseOrderItem>> violations = validator.validate(item);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(field)),
                "Expected validation error for " + field + "; actual violations: " + violations);
    }

    private PurchaseOrderItem validUnpersistedItem() {
        return item(persistOrder("PO-ITEM-VALID"), persistOffer("DEL-ITEM-VALID"),
                10L, null, 0L, BigDecimal.ONE, null);
    }

    private PurchaseOrderItem item(
            PurchaseOrder order,
            SupplierProduct offer,
            Long ordered,
            Long confirmed,
            Long received,
            BigDecimal price,
            LocalDate deliveryDate
    ) {
        return new PurchaseOrderItem(order, offer, ordered, confirmed, received, price, deliveryDate);
    }

    private PurchaseOrder persistOrder(String number) {
        Supplier supplier = entityManager.persistAndFlush(
                new Supplier("SUP-" + number, "Acme Supplies", SupplierStatus.ACTIVE));
        Warehouse warehouse = entityManager.persistAndFlush(
                new Warehouse("WH-" + number, "Fulfillment Center", "Delhi", "IN", true));
        return entityManager.persistAndFlush(new PurchaseOrder(number, supplier, warehouse,
                LocalDate.of(2026, 11, 12), PurchaseOrderStatus.SUBMITTED, "INR", null));
    }

    private SupplierProduct persistOffer(String warehouseCode) {
        Supplier supplier = entityManager.persistAndFlush(new Supplier(
                "SUP-" + warehouseCode, "Acme Supplies", SupplierStatus.ACTIVE));
        Product product = entityManager.persistAndFlush(new Product(
                "SKU-" + warehouseCode, "Wireless Mouse", null, "EACH", null, true));
        Warehouse warehouse = entityManager.persistAndFlush(new Warehouse(
                warehouseCode, "Fulfillment Center", "Delhi", "IN", true));
        return entityManager.persistAndFlush(new SupplierProduct(supplier, product, warehouse,
                "ACME-MOUSE", 1L, 1L, 0, BigDecimal.ONE, "INR", SupplierOfferStatus.AVAILABLE));
    }
}
