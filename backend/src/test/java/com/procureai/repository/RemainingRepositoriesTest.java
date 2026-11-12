package com.procureai.repository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.procureai.domain.entity.AgentEvent;
import com.procureai.domain.entity.AgentRun;
import com.procureai.domain.entity.DemandForecast;
import com.procureai.domain.entity.Product;
import com.procureai.domain.entity.PurchaseOrder;
import com.procureai.domain.entity.PurchaseOrderItem;
import com.procureai.domain.entity.PurchasingBudget;
import com.procureai.domain.entity.StorageCapacity;
import com.procureai.domain.entity.Supplier;
import com.procureai.domain.entity.SupplierProduct;
import com.procureai.domain.entity.Warehouse;
import com.procureai.domain.enums.AgentEventType;
import com.procureai.domain.enums.AgentRunStatus;
import com.procureai.domain.enums.PurchaseDecision;
import com.procureai.domain.enums.PurchaseOrderStatus;
import com.procureai.domain.enums.SupplierOfferStatus;
import com.procureai.domain.enums.SupplierStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class RemainingRepositoriesTest {

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

    @Autowired
    private AgentEventRepository agentEventRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private SupplierProductRepository supplierProductRepository;

    @Test
    void demandForecastRepositoryFindsByProductWarehouseDateAndRunAndOrdersByDate() {
        Product mouse = saveProduct("SKU-REMAINING-FORECAST-MOUSE");
        Product keyboard = saveProduct("SKU-REMAINING-FORECAST-KEYBOARD");
        Warehouse delhi = saveWarehouse("WH-REMAINING-FORECAST-DELHI");
        LocalDate firstDay = LocalDate.of(2026, 11, 10);
        LocalDate secondDay = LocalDate.of(2026, 11, 11);
        UUID runId = UUID.randomUUID();
        DemandForecast second = saveForecast(mouse, delhi, secondDay, runId);
        DemandForecast first = saveForecast(mouse, delhi, firstDay, runId);
        saveForecast(keyboard, delhi, firstDay, runId);

        assertEquals(2, demandForecastRepository.findByProductAndWarehouse(mouse, delhi).size());
        assertEquals(List.of(first), demandForecastRepository.findByProductAndWarehouseAndForecastDate(
                mouse, delhi, firstDay));
        assertEquals(3, demandForecastRepository.findByForecastRunId(runId).size());
        assertEquals(List.of(first.getForecastDate(), second.getForecastDate()),
                demandForecastRepository.findByProductAndWarehouseOrderByForecastDateAsc(mouse, delhi)
                        .stream().map(DemandForecast::getForecastDate).toList());
        assertTrue(demandForecastRepository.findByForecastRunId(UUID.randomUUID()).isEmpty());
    }

    @Test
    void purchasingBudgetRepositoryFindsByWarehouseAndUniquePeriod() {
        Warehouse delhi = saveWarehouse("WH-REMAINING-BUDGET-DELHI");
        Warehouse mumbai = saveWarehouse("WH-REMAINING-BUDGET-MUMBAI");
        LocalDate start = LocalDate.of(2026, 11, 1);
        LocalDate end = LocalDate.of(2026, 11, 30);
        PurchasingBudget selected = saveBudget(delhi, start, end);
        saveBudget(delhi, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31));
        saveBudget(mumbai, start, end);

        assertEquals(2, purchasingBudgetRepository.findByWarehouse(delhi).size());
        assertEquals(selected, purchasingBudgetRepository.findByWarehouseAndPeriodStartAndPeriodEnd(
                delhi, start, end).orElseThrow());
        assertTrue(purchasingBudgetRepository.findByWarehouseAndPeriodStartAndPeriodEnd(
                delhi, start, LocalDate.of(2026, 11, 29)).isEmpty());
    }

    @Test
    void storageCapacityRepositoryReturnsOptionalCapacityByWarehouse() {
        Warehouse delhi = saveWarehouse("WH-REMAINING-CAPACITY-DELHI");
        Warehouse mumbai = saveWarehouse("WH-REMAINING-CAPACITY-MUMBAI");
        StorageCapacity capacity = storageCapacityRepository.saveAndFlush(new StorageCapacity(
                delhi, new BigDecimal("100.000000"), new BigDecimal("35.000000"), LocalDateTime.now()));

        assertEquals(capacity, storageCapacityRepository.findByWarehouse(delhi).orElseThrow());
        assertTrue(storageCapacityRepository.findByWarehouse(mumbai).isEmpty());
    }

    @Test
    void purchaseOrderRepositoryFindsUniqueKeysFiltersAndOrdersRecentFirst() {
        Supplier acme = saveSupplier("SUP-REMAINING-PO-ACME");
        Supplier other = saveSupplier("SUP-REMAINING-PO-OTHER");
        Warehouse delhi = saveWarehouse("WH-REMAINING-PO-DELHI");
        Warehouse mumbai = saveWarehouse("WH-REMAINING-PO-MUMBAI");
        PurchaseOrder older = saveOrder("PO-REMAINING-OLDER", acme, delhi,
                LocalDate.of(2026, 11, 1), PurchaseOrderStatus.SUBMITTED, "idem-older");
        PurchaseOrder newer = saveOrder("PO-REMAINING-NEWER", acme, delhi,
                LocalDate.of(2026, 11, 3), PurchaseOrderStatus.SUBMITTED, "idem-newer");
        PurchaseOrder otherSupplier = saveOrder("PO-REMAINING-OTHER", other, delhi,
                LocalDate.of(2026, 11, 2), PurchaseOrderStatus.DRAFT, null);
        PurchaseOrder mumbaiOrder = saveOrder("PO-REMAINING-MUMBAI", acme, mumbai,
                LocalDate.of(2026, 11, 4), PurchaseOrderStatus.SUBMITTED, null);

        assertEquals(newer, purchaseOrderRepository.findByPoNumber(newer.getPoNumber()).orElseThrow());
        assertEquals(older, purchaseOrderRepository.findByIdempotencyKey("idem-older").orElseThrow());
        assertTrue(purchaseOrderRepository.findByPoNumber("PO-REMAINING-MISSING").isEmpty());
        assertTrue(purchaseOrderRepository.findByIdempotencyKey("idem-missing").isEmpty());
        assertEquals(List.of(older.getId(), newer.getId()), purchaseOrderRepository.findByWarehouseAndStatus(
                delhi, PurchaseOrderStatus.SUBMITTED).stream().map(PurchaseOrder::getId).sorted().toList());
        assertEquals(List.of(older.getId(), newer.getId(), mumbaiOrder.getId()).stream().sorted().toList(),
                purchaseOrderRepository.findBySupplierAndStatus(acme, PurchaseOrderStatus.SUBMITTED).stream()
                        .map(PurchaseOrder::getId).sorted().toList());
        assertEquals(List.of("PO-REMAINING-MUMBAI", newer.getPoNumber(), otherSupplier.getPoNumber(),
                        older.getPoNumber()),
                purchaseOrderRepository.findAllByOrderByOrderDateDesc().stream()
                        .map(PurchaseOrder::getPoNumber).toList());
    }

    @Test
    void purchaseOrderItemRepositoryFindsByOrderOfferAndDeliveryDate() {
        Supplier supplier = saveSupplier("SUP-REMAINING-ITEM");
        Warehouse delhi = saveWarehouse("WH-REMAINING-ITEM-DELHI");
        Product mouse = saveProduct("SKU-REMAINING-ITEM-MOUSE");
        Product keyboard = saveProduct("SKU-REMAINING-ITEM-KEYBOARD");
        SupplierProduct mouseOffer = saveOffer(supplier, mouse, delhi);
        SupplierProduct keyboardOffer = saveOffer(supplier, keyboard, delhi);
        PurchaseOrder firstOrder = saveOrder("PO-REMAINING-ITEM-1", supplier, delhi,
                LocalDate.of(2026, 11, 1), PurchaseOrderStatus.SUBMITTED, null);
        PurchaseOrder secondOrder = saveOrder("PO-REMAINING-ITEM-2", supplier, delhi,
                LocalDate.of(2026, 11, 2), PurchaseOrderStatus.ACKNOWLEDGED, null);
        LocalDate deliveryDate = LocalDate.of(2026, 11, 12);
        PurchaseOrderItem first = saveItem(firstOrder, mouseOffer, deliveryDate);
        PurchaseOrderItem second = saveItem(secondOrder, mouseOffer, deliveryDate.plusDays(1));
        PurchaseOrderItem otherOffer = saveItem(firstOrder, keyboardOffer, deliveryDate);

        assertEquals(List.of(first.getId(), otherOffer.getId()), purchaseOrderItemRepository
                .findByPurchaseOrder(firstOrder).stream().map(PurchaseOrderItem::getId).sorted().toList());
        assertEquals(List.of(first.getId(), second.getId()), purchaseOrderItemRepository
                .findBySupplierProduct(mouseOffer).stream().map(PurchaseOrderItem::getId).sorted().toList());
        assertEquals(List.of(first), purchaseOrderItemRepository.findBySupplierProductAndExpectedDeliveryDate(
                mouseOffer, deliveryDate));
        assertTrue(purchaseOrderItemRepository.findBySupplierProductAndExpectedDeliveryDate(
                mouseOffer, LocalDate.of(2026, 12, 1)).isEmpty());
    }

    @Test
    void agentRunRepositoryFindsByIdContextStatusAndRecentStartTime() {
        Product mouse = saveProduct("SKU-REMAINING-RUN-MOUSE");
        Product keyboard = saveProduct("SKU-REMAINING-RUN-KEYBOARD");
        Warehouse delhi = saveWarehouse("WH-REMAINING-RUN-DELHI");
        Warehouse mumbai = saveWarehouse("WH-REMAINING-RUN-MUMBAI");
        AgentRun older = saveRun(mouse, delhi, AgentRunStatus.COMPLETED,
                LocalDateTime.of(2026, 11, 1, 10, 0));
        AgentRun newer = saveRun(mouse, delhi, AgentRunStatus.RUNNING,
                LocalDateTime.of(2026, 11, 3, 10, 0));
        saveRun(keyboard, delhi, AgentRunStatus.COMPLETED, LocalDateTime.of(2026, 11, 2, 10, 0));
        saveRun(mouse, mumbai, AgentRunStatus.FAILED, LocalDateTime.of(2026, 11, 4, 10, 0));

        assertEquals(older, agentRunRepository.findById(older.getId()).orElseThrow());
        assertEquals(List.of(older, newer), agentRunRepository.findByProductAndWarehouse(mouse, delhi)
                .stream().sorted((a, b) -> a.getStartedAt().compareTo(b.getStartedAt())).toList());
        assertEquals(2, agentRunRepository.findByStatus(AgentRunStatus.COMPLETED).size());
        assertEquals(List.of(LocalDateTime.of(2026, 11, 4, 10, 0),
                        LocalDateTime.of(2026, 11, 3, 10, 0),
                        LocalDateTime.of(2026, 11, 2, 10, 0),
                        LocalDateTime.of(2026, 11, 1, 10, 0)),
                agentRunRepository.findAllByOrderByStartedAtDesc().stream()
                        .map(AgentRun::getStartedAt).toList());
        assertTrue(agentRunRepository.findByProductAndWarehouse(keyboard, mumbai).isEmpty());
    }

    @Test
    void agentEventRepositoryFindsRunEventsInSequenceAndByType() {
        Product product = saveProduct("SKU-REMAINING-EVENT");
        Warehouse warehouse = saveWarehouse("WH-REMAINING-EVENT");
        AgentRun run = saveRun(product, warehouse, AgentRunStatus.RUNNING,
                LocalDateTime.of(2026, 11, 1, 10, 0));
        AgentRun otherRun = saveRun(product, warehouse, AgentRunStatus.RUNNING,
                LocalDateTime.of(2026, 11, 1, 11, 0));
        AgentEvent second = saveEvent(run, 2, AgentEventType.TOOL_RESULT);
        AgentEvent first = saveEvent(run, 1, AgentEventType.TOOL_REQUESTED);
        saveEvent(otherRun, 1, AgentEventType.TOOL_RESULT);

        assertEquals(2, agentEventRepository.findByAgentRun(run).size());
        assertEquals(List.of(first, second), agentEventRepository.findByAgentRunOrderBySequenceNumberAsc(run));
        assertEquals(2, agentEventRepository.findByEventType(AgentEventType.TOOL_RESULT).size());
        assertTrue(agentEventRepository.findByAgentRun(saveRun(
                product, warehouse, AgentRunStatus.COMPLETED,
                LocalDateTime.of(2026, 11, 1, 12, 0))).isEmpty());
    }

    private Product saveProduct(String sku) {
        return productRepository.saveAndFlush(new Product(sku, sku, null, "EACH", null, true));
    }

    private Warehouse saveWarehouse(String code) {
        return warehouseRepository.saveAndFlush(new Warehouse(code, code, "Delhi", "IN", true));
    }

    private Supplier saveSupplier(String code) {
        return supplierRepository.saveAndFlush(new Supplier(code, code, SupplierStatus.ACTIVE));
    }

    private SupplierProduct saveOffer(Supplier supplier, Product product, Warehouse warehouse) {
        return supplierProductRepository.saveAndFlush(new SupplierProduct(supplier, product, warehouse,
                "SKU-" + product.getSku(), 1L, 1L, 1, BigDecimal.ONE, "INR", SupplierOfferStatus.AVAILABLE));
    }

    private DemandForecast saveForecast(Product product, Warehouse warehouse, LocalDate date, UUID runId) {
        return demandForecastRepository.saveAndFlush(new DemandForecast(product, warehouse, date,
                12L, runId, LocalDateTime.of(2026, 11, 1, 9, 0), "repo-test"));
    }

    private PurchasingBudget saveBudget(Warehouse warehouse, LocalDate start, LocalDate end) {
        return purchasingBudgetRepository.saveAndFlush(new PurchasingBudget(
                warehouse, start, end, new BigDecimal("1000.0000"), "INR"));
    }

    private PurchaseOrder saveOrder(
            String number, Supplier supplier, Warehouse warehouse, LocalDate date,
            PurchaseOrderStatus status, String idempotencyKey
    ) {
        return purchaseOrderRepository.saveAndFlush(new PurchaseOrder(
                number, supplier, warehouse, date, status, "INR", idempotencyKey));
    }

    private PurchaseOrderItem saveItem(PurchaseOrder order, SupplierProduct offer, LocalDate deliveryDate) {
        return purchaseOrderItemRepository.saveAndFlush(new PurchaseOrderItem(
                order, offer, 10L, null, 0L, new BigDecimal("10.2500"), deliveryDate));
    }

    private AgentRun saveRun(Product product, Warehouse warehouse, AgentRunStatus status, LocalDateTime startedAt) {
        return agentRunRepository.saveAndFlush(new AgentRun(product, warehouse, 800L, status,
                status == AgentRunStatus.COMPLETED ? PurchaseDecision.ACCEPT : null,
                status == AgentRunStatus.COMPLETED ? 800L : null,
                "Repository test run.", startedAt, null));
    }

    private AgentEvent saveEvent(AgentRun run, int sequence, AgentEventType type) {
        return agentEventRepository.saveAndFlush(new AgentEvent(run, sequence, type,
                null, null, null, "Repository test event.", LocalDateTime.now(Clock.systemUTC())));
    }

}
