package com.procureai.config;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seeds a small, repeatable Wireless Mouse purchasing scenario for local demos. */
@Component
public class DemoDataSeeder implements CommandLineRunner {

    static final String PRODUCT_SKU = "WM-001";
    static final String WAREHOUSE_CODE = "DEL-01";
    static final String PRIMARY_SUPPLIER_CODE = "SUP-NORTHSTAR";
    static final String ALTERNATE_SUPPLIER_CODE = "SUP-METROTECH";
    static final String OPEN_PO_NUMBER = "PO-DEL-DEMO-001";
    static final String DRAFT_PO_NUMBER = "PO-DEL-DEMO-002";
    static final UUID FORECAST_RUN_ID = UUID.fromString("b206d13a-1533-4e11-a555-180a555b0828");
    static final int FORECAST_HORIZON_DAYS = 30;
    static final String FORECAST_MODEL_VERSION = "procureai-demo-v1";

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final BigDecimal PRIMARY_UNIT_PRICE = new BigDecimal("249.5000");
    private static final BigDecimal ALTERNATE_UNIT_PRICE = new BigDecimal("269.0000");
    private static final BigDecimal BUDGET_AMOUNT = new BigDecimal("500000.0000");

    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierProductRepository supplierProductRepository;
    private final InventoryRepository inventoryRepository;
    private final ReplenishmentPolicyRepository replenishmentPolicyRepository;
    private final DemandForecastRepository demandForecastRepository;
    private final PurchasingBudgetRepository purchasingBudgetRepository;
    private final StorageCapacityRepository storageCapacityRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;

    public DemoDataSeeder(
            ProductRepository productRepository,
            WarehouseRepository warehouseRepository,
            SupplierRepository supplierRepository,
            SupplierProductRepository supplierProductRepository,
            InventoryRepository inventoryRepository,
            ReplenishmentPolicyRepository replenishmentPolicyRepository,
            DemandForecastRepository demandForecastRepository,
            PurchasingBudgetRepository purchasingBudgetRepository,
            StorageCapacityRepository storageCapacityRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderItemRepository purchaseOrderItemRepository
    ) {
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.supplierRepository = supplierRepository;
        this.supplierProductRepository = supplierProductRepository;
        this.inventoryRepository = inventoryRepository;
        this.replenishmentPolicyRepository = replenishmentPolicyRepository;
        this.demandForecastRepository = demandForecastRepository;
        this.purchasingBudgetRepository = purchasingBudgetRepository;
        this.storageCapacityRepository = storageCapacityRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Product product = productRepository.findBySku(PRODUCT_SKU).orElseGet(() ->
                productRepository.save(new Product(
                        PRODUCT_SKU,
                        "Wireless Mouse",
                        "2.4 GHz wireless optical mouse",
                        "EACH",
                        new BigDecimal("0.001500"),
                        true
                )));

        Warehouse warehouse = warehouseRepository.findByCode(WAREHOUSE_CODE).orElseGet(() ->
                warehouseRepository.save(new Warehouse(
                        WAREHOUSE_CODE,
                        "Delhi Fulfillment Center",
                        "Delhi",
                        "IN",
                        true
                )));

        Supplier primarySupplier = supplierRepository.findByCode(PRIMARY_SUPPLIER_CODE).orElseGet(() ->
                supplierRepository.save(new Supplier(
                        PRIMARY_SUPPLIER_CODE,
                        "Northstar Office Supplies Pvt Ltd",
                        SupplierStatus.ACTIVE
                )));

        Supplier alternateSupplier = supplierRepository.findByCode(ALTERNATE_SUPPLIER_CODE).orElseGet(() ->
                supplierRepository.save(new Supplier(
                        ALTERNATE_SUPPLIER_CODE,
                        "MetroTech Distribution Pvt Ltd",
                        SupplierStatus.ACTIVE
                )));

        SupplierProduct primaryOffer = findOrCreateOffer(primarySupplier, product, warehouse,
                "NS-WM-001", 100L, 50L, 7, PRIMARY_UNIT_PRICE);
        SupplierProduct alternateOffer = findOrCreateOffer(alternateSupplier, product, warehouse,
                "MT-WM-001", 50L, 25L, 4, ALTERNATE_UNIT_PRICE);

        seedInventory(product, warehouse);
        seedReplenishmentPolicy(product, warehouse);
        seedForecasts(product, warehouse);
        seedBudget(warehouse);
        seedStorageCapacity(warehouse);
        seedOpenPurchaseOrder(primarySupplier, warehouse, primaryOffer);
        seedDraftPurchaseOrder(alternateSupplier, warehouse, alternateOffer);

        LOGGER.info("Initialized ProcureAI demo purchasing data for SKU {} at warehouse {}.",
                PRODUCT_SKU, WAREHOUSE_CODE);
    }

