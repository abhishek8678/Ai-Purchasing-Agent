package com.procureai.repository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import com.procureai.domain.entity.Inventory;
import com.procureai.domain.entity.Product;
import com.procureai.domain.entity.ReplenishmentPolicy;
import com.procureai.domain.entity.Supplier;
import com.procureai.domain.entity.SupplierProduct;
import com.procureai.domain.entity.Warehouse;
import com.procureai.domain.enums.SupplierOfferStatus;
import com.procureai.domain.enums.SupplierStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class CoreDomainRepositoriesTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private SupplierProductRepository supplierProductRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ReplenishmentPolicyRepository replenishmentPolicyRepository;

    @Test
    void productRepositoryFindsBySkuAndFiltersActiveProducts() {
        Product active = saveProduct("SKU-REPO-ACTIVE", true);
        Product inactive = saveProduct("SKU-REPO-INACTIVE", false);

        assertEquals(active, productRepository.findBySku(active.getSku()).orElseThrow());
        assertTrue(productRepository.findBySku("SKU-REPO-MISSING").isEmpty());
        assertEquals(Set.of(active.getId()), ids(productRepository.findByActiveTrue()));
        assertFalse(productRepository.findByActiveTrue().contains(inactive));
    }

    @Test
    void warehouseRepositoryFindsByCodeAndFiltersActiveWarehouses() {
        Warehouse active = saveWarehouse("WH-REPO-ACTIVE", true);
        Warehouse inactive = saveWarehouse("WH-REPO-INACTIVE", false);

        assertEquals(active, warehouseRepository.findByCode(active.getCode()).orElseThrow());
        assertTrue(warehouseRepository.findByCode("WH-REPO-MISSING").isEmpty());
        assertEquals(Set.of(active.getId()), ids(warehouseRepository.findByActiveTrue()));
        assertFalse(warehouseRepository.findByActiveTrue().contains(inactive));
    }

    @Test
    void supplierRepositoryFindsByCodeAndStatus() {
        Supplier active = saveSupplier("SUP-REPO-ACTIVE", SupplierStatus.ACTIVE);
        Supplier inactive = saveSupplier("SUP-REPO-INACTIVE", SupplierStatus.INACTIVE);

        assertEquals(active, supplierRepository.findByCode(active.getCode()).orElseThrow());
        assertTrue(supplierRepository.findByCode("SUP-REPO-MISSING").isEmpty());
        assertEquals(Set.of(active.getId()), ids(supplierRepository.findByStatus(SupplierStatus.ACTIVE)));
        assertEquals(Set.of(inactive.getId()), ids(supplierRepository.findByStatus(SupplierStatus.INACTIVE)));
    }

    @Test
    void supplierProductRepositoryFiltersByProductWarehouseSupplierAndStatus() {
        Product mouse = saveProduct("SKU-REPO-MOUSE", true);
        Product keyboard = saveProduct("SKU-REPO-KEYBOARD", true);
        Warehouse delhi = saveWarehouse("WH-REPO-DELHI", true);
        Warehouse mumbai = saveWarehouse("WH-REPO-MUMBAI", true);
        Supplier firstSupplier = saveSupplier("SUP-REPO-FIRST", SupplierStatus.ACTIVE);
        Supplier secondSupplier = saveSupplier("SUP-REPO-SECOND", SupplierStatus.ACTIVE);

        SupplierProduct mouseDelhiAvailable = saveOffer(
                firstSupplier, mouse, delhi, SupplierOfferStatus.AVAILABLE);
        SupplierProduct mouseDelhiInactive = saveOffer(
                secondSupplier, mouse, delhi, SupplierOfferStatus.INACTIVE);
        SupplierProduct mouseMumbai = saveOffer(
                firstSupplier, mouse, mumbai, SupplierOfferStatus.TEMPORARILY_UNAVAILABLE);
        SupplierProduct keyboardDelhi = saveOffer(
                firstSupplier, keyboard, delhi, SupplierOfferStatus.AVAILABLE);

        assertEquals(Set.of(mouseDelhiAvailable.getId(), mouseDelhiInactive.getId()),
                ids(supplierProductRepository.findByProductAndWarehouse(mouse, delhi)));
        assertEquals(Set.of(mouseDelhiAvailable.getId(), keyboardDelhi.getId()),
                ids(supplierProductRepository.findBySupplierAndWarehouse(firstSupplier, delhi)));
        assertEquals(Set.of(mouseDelhiAvailable.getId()), ids(
                supplierProductRepository.findByProductAndWarehouseAndStatus(
                        mouse, delhi, SupplierOfferStatus.AVAILABLE)));
        assertFalse(supplierProductRepository.findByProductAndWarehouse(mouse, delhi).contains(mouseMumbai));
    }

    @Test
    void inventoryRepositoryFindsUniquePairAndListsByWarehouseOrProduct() {
        Product mouse = saveProduct("SKU-REPO-INV-MOUSE", true);
        Product keyboard = saveProduct("SKU-REPO-INV-KEYBOARD", true);
        Warehouse delhi = saveWarehouse("WH-REPO-INV-DELHI", true);
        Warehouse mumbai = saveWarehouse("WH-REPO-INV-MUMBAI", true);
        Inventory mouseDelhi = saveInventory(mouse, delhi);
        Inventory keyboardDelhi = saveInventory(keyboard, delhi);
        Inventory mouseMumbai = saveInventory(mouse, mumbai);

        assertEquals(mouseDelhi, inventoryRepository.findByProductAndWarehouse(mouse, delhi).orElseThrow());
        assertTrue(inventoryRepository.findByProductAndWarehouse(keyboard, mumbai).isEmpty());
        assertEquals(Set.of(mouseDelhi.getId(), keyboardDelhi.getId()),
                ids(inventoryRepository.findByWarehouse(delhi)));
        assertEquals(Set.of(mouseDelhi.getId(), mouseMumbai.getId()),
                ids(inventoryRepository.findByProduct(mouse)));
    }

    @Test
    void replenishmentPolicyRepositoryFindsUniquePairAndListsByWarehouse() {
        Product mouse = saveProduct("SKU-REPO-POL-MOUSE", true);
        Product keyboard = saveProduct("SKU-REPO-POL-KEYBOARD", true);
        Warehouse delhi = saveWarehouse("WH-REPO-POL-DELHI", true);
        Warehouse mumbai = saveWarehouse("WH-REPO-POL-MUMBAI", true);
        ReplenishmentPolicy mouseDelhi = savePolicy(mouse, delhi);
        ReplenishmentPolicy keyboardDelhi = savePolicy(keyboard, delhi);
        savePolicy(mouse, mumbai);

        assertEquals(mouseDelhi,
                replenishmentPolicyRepository.findByProductAndWarehouse(mouse, delhi).orElseThrow());
        assertTrue(replenishmentPolicyRepository.findByProductAndWarehouse(keyboard, mumbai).isEmpty());
        assertEquals(Set.of(mouseDelhi.getId(), keyboardDelhi.getId()),
                ids(replenishmentPolicyRepository.findByWarehouse(delhi)));
    }

    private Set<Long> ids(java.util.List<?> entities) {
        return entities.stream()
                .map(entity -> {
                    if (entity instanceof Product product) return product.getId();
                    if (entity instanceof Warehouse warehouse) return warehouse.getId();
                    if (entity instanceof Supplier supplier) return supplier.getId();
                    if (entity instanceof SupplierProduct supplierProduct) return supplierProduct.getId();
                    if (entity instanceof Inventory inventory) return inventory.getId();
                    if (entity instanceof ReplenishmentPolicy policy) return policy.getId();
                    throw new IllegalArgumentException("Unsupported entity: " + entity.getClass());
                })
                .collect(Collectors.toSet());
    }

    private Product saveProduct(String sku, boolean active) {
        return productRepository.saveAndFlush(new Product(sku, sku, null, "EACH", null, active));
    }

    private Warehouse saveWarehouse(String code, boolean active) {
        return warehouseRepository.saveAndFlush(new Warehouse(code, code, "Delhi", "IN", active));
    }

    private Supplier saveSupplier(String code, SupplierStatus status) {
        return supplierRepository.saveAndFlush(new Supplier(code, code, status));
    }

    private SupplierProduct saveOffer(
            Supplier supplier, Product product, Warehouse warehouse, SupplierOfferStatus status
    ) {
        return supplierProductRepository.saveAndFlush(new SupplierProduct(
                supplier, product, warehouse, "SKU-" + product.getSku(), 1L, 1L, 2,
                BigDecimal.ONE, "INR", status));
    }

    private Inventory saveInventory(Product product, Warehouse warehouse) {
        return inventoryRepository.saveAndFlush(new Inventory(product, warehouse, 100L, 10L,
                LocalDateTime.now(Clock.systemUTC())));
    }

    private ReplenishmentPolicy savePolicy(Product product, Warehouse warehouse) {
        return replenishmentPolicyRepository.saveAndFlush(new ReplenishmentPolicy(product, warehouse, 10L, 7));
    }
}
