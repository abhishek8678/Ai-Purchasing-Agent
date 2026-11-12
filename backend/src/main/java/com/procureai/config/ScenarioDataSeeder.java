package com.procureai.config;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds one self-contained dataset per additional assignment scenario (2, 3 and 4).
 *
 * <p>Each scenario uses its own product and warehouse so budgets, storage and open purchase orders never
 * interfere with the primary Scenario 1 data (WM-001 at DEL-01). The data only describes the business situation;
 * it does not encode any expected agent decision. Seeding is idempotent: existing rows are left untouched.</p>
 */
@Component
public class ScenarioDataSeeder implements CommandLineRunner {

    static final String NORTHSTAR = "SUP-NORTHSTAR";
    static final String METROTECH = "SUP-METROTECH";
    static final String APEX = "SUP-APEX";

    /** Scenario 2: a 500-unit PO where the supplier has confirmed only 250 units. */
    static final String SHORTFALL_SKU = "KB-002";
    static final String SHORTFALL_WAREHOUSE = "BLR-02";
    static final String SHORTFALL_PO = "PO-BLR-SCN2-001";

    /** Scenario 3: a newer demand-sensing forecast run supersedes a normal-demand run. */
    static final String DEMAND_SKU = "HS-003";
    static final String DEMAND_WAREHOUSE = "MUM-03";
    static final String DEMAND_PO = "PO-MUM-SCN3-001";
    static final UUID BASELINE_FORECAST_RUN = UUID.fromString("3a7d1f0e-0b51-4a3e-9c1d-5f0b7e2c3a01");
    static final UUID SPIKE_FORECAST_RUN = UUID.fromString("3a7d1f0e-0b51-4a3e-9c1d-5f0b7e2c3a02");

    /** Scenario 4: a bulky, expensive product where budget and storage limit the recommended purchase. */
    static final String CONSTRAINT_SKU = "MN-004";
    static final String CONSTRAINT_WAREHOUSE = "HYD-04";
    static final UUID CONSTRAINT_FORECAST_RUN = UUID.fromString("3a7d1f0e-0b51-4a3e-9c1d-5f0b7e2c3a04");
    static final UUID SHORTFALL_FORECAST_RUN = UUID.fromString("3a7d1f0e-0b51-4a3e-9c1d-5f0b7e2c3a03");

    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    private final SupplierRepository suppliers;
    private final SupplierProductRepository offers;
    private final InventoryRepository inventories;
    private final ReplenishmentPolicyRepository policies;
    private final DemandForecastRepository forecasts;
    private final PurchasingBudgetRepository budgets;
    private final StorageCapacityRepository capacities;
    private final PurchaseOrderRepository orders;
    private final PurchaseOrderItemRepository orderItems;

    public ScenarioDataSeeder(ProductRepository products, WarehouseRepository warehouses,
                              SupplierRepository suppliers, SupplierProductRepository offers,
                              InventoryRepository inventories, ReplenishmentPolicyRepository policies,
                              DemandForecastRepository forecasts, PurchasingBudgetRepository budgets,
                              StorageCapacityRepository capacities, PurchaseOrderRepository orders,
                              PurchaseOrderItemRepository orderItems) {
        this.products = products;
        this.warehouses = warehouses;
        this.suppliers = suppliers;
        this.offers = offers;
        this.inventories = inventories;
        this.policies = policies;
        this.forecasts = forecasts;
        this.budgets = budgets;
        this.capacities = capacities;
        this.orders = orders;
        this.orderItems = orderItems;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Supplier northstar = supplier(NORTHSTAR, "Northstar Office Supplies Pvt Ltd");
        Supplier metrotech = supplier(METROTECH, "MetroTech Distribution Pvt Ltd");
        Supplier apex = supplier(APEX, "Apex Peripherals India Pvt Ltd");
        seedSupplierShortfall(northstar, metrotech, apex);
        seedDemandChange(northstar, metrotech, apex);
        seedPurchasingConstraint(northstar, metrotech);
    }

