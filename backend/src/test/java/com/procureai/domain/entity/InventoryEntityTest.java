package com.procureai.domain.entity;

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
class InventoryEntityTest {

    @Autowired
    private TestEntityManager entityManager;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void inventoryPersistsAndCanBeRetrievedWithProductAndWarehouse() {
        Product product = persistProduct();
        Warehouse warehouse = persistWarehouse();
        Inventory inventory = entityManager.persistAndFlush(
                inventory(product, warehouse, 20L, 3L));
        Long inventoryId = inventory.getId();

        entityManager.clear();
        Inventory found = entityManager.find(Inventory.class, inventoryId);

        assertNotNull(found);
        assertEquals(inventoryId, found.getId());
        assertEquals(product.getId(), found.getProduct().getId());
        assertEquals(warehouse.getId(), found.getWarehouse().getId());
        assertEquals(20L, found.getOnHandUnits());
        assertEquals(3L, found.getReservedUnits());
    }

    @Test
    void productAndWarehousePairMustBeUnique() {
        Product product = persistProduct();
        Warehouse warehouse = persistWarehouse();
        entityManager.persistAndFlush(inventory(product, warehouse, 20L, 3L));

        assertThrows(RuntimeException.class,
                () -> entityManager.persistAndFlush(inventory(product, warehouse, 12L, 1L)));
    }

    @Test
    void negativeQuantitiesAreRejected() {
        Product product = persistProduct();
        Warehouse warehouse = persistWarehouse();
        Inventory inventory = inventory(product, warehouse, -1L, 0L);

        Set<ConstraintViolation<Inventory>> violations = validator.validate(inventory);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("onHandUnits")));
        inventory.setOnHandUnits(1L);
        inventory.setReservedUnits(-1L);
        assertTrue(validator.validate(inventory).stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("reservedUnits")));
    }

    @Test
    void reservedGreaterThanOnHandIsPreservedForInvestigation() {
        Product product = persistProduct();
        Warehouse warehouse = persistWarehouse();
        Inventory inventory = entityManager.persistAndFlush(
                inventory(product, warehouse, 2L, 5L));

        entityManager.clear();
        Inventory found = entityManager.find(Inventory.class, inventory.getId());

        assertEquals(2L, found.getOnHandUnits());
        assertEquals(5L, found.getReservedUnits());
    }

    @Test
    void versionIncrementsWhenInventoryIsUpdated() {
        Product product = persistProduct();
        Warehouse warehouse = persistWarehouse();
        Inventory inventory = entityManager.persistAndFlush(
                inventory(product, warehouse, 20L, 3L));
        Long initialVersion = inventory.getVersion();

        inventory.setOnHandUnits(21L);
        entityManager.flush();

        assertTrue(inventory.getVersion() > initialVersion);
    }

    private Product persistProduct() {
        return entityManager.persistAndFlush(
                new Product("MOUSE-INV", "Wireless Mouse", null, "EACH", null, true));
    }

    private Warehouse persistWarehouse() {
        return entityManager.persistAndFlush(
                new Warehouse("DEL-INV", "Delhi Fulfillment Center", "Delhi", "IN", true));
    }

    private Inventory inventory(Product product, Warehouse warehouse, Long onHand, Long reserved) {
        return new Inventory(product, warehouse, onHand, reserved, LocalDateTime.now(Clock.systemUTC()));
    }
}