    private SupplierProduct findOrCreateOffer(
            Supplier supplier,
            Product product,
            Warehouse warehouse,
            String supplierSku,
            Long minimumOrderQuantity,
            Long packSize,
            Integer leadTimeDays,
            BigDecimal unitPrice
    ) {
        return supplierProductRepository.findByProductAndWarehouse(product, warehouse).stream()
                .filter(offer -> offer.getSupplier().getId().equals(supplier.getId()))
                .findFirst()
                .orElseGet(() -> supplierProductRepository.save(new SupplierProduct(
                        supplier,
                        product,
                        warehouse,
                        supplierSku,
                        minimumOrderQuantity,
                        packSize,
                        leadTimeDays,
                        unitPrice,
                        "INR",
                        SupplierOfferStatus.AVAILABLE
                )));
    }

    private void seedInventory(Product product, Warehouse warehouse) {
        inventoryRepository.findByProductAndWarehouse(product, warehouse).orElseGet(() ->
                inventoryRepository.save(new Inventory(
                        product,
                        warehouse,
                        260L,
                        45L,
                        LocalDateTime.now(Clock.systemUTC())
                )));
    }

    private void seedReplenishmentPolicy(Product product, Warehouse warehouse) {
        replenishmentPolicyRepository.findByProductAndWarehouse(product, warehouse).orElseGet(() ->
                replenishmentPolicyRepository.save(new ReplenishmentPolicy(product, warehouse, 90L, 7)));
    }

    private void seedForecasts(Product product, Warehouse warehouse) {
        List<DemandForecast> existingRunForecasts = demandForecastRepository.findByForecastRunId(FORECAST_RUN_ID);
        LocalDateTime generatedAt = existingRunForecasts.stream()
                .filter(forecast -> forecast.getProduct().getId().equals(product.getId())
                        && forecast.getWarehouse().getId().equals(warehouse.getId()))
                .map(DemandForecast::getGeneratedAt)
                .findFirst()
                .orElseGet(() -> LocalDateTime.now(Clock.systemUTC()));
        Set<LocalDate> existingDates = new HashSet<>();
        existingRunForecasts.stream()
                .filter(forecast -> forecast.getProduct().getId().equals(product.getId())
                        && forecast.getWarehouse().getId().equals(warehouse.getId()))
                .map(DemandForecast::getForecastDate)
                .forEach(existingDates::add);

        LocalDate firstForecastDate = LocalDate.now(Clock.systemUTC()).plusDays(1);
        for (int day = 0; day < FORECAST_HORIZON_DAYS; day++) {
            LocalDate forecastDate = firstForecastDate.plusDays(day);
            if (!existingDates.contains(forecastDate)) {
                long expectedUnits = 18L + (day % 5);
                demandForecastRepository.save(new DemandForecast(
                        product,
                        warehouse,
                        forecastDate,
                        expectedUnits,
                        FORECAST_RUN_ID,
                        generatedAt,
                        FORECAST_MODEL_VERSION
                ));
            }
        }
    }

    private void seedBudget(Warehouse warehouse) {
        int year = LocalDate.now(Clock.systemUTC()).getYear();
        LocalDate periodStart = LocalDate.of(year, 1, 1);
        LocalDate periodEnd = LocalDate.of(year, 12, 31);
        purchasingBudgetRepository.findByWarehouseAndPeriodStartAndPeriodEnd(
                warehouse, periodStart, periodEnd).orElseGet(() ->
                purchasingBudgetRepository.save(new PurchasingBudget(
                        warehouse, periodStart, periodEnd, BUDGET_AMOUNT, "INR")));
    }