    private void seedSupplierShortfall(Supplier northstar, Supplier metrotech, Supplier apex) {
        Product keyboard = product(SHORTFALL_SKU, "USB Keyboard", "Full-size wired USB keyboard", "0.002500");
        Warehouse blr = warehouse(SHORTFALL_WAREHOUSE, "Bengaluru Fulfillment Center", "Bengaluru");
        SupplierProduct apexOffer = offer(apex, keyboard, blr, "AP-KB-002", 100L, 50L, 6, "499.0000");
        offer(northstar, keyboard, blr, "NS-KB-002", 100L, 50L, 7, "520.0000");
        offer(metrotech, keyboard, blr, "MT-KB-002", 50L, 25L, 4, "559.0000");
        inventory(keyboard, blr, 140L, 20L);
        policy(keyboard, blr, 60L, 7);
        forecastRun(keyboard, blr, SHORTFALL_FORECAST_RUN, "procureai-demo-v1", 0, 18, 4);
        budget(blr, "450000.0000");
        capacity(blr, "60.000000", "31.000000");
        LocalDate today = today();
        // The supplier acknowledged the 500-unit PO but confirmed only 250 units.
        purchaseOrder(SHORTFALL_PO, "scenario2-shortfall-po-blr-02", apex, blr, today.minusDays(3),
                PurchaseOrderStatus.ACKNOWLEDGED, apexOffer, 500L, 250L, 0L, "499.0000", today.plusDays(4));
    }

    private void seedDemandChange(Supplier northstar, Supplier metrotech, Supplier apex) {
        Product headset = product(DEMAND_SKU, "USB Headset", "Stereo USB headset with microphone", "0.004000");
        Warehouse mum = warehouse(DEMAND_WAREHOUSE, "Mumbai Fulfillment Center", "Mumbai");
        SupplierProduct apexOffer = offer(apex, headset, mum, "AP-HS-003", 100L, 50L, 8, "849.0000");
        offer(northstar, headset, mum, "NS-HS-003", 100L, 50L, 7, "899.0000");
        offer(metrotech, headset, mum, "MT-HS-003", 50L, 25L, 3, "949.0000");
        inventory(headset, mum, 170L, 30L);
        policy(headset, mum, 80L, 7);
        // Older run generated 6 days ago under normal demand (about 12/day) ...
        forecastRun(headset, mum, BASELINE_FORECAST_RUN, "procureai-demo-v1", 6, 11, 3);
        // ... superseded by today's demand-sensing run after actual sales accelerated (about 30/day).
        forecastRun(headset, mum, SPIKE_FORECAST_RUN, "procureai-demand-sensing-v2", 0, 28, 5);
        budget(mum, "1000000.0000");
        capacity(mum, "80.000000", "52.000000");
        LocalDate today = today();
        // Existing PO sized for the old, normal-demand plan; fully confirmed by the supplier.
        purchaseOrder(DEMAND_PO, "scenario3-existing-po-mum-03", apex, mum, today.minusDays(6),
                PurchaseOrderStatus.ACKNOWLEDGED, apexOffer, 200L, 200L, 0L, "849.0000", today.plusDays(5));
    }

    private void seedPurchasingConstraint(Supplier northstar, Supplier metrotech) {
        Product monitor = product(CONSTRAINT_SKU, "27-inch Monitor", "27-inch IPS office monitor", "0.060000");
        Warehouse hyd = warehouse(CONSTRAINT_WAREHOUSE, "Hyderabad Fulfillment Center", "Hyderabad");
        offer(northstar, monitor, hyd, "NS-MN-004", 20L, 10L, 10, "11500.0000");
        offer(metrotech, monitor, hyd, "MT-MN-004", 10L, 5L, 6, "12400.0000");
        inventory(monitor, hyd, 40L, 5L);
        policy(monitor, hyd, 25L, 7);
        forecastRun(monitor, hyd, CONSTRAINT_FORECAST_RUN, "procureai-demo-v1", 0, 9, 3);
        // Budget covers roughly 130 units and free storage roughly 150 units: both below a 300-unit proposal.
        budget(hyd, "1500000.0000");
        capacity(hyd, "50.000000", "41.000000");
    }

    // ---- idempotent find-or-create helpers -------------------------------------------------------------

