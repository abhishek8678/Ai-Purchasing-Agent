package com.procureai.approval.service;

import java.util.ArrayList;
import java.util.List;

import com.procureai.domain.entity.PurchaseOrder;
import com.procureai.domain.entity.PurchaseOrderItem;
import com.procureai.domain.entity.PurchasingApproval;
import com.procureai.domain.enums.PurchaseOrderStatus;
import org.springframework.stereotype.Component;

/** Compares only the database read-back against the immutable human-approved plan. */
@Component
public class PurchaseOrderReadBackVerifier {

    public Verification verify(PurchasingApproval approval, PurchaseOrder order, List<PurchaseOrderItem> lines) {
        List<String> discrepancies = new ArrayList<>();
        if (order == null) {
            discrepancies.add("Persisted purchase order was not found by its database identifier.");
            return new Verification(false, List.copyOf(discrepancies));
        }
        if (!("PA-" + approval.getId()).equals(order.getPoNumber())) discrepancies.add("PO number differs from the approved execution reference.");
        if (order.getSupplier() == null || approval.getSupplierProduct() == null
                || approval.getSupplierProduct().getSupplier() == null
                || !order.getSupplier().getId().equals(approval.getSupplierProduct().getSupplier().getId())) {
            discrepancies.add("Supplier differs from the approved supplier offer.");
        }
        if (order.getWarehouse() == null || approval.getAgentRun().getWarehouse() == null
                || !order.getWarehouse().getId().equals(approval.getAgentRun().getWarehouse().getId())) {
            discrepancies.add("Warehouse differs from the approved run.");
        }
        if (order.getStatus() != PurchaseOrderStatus.APPROVED) discrepancies.add("PO status is not APPROVED.");
        if (!approval.getCurrencyCode().equals(order.getCurrencyCode())) discrepancies.add("PO currency differs from the approved currency.");
        if (!approval.getIdempotencyKey().equals(order.getIdempotencyKey())) discrepancies.add("PO idempotency key differs from the approval key.");
        if (lines.size() != 1) {
            discrepancies.add("PO does not contain exactly one persisted line.");
        } else {
            PurchaseOrderItem line = lines.get(0);
            if (line.getSupplierProduct() == null
                    || !line.getSupplierProduct().getId().equals(approval.getSupplierProduct().getId())) {
                discrepancies.add("PO line offer differs from the approved supplier offer.");
            } else if (line.getSupplierProduct().getProduct() == null
                    || !line.getSupplierProduct().getProduct().getId().equals(approval.getAgentRun().getProduct().getId())) {
                discrepancies.add("PO line product differs from the approved product.");
            }
            if (!approval.getApprovedQuantity().equals(line.getOrderedUnits())) discrepancies.add("PO line quantity differs from the approved quantity.");
            if (line.getUnitPriceAtOrder() == null
                    || line.getUnitPriceAtOrder().compareTo(approval.getUnitPriceAtApproval()) != 0) {
                discrepancies.add("PO line order-time unit price differs from the approved price.");
            } else if (line.getUnitPriceAtOrder().signum() < 0) {
                discrepancies.add("PO line order-time unit price is invalid.");
            }
        }
        return new Verification(discrepancies.isEmpty(), List.copyOf(discrepancies));
    }

    public record Verification(boolean verified, List<String> discrepancies) { }
}
