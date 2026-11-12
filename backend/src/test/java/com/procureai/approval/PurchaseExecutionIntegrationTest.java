package com.procureai.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procureai.agent.dto.PurchasingRecommendation;
import com.procureai.approval.dto.ApprovePurchaseRequest;
import com.procureai.approval.dto.ApprovalResponse;
import com.procureai.approval.dto.RejectPurchaseRequest;
import com.procureai.approval.exception.ApprovalConflictException;
import com.procureai.approval.service.ApprovalService;
import com.procureai.approval.service.PurchaseExecutionService;
import com.procureai.domain.entity.AgentEvent;
import com.procureai.domain.entity.AgentRun;
import com.procureai.domain.entity.DemandForecast;
import com.procureai.domain.entity.Inventory;
import com.procureai.domain.entity.Product;
import com.procureai.domain.entity.PurchaseOrder;
import com.procureai.domain.entity.PurchaseOrderItem;
import com.procureai.domain.entity.PurchasingApproval;
import com.procureai.domain.entity.PurchasingBudget;
import com.procureai.domain.entity.StorageCapacity;
import com.procureai.domain.entity.SupplierProduct;
import com.procureai.domain.entity.Warehouse;
import com.procureai.domain.enums.AgentEventType;
import com.procureai.domain.enums.AgentRunStatus;
import com.procureai.domain.enums.ApprovalStatus;
import com.procureai.domain.enums.ExecutionStatus;
import com.procureai.domain.enums.PurchaseDecision;
import com.procureai.domain.enums.PurchaseOrderStatus;
import com.procureai.domain.enums.SupplierOfferStatus;
import com.procureai.repository.AgentEventRepository;
import com.procureai.repository.AgentRunRepository;
import com.procureai.repository.DemandForecastRepository;
import com.procureai.repository.InventoryRepository;
import com.procureai.repository.ProductRepository;
import com.procureai.repository.PurchaseOrderItemRepository;
import com.procureai.repository.PurchaseOrderRepository;
import com.procureai.repository.PurchasingApprovalRepository;
import com.procureai.repository.PurchasingBudgetRepository;
import com.procureai.repository.StorageCapacityRepository;
import com.procureai.repository.SupplierProductRepository;
import com.procureai.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PurchaseExecutionIntegrationTest {

    @Autowired private ApprovalService approvalService;
    @Autowired private PurchaseExecutionService executionService;
    @Autowired private AgentRunRepository runRepository;
    @Autowired private AgentEventRepository eventRepository;
    @Autowired private PurchasingApprovalRepository approvalRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private SupplierProductRepository offerRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private DemandForecastRepository forecastRepository;
    @Autowired private PurchasingBudgetRepository budgetRepository;
    @Autowired private StorageCapacityRepository storageRepository;
    @Autowired private PurchaseOrderRepository orderRepository;
    @Autowired private PurchaseOrderItemRepository itemRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private Clock clock;

    private Product product;
    private Warehouse warehouse;
    private SupplierProduct offer;
    private final List<UUID> testRunIds = new ArrayList<>();

    @BeforeEach
    void loadDemoRecords() {
        product = productRepository.findBySku("WM-001").orElseThrow();
        warehouse = warehouseRepository.findByCode("DEL-01").orElseThrow();
        offer = offerRepository.findByProductAndWarehouse(product, warehouse).stream()
                .filter(candidate -> candidate.getStatus() == SupplierOfferStatus.AVAILABLE)
                .findFirst().orElseThrow();
    }

    @AfterEach
    void removeTestRecords() {
        for (UUID runId : testRunIds) {
            AgentRun run = runRepository.findById(runId).orElse(null);
            if (run == null) continue;
            PurchasingApproval approval = approvalRepository.findByAgentRun(run).orElse(null);
            if (approval != null) {
                String key = approval.getIdempotencyKey();
                approvalRepository.delete(approval);
                approvalRepository.flush();
                if (key != null) {
                    PurchaseOrder order = orderRepository.findByIdempotencyKey(key).orElse(null);
                    if (order != null) {
                        itemRepository.deleteAll(itemRepository.findByPurchaseOrder(order));
                        orderRepository.delete(order);
                    }
                }
            }
            eventRepository.deleteAll(eventRepository.findByAgentRunOrderBySequenceNumberAsc(run));
            runRepository.delete(run);
        }
        testRunIds.clear();
    }

    @Test
    void explicitApprovalThenExecutionCreatesAndVerifiesOneInternalPo() {
        AgentRun run = completedRun(PurchaseDecision.MODIFY, 100L);
        ApprovalResponse approval = approve(run, offer, 100L);

        assertThat(approval.approvalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approval.executionStatus()).isEqualTo(ExecutionStatus.NOT_EXECUTED);
        assertThat(approval.purchaseOrderId()).isNull();
        assertThat(orderRepository.findByIdempotencyKey(approval.idempotencyKey())).isEmpty();
        assertThat(approvalService.get(approval.approvalId()).purchaseOrderId()).isNull();

        ApprovalResponse executed = executionService.execute(approval.approvalId());

        assertThat(executed.executionStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(executed.purchaseOrderId()).isNotNull();
        assertThat(executed.purchaseOrderNumber()).isEqualTo("PA-" + approval.approvalId());
        PurchaseOrder persisted = orderRepository.findById(executed.purchaseOrderId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(PurchaseOrderStatus.APPROVED);
        assertThat(persisted.getIdempotencyKey()).isEqualTo(approval.idempotencyKey());
        assertThat(persisted.getCurrencyCode()).isEqualTo(offer.getCurrencyCode());
        assertThat(persisted.getApprovedBy()).isEqualTo("Demo Buyer");
        assertThat(Math.abs(java.time.Duration.between(persisted.getApprovedAt(), approval.decisionAt()).toNanos()))
                .isLessThanOrEqualTo(1_000L); // H2 rounds LocalDateTime columns to microseconds.
        List<PurchaseOrderItem> lines = itemRepository.findByPurchaseOrder(persisted);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).getSupplierProduct().getId()).isEqualTo(offer.getId());
        assertThat(lines.get(0).getOrderedUnits()).isEqualTo(100L);
        assertThat(lines.get(0).getConfirmedUnits()).isNull();
        assertThat(lines.get(0).getReceivedUnits()).isZero();
        assertThat(lines.get(0).getUnitPriceAtOrder()).isEqualByComparingTo(offer.getUnitPrice());

        ApprovalResponse retry = executionService.execute(approval.approvalId());
        assertThat(retry.executionStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(retry.purchaseOrderId()).isEqualTo(executed.purchaseOrderId());
        assertThat(orderRepository.findByIdempotencyKey(approval.idempotencyKey()).orElseThrow().getId())
                .isEqualTo(persisted.getId());
        List<AgentEvent> events = eventRepository.findByAgentRunOrderBySequenceNumberAsc(run);
        assertThat(events).extracting(AgentEvent::getSequenceNumber).isSorted();
        assertThat(events).extracting(AgentEvent::getEventType)
                .contains(AgentEventType.APPROVAL_GRANTED, AgentEventType.REVALIDATION_PASSED,
                        AgentEventType.PO_CREATED, AgentEventType.PO_VERIFICATION_COMPLETED);
    }

    @ParameterizedTest
    @EnumSource(EvidenceChange.class)
    void anyRelevantEvidenceChangeAfterApprovalBlocksExecution(EvidenceChange change) {
        AgentRun run = completedRun(PurchaseDecision.ACCEPT, 100L);
        ApprovalResponse approval = approve(run, offer, 100L);
        BigDecimal oldPrice = offer.getUnitPrice();
        String oldCurrency = offer.getCurrencyCode();
        Long oldMoq = offer.getMinimumOrderQuantity();
        Long oldPack = offer.getPackSize();
        SupplierOfferStatus oldStatus = offer.getStatus();
        Inventory inventory = inventoryRepository.findByProductAndWarehouse(product, warehouse).orElseThrow();
        Long oldOnHand = inventory.getOnHandUnits();
        LocalDateTime oldCountedAt = inventory.getLastCountedAt();
        PurchasingBudget budget = budgetRepository.findByWarehouse(warehouse).get(0);
        BigDecimal oldBudget = budget.getBudgetAmount();
        StorageCapacity capacity = storageRepository.findByWarehouse(warehouse).orElseThrow();
        BigDecimal oldAllocated = capacity.getAllocatedVolumeM3();
        DemandForecast forecast = forecastRepository.findByProductAndWarehouse(product, warehouse).get(0);
        Long oldForecastUnits = forecast.getExpectedUnits();
        PurchaseOrder seedOrder = orderRepository.findByPoNumber("PO-DEL-DEMO-001").orElseThrow();
        PurchaseOrderItem seedLine = itemRepository.findByPurchaseOrder(seedOrder).get(0);
        Long oldReceivedUnits = seedLine.getReceivedUnits();

        try {
            switch (change) {
                case INVENTORY -> inventory.setOnHandUnits(oldOnHand - 10);
                case STALE_INVENTORY -> inventory.setLastCountedAt(LocalDateTime.now(clock).minusDays(3));
                case SUPPLIER_UNAVAILABLE -> offer.setStatus(SupplierOfferStatus.TEMPORARILY_UNAVAILABLE);
                case PRICE -> offer.setUnitPrice(oldPrice.add(BigDecimal.ONE));
                case CURRENCY -> offer.setCurrencyCode("USD");
                case BUDGET -> budget.setBudgetAmount(BigDecimal.ONE);
                case STORAGE -> capacity.setAllocatedVolumeM3(capacity.getTotalVolumeM3());
                case MOQ -> offer.setMinimumOrderQuantity(1000L);
                case PACK_SIZE -> offer.setPackSize(30L);
                case INCOMING_PO -> {
                    seedLine.setReceivedUnits(oldReceivedUnits + 10);
                    itemRepository.saveAndFlush(seedLine);
                }
                case FORECAST -> forecast.setExpectedUnits(oldForecastUnits + 1);
            }
            if (change == EvidenceChange.INVENTORY || change == EvidenceChange.STALE_INVENTORY) inventoryRepository.saveAndFlush(inventory);
            if (change == EvidenceChange.SUPPLIER_UNAVAILABLE || change == EvidenceChange.PRICE
                    || change == EvidenceChange.CURRENCY || change == EvidenceChange.MOQ || change == EvidenceChange.PACK_SIZE) {
                offerRepository.saveAndFlush(offer);
            }
            if (change == EvidenceChange.BUDGET) budgetRepository.saveAndFlush(budget);
            if (change == EvidenceChange.STORAGE) storageRepository.saveAndFlush(capacity);
            if (change == EvidenceChange.FORECAST) forecastRepository.saveAndFlush(forecast);

            ApprovalResponse blocked = executionService.execute(approval.approvalId());

            assertThat(blocked.executionStatus()).isEqualTo(ExecutionStatus.BLOCKED);
            assertThat(blocked.executionSummary()).contains("changed after approval");
            assertThat(orderRepository.findByIdempotencyKey(approval.idempotencyKey())).isEmpty();
        } finally {
            offer.setUnitPrice(oldPrice);
            offer.setCurrencyCode(oldCurrency);
            offer.setMinimumOrderQuantity(oldMoq);
            offer.setPackSize(oldPack);
            offer.setStatus(oldStatus);
            offerRepository.saveAndFlush(offer);
            Inventory inventoryToRestore = inventoryRepository.findByProductAndWarehouse(product, warehouse).orElseThrow();
            inventoryToRestore.setOnHandUnits(oldOnHand);
            inventoryToRestore.setLastCountedAt(oldCountedAt);
            inventoryRepository.saveAndFlush(inventoryToRestore);
            budget.setBudgetAmount(oldBudget);
            budgetRepository.saveAndFlush(budget);
            StorageCapacity capacityToRestore = storageRepository.findByWarehouse(warehouse).orElseThrow();
            capacityToRestore.setAllocatedVolumeM3(oldAllocated);
            storageRepository.saveAndFlush(capacityToRestore);
            forecast.setExpectedUnits(oldForecastUnits);
            forecastRepository.saveAndFlush(forecast);
            seedLine.setReceivedUnits(oldReceivedUnits);
            itemRepository.saveAndFlush(seedLine);
        }
    }

    @Test
    void invalidRecommendationDuplicateDecisionAndMissingApprovalFieldsAreRejected() {
        AgentRun actionable = completedRun(PurchaseDecision.MODIFY, 100L);
        assertThatThrownBy(() -> approvalService.approve(actionable.getId(),
                new ApprovePurchaseRequest(99L, offer.getId(), "Buyer")))
                .isInstanceOf(ApprovalConflictException.class).hasMessageContaining("exactly match");
        assertThatThrownBy(() -> approvalService.approve(actionable.getId(),
                new ApprovePurchaseRequest(100L, 9999999L, "Buyer")))
                .isInstanceOf(ApprovalConflictException.class).hasMessageContaining("does not exist");
        assertThatThrownBy(() -> approvalService.approve(actionable.getId(),
                new ApprovePurchaseRequest(100L, offer.getId(), " ")))
                .isInstanceOf(ApprovalConflictException.class).hasMessageContaining("identity is required");

        AgentRun rejectedRecommendation = completedRun(PurchaseDecision.REJECT, null);
        assertThatThrownBy(() -> approvalService.approve(rejectedRecommendation.getId(),
                new ApprovePurchaseRequest(100L, offer.getId(), "Buyer")))
                .isInstanceOf(ApprovalConflictException.class).hasMessageContaining("ACCEPT or MODIFY");
        AgentRun investigateRecommendation = completedRun(PurchaseDecision.INVESTIGATE, null);
        assertThatThrownBy(() -> approvalService.approve(investigateRecommendation.getId(),
                new ApprovePurchaseRequest(100L, offer.getId(), "Buyer")))
                .isInstanceOf(ApprovalConflictException.class).hasMessageContaining("ACCEPT or MODIFY");

        ApprovalResponse rejected = approvalService.reject(actionable.getId(),
                new RejectPurchaseRequest("Buyer", "Do not proceed."));
        assertThat(rejected.approvalStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThatThrownBy(() -> approvalService.approve(actionable.getId(),
                new ApprovePurchaseRequest(100L, offer.getId(), "Buyer")))
                .isInstanceOf(ApprovalConflictException.class).hasMessageContaining("already has");
        assertThatThrownBy(() -> executionService.execute(rejected.approvalId()))
                .isInstanceOf(ApprovalConflictException.class).hasMessageContaining("rejected");
        assertThat(approvalService.get(rejected.approvalId()).rejectionReason()).isEqualTo("Do not proceed.");
    }

    @Test
    void concurrentExecutionRequestsCreateOnlyOnePurchaseOrder() throws Exception {
        AgentRun run = completedRun(PurchaseDecision.MODIFY, 100L);
        ApprovalResponse approval = approve(run, offer, 100L);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> executeTogether(approval.approvalId(), ready, start));
            var second = pool.submit(() -> executeTogether(approval.approvalId(), ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            ApprovalResponse firstResult = first.get(30, TimeUnit.SECONDS);
            ApprovalResponse secondResult = second.get(30, TimeUnit.SECONDS);
            assertThat(firstResult.executionStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
            assertThat(secondResult.executionStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
            assertThat(firstResult.purchaseOrderId()).isEqualTo(secondResult.purchaseOrderId());
            assertThat(orderRepository.findByIdempotencyKey(approval.idempotencyKey())).isPresent();
            assertThat(itemRepository.findByPurchaseOrder(orderRepository
                    .findByIdempotencyKey(approval.idempotencyKey()).orElseThrow())).hasSize(1);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    private ApprovalResponse executeTogether(java.util.UUID approvalId, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Execution start timed out.");
            return executionService.execute(approvalId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private ApprovalResponse approve(AgentRun run, SupplierProduct selectedOffer, long quantity) {
        return approvalService.approve(run.getId(),
                new ApprovePurchaseRequest(quantity, selectedOffer.getId(), "Demo Buyer"));
    }

    private AgentRun completedRun(PurchaseDecision decision, Long proposedQuantity) {
        LocalDateTime now = LocalDateTime.now(clock);
        AgentRun run = runRepository.saveAndFlush(new AgentRun(product, warehouse, 800L,
                decision == PurchaseDecision.INVESTIGATE ? AgentRunStatus.NEEDS_INPUT : AgentRunStatus.COMPLETED,
                decision, proposedQuantity, "Persisted demo recommendation.", now, now));
        testRunIds.add(run.getId());
        LocalDate start = LocalDate.now(clock).plusDays(1);
        LocalDate end = start.plusDays(9);
        try {
            eventRepository.saveAndFlush(new AgentEvent(run, 1, AgentEventType.RUN_STARTED, null,
                    objectMapper.writeValueAsString(Map.of("productSku", product.getSku(),
                            "warehouseCode", warehouse.getCode(), "recommendedPurchaseUnits", 800,
                            "planningStartDate", start.toString(), "planningEndDate", end.toString())),
                    null, "Agent investigation started.", now));
            PurchasingRecommendation recommendation = new PurchasingRecommendation(decision, 800L, proposedQuantity,
                    null, null, "Human-reviewable recommendation.", List.of(), List.of("Test evidence."),
                    List.of(), List.of(), List.of(), new BigDecimal("0.8"));
            eventRepository.saveAndFlush(new AgentEvent(run, 2, AgentEventType.DECISION_PROPOSED, null,
                    null, objectMapper.writeValueAsString(recommendation), "Recommendation returned by the agent.", now));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        return run;
    }

    enum EvidenceChange {
        INVENTORY, STALE_INVENTORY, SUPPLIER_UNAVAILABLE, PRICE, CURRENCY,
        BUDGET, STORAGE, MOQ, PACK_SIZE, INCOMING_PO, FORECAST
    }
}
