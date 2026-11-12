package com.procureai.readmodel.service;

import java.util.List;

import com.procureai.agent.dto.AgentRunResponse;
import com.procureai.agent.service.AgentRunQueryService;
import com.procureai.approval.dto.ApprovalResponse;
import com.procureai.domain.entity.AgentRun;
import com.procureai.domain.entity.PurchaseOrder;
import com.procureai.domain.entity.PurchaseOrderItem;
import com.procureai.domain.entity.SupplierProduct;
import com.procureai.readmodel.dto.ApprovalQueueResponse;
import com.procureai.readmodel.dto.CatalogProductResponse;
import com.procureai.readmodel.dto.InventoryRowResponse;
import com.procureai.readmodel.dto.PurchaseOrderItemResponse;
import com.procureai.readmodel.dto.PurchaseOrderResponse;
import com.procureai.readmodel.dto.SupplierOfferResponse;
import com.procureai.readmodel.dto.WarehouseResponse;
import com.procureai.repository.AgentRunRepository;
import com.procureai.repository.InventoryRepository;
import com.procureai.repository.ProductRepository;
import com.procureai.repository.PurchaseOrderItemRepository;
import com.procureai.repository.PurchaseOrderRepository;
import com.procureai.repository.PurchasingApprovalRepository;
import com.procureai.repository.SupplierProductRepository;
import com.procureai.repository.WarehouseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** DTO-only read models used by the buyer dashboard. */
@Service
public class ProcurementReadService {
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryRepository inventoryRepository;
    private final SupplierProductRepository offerRepository;
    private final PurchaseOrderRepository orderRepository;
    private final PurchaseOrderItemRepository itemRepository;
    private final AgentRunRepository runRepository;
    private final PurchasingApprovalRepository approvalRepository;
    private final AgentRunQueryService runQueryService;

    public ProcurementReadService(ProductRepository productRepository, WarehouseRepository warehouseRepository,
                                  InventoryRepository inventoryRepository, SupplierProductRepository offerRepository,
                                  PurchaseOrderRepository orderRepository, PurchaseOrderItemRepository itemRepository,
                                  AgentRunRepository runRepository, PurchasingApprovalRepository approvalRepository,
                                  AgentRunQueryService runQueryService) {
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.inventoryRepository = inventoryRepository;
        this.offerRepository = offerRepository;
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.runRepository = runRepository;
        this.approvalRepository = approvalRepository;
        this.runQueryService = runQueryService;
    }

    @Transactional(readOnly = true)
    public List<CatalogProductResponse> products() {
        return productRepository.findAll().stream().map(p -> new CatalogProductResponse(
                p.getId(), p.getSku(), p.getName(), p.getUnitOfMeasure(), p.getUnitVolumeM3(), p.getActive())).toList();
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> warehouses() {
        return warehouseRepository.findAll().stream().map(w -> new WarehouseResponse(
                w.getId(), w.getCode(), w.getName(), w.getCity(), w.getCountryCode(), w.getActive())).toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryRowResponse> inventory() {
        return inventoryRepository.findAll().stream().map(i -> new InventoryRowResponse(i.getId(),
                i.getProduct().getId(), i.getProduct().getSku(), i.getProduct().getName(),
                i.getProduct().getUnitOfMeasure(), i.getWarehouse().getId(), i.getWarehouse().getCode(),
                i.getWarehouse().getName(), i.getOnHandUnits(), i.getReservedUnits(),
                i.getOnHandUnits() - i.getReservedUnits(), i.getLastCountedAt())).toList();
    }

    @Transactional(readOnly = true)
    public List<SupplierOfferResponse> offers(String productSku, String warehouseCode) {
        var product = productRepository.findBySku(productSku)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown product SKU: " + productSku));
        var warehouse = warehouseRepository.findByCode(warehouseCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown warehouse code: " + warehouseCode));
        return offerRepository.findByProductAndWarehouse(product, warehouse).stream().map(this::offerResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AgentRunResponse> recentRuns() {
        return runRepository.findAllByOrderByStartedAtDesc().stream().limit(50)
                .map(run -> runQueryService.getRun(run.getId())).toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalQueueResponse> approvals() {
        return runRepository.findAllByOrderByStartedAtDesc().stream().limit(100).map(run -> {
            AgentRunResponse runResponse = runQueryService.getRun(run.getId());
            ApprovalResponse approvalResponse = approvalRepository.findByAgentRun(run)
                    .map(a -> new ApprovalResponse(a.getId(), run.getId(), a.getApprovalStatus(), a.getExecutionStatus(),
                            a.getApprovedQuantity(), a.getSupplierProduct() == null ? null : a.getSupplierProduct().getId(),
                            a.getSupplierProduct() == null ? null : a.getSupplierProduct().getSupplier().getId(),
                            a.getSupplierProduct() == null ? null : a.getSupplierProduct().getSupplier().getCode(),
                            a.getApproverIdentity(), a.getDecisionAt(), a.getRejectionReason(), a.getIdempotencyKey(),
                            a.getPurchaseOrder() == null ? null : a.getPurchaseOrder().getId(),
                            a.getPurchaseOrder() == null ? null : a.getPurchaseOrder().getPoNumber(),
                            a.getExecutionSummary(), a.getExecutionAt())).orElse(null);
            return new ApprovalQueueResponse(runResponse, approvalResponse);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderResponse> purchaseOrders() {
        return orderRepository.findAllByOrderByOrderDateDesc().stream().map(this::orderResponse).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse purchaseOrder(Long id) {
        return orderResponse(orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase order " + id + " was not found.")));
    }

    private SupplierOfferResponse offerResponse(SupplierProduct offer) {
        return new SupplierOfferResponse(offer.getId(), offer.getSupplier().getId(), offer.getSupplier().getCode(),
                offer.getSupplier().getName(), offer.getProduct().getId(), offer.getProduct().getSku(),
                offer.getWarehouse().getId(), offer.getWarehouse().getCode(), offer.getStatus().name(),
                offer.getMinimumOrderQuantity(), offer.getPackSize(), offer.getLeadTimeDays(),
                offer.getUnitPrice(), offer.getCurrencyCode());
    }

    private PurchaseOrderResponse orderResponse(PurchaseOrder order) {
        List<PurchaseOrderItemResponse> items = itemRepository.findByPurchaseOrder(order).stream()
                .map(this::itemResponse).toList();
        return new PurchaseOrderResponse(order.getId(), order.getPoNumber(), order.getSupplier().getId(),
                order.getSupplier().getCode(), order.getSupplier().getName(), order.getWarehouse().getId(),
                order.getWarehouse().getCode(), order.getWarehouse().getName(), order.getOrderDate(),
                order.getStatus().name(), order.getCurrencyCode(), order.getApprovedBy(), order.getApprovedAt(), items);
    }

    private PurchaseOrderItemResponse itemResponse(PurchaseOrderItem item) {
        var product = item.getSupplierProduct().getProduct();
        return new PurchaseOrderItemResponse(item.getId(), item.getSupplierProduct().getId(), product.getSku(),
                product.getName(), product.getUnitOfMeasure(), item.getOrderedUnits(), item.getConfirmedUnits(),
                item.getReceivedUnits(), item.getUnitPriceAtOrder(), item.getExpectedDeliveryDate());
    }
}