    private Supplier supplier(String code, String name) {
        return suppliers.findByCode(code).orElseGet(() ->
                suppliers.save(new Supplier(code, name, SupplierStatus.ACTIVE)));
    }

    private Product product(String sku, String name, String description, String unitVolumeM3) {
        return products.findBySku(sku).orElseGet(() ->
                products.save(new Product(sku, name, description, "EACH", new BigDecimal(unitVolumeM3), true)));
    }

    private Warehouse warehouse(String code, String name, String city) {
        return warehouses.findByCode(code).orElseGet(() ->
                warehouses.save(new Warehouse(code, name, city, "IN", true)));
    }

    private SupplierProduct offer(Supplier supplier, Product product, Warehouse warehouse, String supplierSku,
                                  Long moq, Long packSize, Integer leadTimeDays, String unitPrice) {
        return offers.findByProductAndWarehouse(product, warehouse).stream()
                .filter(offer -> offer.getSupplier().getId().equals(supplier.getId()))
                .findFirst()
                .orElseGet(() -> offers.save(new SupplierProduct(supplier, product, warehouse, supplierSku,
                        moq, packSize, leadTimeDays, new BigDecimal(unitPrice), "INR",
                        SupplierOfferStatus.AVAILABLE)));
    }

    private void inventory(Product product, Warehouse warehouse, Long onHand, Long reserved) {
        inventories.findByProductAndWarehouse(product, warehouse).orElseGet(() ->
                inventories.save(new Inventory(product, warehouse, onHand, reserved, now())));
    }

    private void policy(Product product, Warehouse warehouse, Long safetyStock, Integer reviewDays) {
        policies.findByProductAndWarehouse(product, warehouse).orElseGet(() ->
                policies.save(new ReplenishmentPolicy(product, warehouse, safetyStock, reviewDays)));
    }

    /** Seeds a 30-day forecast run (base + day % spread units/day), generated {@code ageDays} ago. */
    private void forecastRun(Product product, Warehouse warehouse, UUID runId, String modelVersion,
                             int ageDays, long baseUnits, int spread) {
        if (!forecasts.findByForecastRunId(runId).isEmpty()) return;
        LocalDateTime generatedAt = now().minusDays(ageDays);
        LocalDate first = today().plusDays(1);
        for (int day = 0; day < DemoDataSeeder.FORECAST_HORIZON_DAYS; day++) {
            forecasts.save(new DemandForecast(product, warehouse, first.plusDays(day),
                    baseUnits + (day % spread), runId, generatedAt, modelVersion));
        }
    }

    private void budget(Warehouse warehouse, String amount) {
        LocalDate start = LocalDate.of(today().getYear(), 1, 1);
        LocalDate end = LocalDate.of(today().getYear(), 12, 31);
        budgets.findByWarehouseAndPeriodStartAndPeriodEnd(warehouse, start, end).orElseGet(() ->
                budgets.save(new PurchasingBudget(warehouse, start, end, new BigDecimal(amount), "INR")));
    }

    private void capacity(Warehouse warehouse, String totalM3, String allocatedM3) {
        capacities.findByWarehouse(warehouse).orElseGet(() ->
                capacities.save(new StorageCapacity(warehouse, new BigDecimal(totalM3),
                        new BigDecimal(allocatedM3), now())));
    }

    private void purchaseOrder(String poNumber, String idempotencyKey, Supplier supplier, Warehouse warehouse,
                               LocalDate orderDate, PurchaseOrderStatus status, SupplierProduct offer,
                               Long ordered, Long confirmed, Long received, String unitPrice,
                               LocalDate expectedDelivery) {
        if (orders.findByPoNumber(poNumber).isPresent() || orders.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return;
        }
        PurchaseOrder order = orders.save(new PurchaseOrder(poNumber, supplier, warehouse, orderDate, status,
                "INR", idempotencyKey));
        orderItems.save(new PurchaseOrderItem(order, offer, ordered, confirmed, received,
                new BigDecimal(unitPrice), expectedDelivery));
    }

    private static LocalDate today() {
        return LocalDate.now(Clock.systemUTC());
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(Clock.systemUTC());
    }
}
