package com.procureai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.procureai.domain.entity.Product;
import com.procureai.domain.entity.PurchaseOrderItem;
import com.procureai.domain.entity.SupplierProduct;
import com.procureai.domain.entity.Warehouse;
import com.procureai.domain.enums.PurchaseOrderStatus;
import com.procureai.repository.PurchaseOrderItemRepository;
import com.procureai.repository.SupplierProductRepository;
import com.procureai.service.dto.PlanningHorizon;
import com.procureai.service.dto.PurchaseOrderEvidence;
import com.procureai.service.dto.PurchaseOrderLineEvidence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenPurchaseOrderInvestigationService {

    private static final Set<PurchaseOrderStatus> POTENTIAL_SUPPLY_STATUSES = Set.of(
            PurchaseOrderStatus.SUBMITTED,
            PurchaseOrderStatus.ACKNOWLEDGED,
            PurchaseOrderStatus.PARTIALLY_RECEIVED
    );
    private static final Set<PurchaseOrderStatus> CONFIRMED_SUPPLY_STATUSES = Set.of(
            PurchaseOrderStatus.ACKNOWLEDGED,
            PurchaseOrderStatus.PARTIALLY_RECEIVED
    );

    private final SupplierProductRepository supplierProductRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;

    public OpenPurchaseOrderInvestigationService(
            SupplierProductRepository supplierProductRepository,
            PurchaseOrderItemRepository purchaseOrderItemRepository
    ) {
        this.supplierProductRepository = supplierProductRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
    }

    @Transactional(readOnly = true)
    public PurchaseOrderEvidence investigate(Product product, Warehouse warehouse, PlanningHorizon horizon) {
        return investigate(product, warehouse, horizon,
                supplierProductRepository.findByProductAndWarehouse(product, warehouse));
    }

    @Transactional(readOnly = true)
    public PurchaseOrderEvidence investigate(
            Product product,
            Warehouse warehouse,
            PlanningHorizon horizon,
            List<SupplierProduct> offers
    ) {
        if (offers.isEmpty()) {
            return new PurchaseOrderEvidence(product.getSku(), warehouse.getCode(), horizon,
                    List.of(), 0L, 0L, true, List.of());
        }

        List<PurchaseOrderItem> items = purchaseOrderItemRepository.findBySupplierProductIn(offers);
        List<PurchaseOrderLineEvidence> lines = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        long confirmedIncoming = 0L;
        long uncertainPotential = 0L;
        boolean complete = true;

        for (PurchaseOrderItem item : items) {
            var order = item.getPurchaseOrder();
            SupplierProduct offer = item.getSupplierProduct();
            if (order == null) {
                complete = false;
                warnings.add("A relevant purchase-order line has no purchase-order header.");
                continue;
            }
            String warning = validateRelationships(item, order, offer, product, warehouse);
            if (warning == null) {
                warning = validateQuantities(item);
            }
            if (warning == null && POTENTIAL_SUPPLY_STATUSES.contains(order.getStatus())
                    && item.getExpectedDeliveryDate() == null) {
                warning = "Expected delivery date is missing; line cannot count within the planning horizon.";
            }
            boolean dueWithinHorizon = horizon.contains(item.getExpectedDeliveryDate());
            long confirmedOutstanding = 0L;
            long unconfirmedPotential = 0L;
            boolean statusCanSupply = order.getStatus() != null
                    && POTENTIAL_SUPPLY_STATUSES.contains(order.getStatus());

            if (warning == null && dueWithinHorizon && statusCanSupply) {
                Long confirmed = item.getConfirmedUnits();
                long received = item.getReceivedUnits();
                if (CONFIRMED_SUPPLY_STATUSES.contains(order.getStatus()) && confirmed != null) {
                    confirmedOutstanding = Math.max(confirmed - received, 0L);
                }
                long confirmedOrReceived = Math.max(confirmed == null ? 0L : confirmed, received);
                unconfirmedPotential = Math.max(item.getOrderedUnits() - confirmedOrReceived, 0L);
                if (order.getStatus() == PurchaseOrderStatus.SUBMITTED) {
                    confirmedOutstanding = 0L;
                }
            }
            if (warning != null) {
                complete = false;
                warnings.add(order.getPoNumber() + ": " + warning);
            }
            confirmedIncoming = Math.addExact(confirmedIncoming, confirmedOutstanding);
            uncertainPotential = Math.addExact(uncertainPotential, unconfirmedPotential);
            lines.add(new PurchaseOrderLineEvidence(
                    order.getPoNumber(),
                    order.getStatus(),
                    offer.getSupplier() == null ? null : offer.getSupplier().getCode(),
                    item.getExpectedDeliveryDate(),
                    dueWithinHorizon,
                    statusCanSupply,
                    item.getOrderedUnits() == null ? 0L : item.getOrderedUnits(),
                    item.getConfirmedUnits(),
                    item.getReceivedUnits() == null ? 0L : item.getReceivedUnits(),
                    confirmedOutstanding,
                    unconfirmedPotential,
                    warning
            ));
        }

        return new PurchaseOrderEvidence(product.getSku(), warehouse.getCode(), horizon,
                List.copyOf(lines), confirmedIncoming, uncertainPotential, complete, List.copyOf(warnings));
    }

    private String validateRelationships(PurchaseOrderItem item, com.procureai.domain.entity.PurchaseOrder order,
                                         SupplierProduct offer, Product product, Warehouse warehouse) {
        if (offer == null || order.getSupplier() == null || offer.getSupplier() == null
                || !Objects.equals(order.getSupplier().getId(), offer.getSupplier().getId())) {
            return "Purchase order supplier does not match the supplier offer.";
        }
        if (order.getWarehouse() == null || offer.getWarehouse() == null
                || !Objects.equals(order.getWarehouse().getId(), offer.getWarehouse().getId())
                || !Objects.equals(order.getWarehouse().getId(), warehouse.getId())) {
            return "Purchase order warehouse does not match the supplier offer.";
        }
        if (offer.getProduct() == null || !Objects.equals(product.getId(), offer.getProduct().getId())) {
            return "Supplier offer product does not match the investigated product.";
        }
        if (order.getStatus() == null) {
            return "Purchase order status is missing.";
        }
        return null;
    }

    private String validateQuantities(PurchaseOrderItem item) {
        Long ordered = item.getOrderedUnits();
        Long confirmed = item.getConfirmedUnits();
        Long received = item.getReceivedUnits();
        if (ordered == null || ordered <= 0 || received == null || received < 0 || received > ordered) {
            return "Ordered or received quantities are missing or inconsistent.";
        }
        if (confirmed != null && (confirmed < 0 || confirmed > ordered)) {
            return "Confirmed quantity is outside the ordered quantity range.";
        }
        return null;
    }
}
