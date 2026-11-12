package com.procureai.config;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

import com.procureai.domain.entity.DemandForecast;
import com.procureai.domain.entity.Inventory;
import com.procureai.domain.entity.Product;
import com.procureai.domain.entity.PurchaseOrder;
import com.procureai.domain.entity.PurchaseOrderItem;
import com.procureai.domain.entity.PurchasingBudget;
import com.procureai.domain.entity.ReplenishmentPolicy;
import com.procureai.domain.entity.StorageCapacity;
import com.procureai.domain.entity.Supplier;
import com.procureai.domain.entity.SupplierProduct;
import com.procureai.domain.entity.Warehouse;
import com.procureai.domain.enums.PurchaseOrderStatus;
import com.procureai.domain.enums.SupplierOfferStatus;
import com.procureai.domain.enums.SupplierStatus;
import com.procureai.repository.AgentRunRepository;
import com.procureai.repository.DemandForecastRepository;
import com.procureai.repository.InventoryRepository;
import com.procureai.repository.ProductRepository;
import com.procureai.repository.PurchaseOrderItemRepository;
import com.procureai.repository.PurchaseOrderRepository;
import com.procureai.repository.PurchasingBudgetRepository;
import com.procureai.repository.ReplenishmentPolicyRepository;
import com.procureai.repository.StorageCapacityRepository;
import com.procureai.repository.SupplierProductRepository;
import com.procureai.repository.SupplierRepository;
import com.procureai.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DemoDataSeederTest {

    @Autowired
    private DemoDataSeeder demoDataSeeder;

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

    @Autowired
    private DemandForecastRepository demandForecastRepository;

    @Autowired
    private PurchasingBudgetRepository purchasingBudgetRepository;

    @Autowired
    private StorageCapacityRepository storageCapacityRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private PurchaseOrderItemRepository purchaseOrderItemRepository;

    @Autowired
    private AgentRunRepository agentRunRepository;

    @Test
    @Transactional
    void startupSeedsCoherentScenarioAndRepeatedSeedingIsIdempotent() throws Exception {
        Product product = productRepository.findBySku(DemoDataSeeder.PRODUCT_SKU).orElseThrow();
        Warehouse warehouse = warehouseRepository.findByCode(DemoDataSeeder.WAREHOUSE_CODE).orElseThrow();
        assertEquals("Wireless Mouse", product.getName());
        assertEquals("EACH", product.getUnitOfMeasure());
        assertNotNull(product.getUnitVolumeM3());
        assertEquals("Delhi Fulfillment Center", warehouse.getName());
        assertEquals("Delhi", warehouse.getCity());
        assertEquals("IN", warehouse.getCountryCode());

        Supplier primary = supplierRepository.findByCode(DemoDataSeeder.PRIMARY_SUPPLIER_CODE).orElseThrow();
        Supplier alternate = supplierRepository.findByCode(DemoDataSeeder.ALTERNATE_SUPPLIER_CODE).orElseThrow();
        assertEquals(SupplierStatus.ACTIVE, primary.getStatus());
        assertEquals(SupplierStatus.ACTIVE, alternate.getStatus());

        List<SupplierProduct> offers = supplierProductRepository.findByProductAndWarehouse(product, warehouse);
        assertEquals(2, offers.size());
        SupplierProduct primaryOffer = offers.stream()
                .filter(offer -> offer.getSupplier().getId().equals(primary.getId())).findFirst().orElseThrow();
        SupplierProduct alternateOffer = offers.stream()
                .filter(offer -> offer.getSupplier().getId().equals(alternate.getId())).findFirst().orElseThrow();
        assertEquals(SupplierOfferStatus.AVAILABLE, primaryOffer.getStatus());
        assertEquals(SupplierOfferStatus.AVAILABLE, alternateOffer.getStatus());
        assertTrue(primaryOffer.getUnitPrice().compareTo(alternateOffer.getUnitPrice()) < 0);
        assertTrue(primaryOffer.getLeadTimeDays() > alternateOffer.getLeadTimeDays());
        assertEquals("INR", primaryOffer.getCurrencyCode());
        assertEquals("INR", alternateOffer.getCurrencyCode());

        Inventory inventory = inventoryRepository.findByProductAndWarehouse(product, warehouse).orElseThrow();
        assertEquals(260L, inventory.getOnHandUnits());
        assertEquals(45L, inventory.getReservedUnits());
        ReplenishmentPolicy policy = replenishmentPolicyRepository
                .findByProductAndWarehouse(product, warehouse).orElseThrow();
        assertEquals(90L, policy.getSafetyStockUnits());
        assertEquals(7, policy.getReviewPeriodDays());

        List<DemandForecast> forecasts = demandForecastRepository.findByForecastRunId(
                DemoDataSeeder.FORECAST_RUN_ID);
        assertEquals(DemoDataSeeder.FORECAST_HORIZON_DAYS, forecasts.size());
        assertTrue(forecasts.stream().allMatch(forecast -> forecast.getProduct().getId().equals(product.getId())
                && forecast.getWarehouse().getId().equals(warehouse.getId())
                && DemoDataSeeder.FORECAST_MODEL_VERSION.equals(forecast.getModelVersion())));
        assertTrue(forecasts.stream().map(DemandForecast::getForecastDate).distinct().count()
                == DemoDataSeeder.FORECAST_HORIZON_DAYS);

        int year = Year.now(Clock.systemUTC()).getValue();
        PurchasingBudget budget = purchasingBudgetRepository.findByWarehouseAndPeriodStartAndPeriodEnd(
                warehouse, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)).orElseThrow();
        assertEquals("INR", budget.getCurrencyCode());
        assertTrue(budget.getBudgetAmount().signum() > 0);
        StorageCapacity capacity = storageCapacityRepository.findByWarehouse(warehouse).orElseThrow();
        assertTrue(capacity.getTotalVolumeM3().compareTo(capacity.getAllocatedVolumeM3()) > 0);
        assertNotNull(capacity.getMeasuredAt());

        PurchaseOrder openOrder = purchaseOrderRepository.findByPoNumber(DemoDataSeeder.OPEN_PO_NUMBER)
                .orElseThrow();
        assertEquals(PurchaseOrderStatus.ACKNOWLEDGED, openOrder.getStatus());
        PurchaseOrderItem openLine = purchaseOrderItemRepository.findByPurchaseOrder(openOrder).stream()
                .findFirst().orElseThrow();
        assertEquals(300L, openLine.getOrderedUnits());
        assertEquals(250L, openLine.getConfirmedUnits());
        assertEquals(50L, openLine.getReceivedUnits());
        assertTrue(openLine.getConfirmedUnits() <= openLine.getOrderedUnits());
        assertTrue(openLine.getReceivedUnits() <= openLine.getOrderedUnits());
        assertEquals(primary.getId(), openOrder.getSupplier().getId());
        assertEquals(warehouse.getId(), openOrder.getWarehouse().getId());
        assertEquals(primary.getId(), openLine.getSupplierProduct().getSupplier().getId());
        assertEquals(warehouse.getId(), openLine.getSupplierProduct().getWarehouse().getId());
        assertEquals("INR", openOrder.getCurrencyCode());

        PurchaseOrder draftOrder = purchaseOrderRepository.findByPoNumber(DemoDataSeeder.DRAFT_PO_NUMBER)
                .orElseThrow();
        assertEquals(PurchaseOrderStatus.DRAFT, draftOrder.getStatus());
        PurchaseOrderItem draftLine = purchaseOrderItemRepository.findByPurchaseOrder(draftOrder).stream()
                .findFirst().orElseThrow();
        assertEquals(100L, draftLine.getOrderedUnits());
        assertNull(draftLine.getConfirmedUnits());
        assertEquals(0L, draftLine.getReceivedUnits());
        assertEquals(alternate.getId(), draftLine.getSupplierProduct().getSupplier().getId());

        assertTrue(agentRunRepository.findByProductAndWarehouse(product, warehouse).isEmpty(),
                "Demo data must not fabricate an unfinished or completed AI investigation.");

        long offersBefore = offers.size();
        long forecastsBefore = forecasts.size();
        long runsBefore = agentRunRepository.findByProductAndWarehouse(product, warehouse).size();
        long productsBefore = productRepository.count();
        long warehousesBefore = warehouseRepository.count();
        long suppliersBefore = supplierRepository.count();
        long inventoryBefore = inventoryRepository.count();
        long policiesBefore = replenishmentPolicyRepository.count();
        long budgetsBefore = purchasingBudgetRepository.count();
        long capacitiesBefore = storageCapacityRepository.count();
        long ordersBefore = purchaseOrderRepository.count();
        long itemsBefore = purchaseOrderItemRepository.count();
        product.setName("Manually preserved product name");
        productRepository.saveAndFlush(product);
        inventory.setOnHandUnits(777L);
        inventoryRepository.saveAndFlush(inventory);

        demoDataSeeder.run();

        assertEquals(offersBefore, supplierProductRepository.findByProductAndWarehouse(product, warehouse).size());
        assertEquals(forecastsBefore, demandForecastRepository
                .findByForecastRunId(DemoDataSeeder.FORECAST_RUN_ID).size());
        assertEquals(runsBefore, agentRunRepository.findByProductAndWarehouse(product, warehouse).size());
        assertEquals(productsBefore, productRepository.count());
        assertEquals(warehousesBefore, warehouseRepository.count());
        assertEquals(suppliersBefore, supplierRepository.count());
        assertEquals(inventoryBefore, inventoryRepository.count());
        assertEquals(policiesBefore, replenishmentPolicyRepository.count());
        assertEquals(budgetsBefore, purchasingBudgetRepository.count());
        assertEquals(capacitiesBefore, storageCapacityRepository.count());
        assertEquals(ordersBefore, purchaseOrderRepository.count());
        assertEquals(itemsBefore, purchaseOrderItemRepository.count());
        assertEquals("Manually preserved product name",
                productRepository.findBySku(DemoDataSeeder.PRODUCT_SKU).orElseThrow().getName());
        assertEquals(777L, inventoryRepository.findByProductAndWarehouse(product, warehouse)
                .orElseThrow().getOnHandUnits());
        assertEquals(1, purchaseOrderRepository.findBySupplierAndStatus(primary, PurchaseOrderStatus.ACKNOWLEDGED)
                .size());
        assertEquals(1, purchaseOrderRepository.findBySupplierAndStatus(alternate, PurchaseOrderStatus.DRAFT).size());
    }
}