    private void seedStorageCapacity(Warehouse warehouse) {
        // WMS allocation includes occupied space and inbound reservations; do not add PO volume separately.
        storageCapacityRepository.findByWarehouse(warehouse).orElseGet(() ->
                storageCapacityRepository.save(new StorageCapacity(
                        warehouse,
                        new BigDecimal("120.000000"),
                        new BigDecimal("82.000000"),
                        LocalDateTime.now(Clock.systemUTC())
                )));
    }

    private void seedOpenPurchaseOrder(Supplier supplier, Warehouse warehouse, SupplierProduct offer) {
        LocalDate today = LocalDate.now(Clock.systemUTC());
        PurchaseOrder order = findOrCreatePurchaseOrder(
                OPEN_PO_NUMBER, "demo-open-po-del-01", supplier, warehouse,
                today.minusDays(2), PurchaseOrderStatus.ACKNOWLEDGED);
        seedPurchaseOrderItem(order, supplier, warehouse, offer,
                300L, 250L, 50L, PRIMARY_UNIT_PRICE, today.plusDays(5));
    }

    private void seedDraftPurchaseOrder(Supplier supplier, Warehouse warehouse, SupplierProduct offer) {
        LocalDate today = LocalDate.now(Clock.systemUTC());
        PurchaseOrder order = findOrCreatePurchaseOrder(
                DRAFT_PO_NUMBER, "demo-draft-po-del-02", supplier, warehouse,
                today, PurchaseOrderStatus.DRAFT);
        seedPurchaseOrderItem(order, supplier, warehouse, offer,
                100L, null, 0L, ALTERNATE_UNIT_PRICE, today.plusDays(8));
    }

    private PurchaseOrder findOrCreatePurchaseOrder(
            String poNumber,
            String idempotencyKey,
            Supplier supplier,
            Warehouse warehouse,
            LocalDate orderDate,
            PurchaseOrderStatus status
    ) {
        PurchaseOrder byNumber = purchaseOrderRepository.findByPoNumber(poNumber).orElse(null);
        if (byNumber != null) {
            boolean isExpectedSeedOrder = hasMatchingHeader(byNumber, supplier, warehouse)
                    && byNumber.getStatus() == status
                    && "INR".equals(byNumber.getCurrencyCode())
                    && idempotencyKey.equals(byNumber.getIdempotencyKey());
            return isExpectedSeedOrder ? byNumber : null;
        }

        if (purchaseOrderRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return null;
        }

        return purchaseOrderRepository.save(new PurchaseOrder(
                poNumber, supplier, warehouse, orderDate, status, "INR", idempotencyKey));
    }

    private void seedPurchaseOrderItem(
            PurchaseOrder order,
            Supplier supplier,
            Warehouse warehouse,
            SupplierProduct offer,
            Long orderedUnits,
            Long confirmedUnits,
            Long receivedUnits,
            BigDecimal unitPrice,
            LocalDate expectedDeliveryDate
    ) {
        if (order == null || !hasMatchingHeader(order, supplier, warehouse)
                || !offer.getSupplier().getId().equals(supplier.getId())
                || !offer.getWarehouse().getId().equals(warehouse.getId())) {
            return;
        }

        boolean existingLine = purchaseOrderItemRepository.findByPurchaseOrder(order).stream()
                .anyMatch(item -> item.getSupplierProduct().getId().equals(offer.getId()));
        if (!existingLine) {
            purchaseOrderItemRepository.save(new PurchaseOrderItem(
                    order, offer, orderedUnits, confirmedUnits, receivedUnits, unitPrice, expectedDeliveryDate));
        }
    }

    private boolean hasMatchingHeader(PurchaseOrder order, Supplier supplier, Warehouse warehouse) {
        return order.getSupplier().getId().equals(supplier.getId())
                && order.getWarehouse().getId().equals(warehouse.getId());
    }

}
