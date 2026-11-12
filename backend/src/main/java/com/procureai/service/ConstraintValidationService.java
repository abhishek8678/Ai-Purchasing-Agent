package com.procureai.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.procureai.service.dto.BudgetEvidence;
import com.procureai.service.dto.ConstraintEvaluation;
import com.procureai.service.dto.ConstraintResult;
import com.procureai.service.dto.ConstraintStatus;
import com.procureai.service.dto.ForecastEvidence;
import com.procureai.service.dto.InventoryEvidence;
import com.procureai.service.dto.PurchasingEvidenceSnapshot;
import com.procureai.service.dto.StorageEvidence;
import com.procureai.service.dto.SupplierOfferEvidence;
import org.springframework.stereotype.Service;

/** Evaluates explicit hard constraints without producing or modifying a purchase decision. */
@Service
public class ConstraintValidationService {

    public ConstraintEvaluation evaluate(
            PurchasingEvidenceSnapshot snapshot,
            Long selectedSupplierProductId,
            Long proposedUnits
    ) {
        List<ConstraintResult> results = new ArrayList<>();
        SupplierOfferEvidence offer = snapshot.suppliers() == null ? null : snapshot.suppliers().offers().stream()
                .filter(candidate -> candidate.supplierProductId().equals(selectedSupplierProductId))
                .findFirst().orElse(null);

        if (proposedUnits == null || proposedUnits <= 0) {
            results.add(result("proposed_quantity", ConstraintStatus.FAIL, String.valueOf(proposedUnits),
                    "positive whole units", "Proposed quantity must be positive."));
        } else {
            results.add(result("proposed_quantity", ConstraintStatus.PASS, proposedUnits.toString(),
                    "positive whole units", "Proposed quantity is a positive whole-unit value."));
        }

        if (snapshot.product() == null) {
            results.add(result("product_status", ConstraintStatus.NEEDS_INVESTIGATION, null,
                    "known active product", "Product evidence is missing."));
        } else {
            boolean active = snapshot.product().active();
            results.add(result("product_status", active ? ConstraintStatus.PASS : ConstraintStatus.FAIL,
                    Boolean.toString(active), "active", active ? "Product is active." : "Product is inactive."));
        }
        if (snapshot.warehouse() == null) {
            results.add(result("warehouse_status", ConstraintStatus.NEEDS_INVESTIGATION, null,
                    "known active warehouse", "Warehouse evidence is missing."));
        } else {
            boolean active = snapshot.warehouse().active();
            results.add(result("warehouse_status", active ? ConstraintStatus.PASS : ConstraintStatus.FAIL,
                    Boolean.toString(active), "active", active ? "Warehouse is active." : "Warehouse is inactive."));
        }

        checkInventory(snapshot.inventory(), results);
        checkForecast(snapshot.forecast(), results);
        checkPurchaseOrderEvidence(snapshot, results);
        if (snapshot.baselineRequirement() == null || !snapshot.baselineRequirement().calculable()) {
            results.add(result("baseline_requirement_evidence", ConstraintStatus.NEEDS_INVESTIGATION,
                    snapshot.baselineRequirement() == null ? null : "not calculable",
                    "complete and consistent requirement evidence",
                    snapshot.baselineRequirement() == null ? "Baseline requirement evidence is missing."
                            : String.join(" ", snapshot.baselineRequirement().warnings())));
        } else {
            results.add(result("baseline_requirement_evidence", ConstraintStatus.PASS,
                    snapshot.baselineRequirement().baselineNetRequirementUnits().toString(),
                    "complete and consistent requirement evidence", "Baseline components are calculable."));
        }
        checkOffer(snapshot, offer, proposedUnits, results);
        checkBudget(snapshot.budget(), offer, proposedUnits, results);
        checkStorage(snapshot, proposedUnits, results);
        return new ConstraintEvaluation(proposedUnits, selectedSupplierProductId, List.copyOf(results));
    }

    private void checkInventory(InventoryEvidence inventory, List<ConstraintResult> results) {
        if (inventory == null) {
            results.add(result("inventory_evidence", ConstraintStatus.NEEDS_INVESTIGATION,
                    null, "present, fresh, consistent inventory", "Inventory evidence is missing."));
        } else if (!inventory.fresh() || !inventory.warnings().isEmpty()) {
            results.add(result("inventory_evidence", ConstraintStatus.NEEDS_INVESTIGATION,
                    inventory.availableUnits() + " available", "present, fresh, consistent inventory",
                    String.join(" ", inventory.warnings())));
        } else {
            results.add(result("inventory_evidence", ConstraintStatus.PASS,
                    inventory.availableUnits() + " available", "present, fresh, consistent inventory",
                    "Inventory evidence is current and internally consistent."));
        }
    }

    private void checkForecast(ForecastEvidence forecast, List<ConstraintResult> results) {
        if (forecast == null || !forecast.complete() || !forecast.fresh()) {
            results.add(result("forecast_evidence", ConstraintStatus.NEEDS_INVESTIGATION,
                    forecast == null ? null : forecast.forecastRunId().toString(),
                    "complete, fresh forecast generation",
                    forecast == null ? "Forecast evidence is missing." : String.join(" ", forecast.warnings())));
        } else {
            results.add(result("forecast_evidence", ConstraintStatus.PASS, forecast.forecastRunId().toString(),
                    "complete, fresh forecast generation", "Forecast evidence covers the horizon and is current."));
        }
    }

    private void checkPurchaseOrderEvidence(PurchasingEvidenceSnapshot snapshot, List<ConstraintResult> results) {
        if (snapshot.purchaseOrders() == null || !snapshot.purchaseOrders().complete()) {
            results.add(result("purchase_order_evidence", ConstraintStatus.NEEDS_INVESTIGATION,
                    snapshot.purchaseOrders() == null ? null : "incomplete",
                    "consistent incoming-supply evidence",
                    snapshot.purchaseOrders() == null ? "Purchase-order evidence is missing."
                            : String.join(" ", snapshot.purchaseOrders().warnings())));
        } else {
            results.add(result("purchase_order_evidence", ConstraintStatus.PASS,
                    snapshot.purchaseOrders().confirmedIncomingUnits() + " confirmed, "
                            + snapshot.purchaseOrders().uncertainPotentialUnits() + " uncertain",
                    "consistent incoming-supply evidence", "Incoming supply is separated by confidence."));
        }
    }

    private void checkOffer(PurchasingEvidenceSnapshot snapshot, SupplierOfferEvidence offer,
                            Long proposedUnits, List<ConstraintResult> results) {
        if (offer == null) {
            results.add(result("supplier_offer", ConstraintStatus.NEEDS_INVESTIGATION,
                    String.valueOf(snapshot.suppliers() == null ? null : snapshot.suppliers().offers().size()),
                    "selected offer exists", "Selected supplier offer was not found in the evidence snapshot."));
            results.add(result("supplier_availability", ConstraintStatus.NEEDS_INVESTIGATION,
                    null, "available offer from active supplier", "Supplier offer evidence is missing."));
            results.add(result("supplier_warehouse_consistency", ConstraintStatus.NEEDS_INVESTIGATION,
                    null, "offer matches investigated product and warehouse", "Offer relationships cannot be checked."));
            results.add(result("supplier_moq", ConstraintStatus.NEEDS_INVESTIGATION,
                    null, "quantity >= MOQ", "Supplier offer evidence is missing."));
            results.add(result("supplier_pack_size", ConstraintStatus.NEEDS_INVESTIGATION,
                    null, "quantity is a pack-size multiple", "Supplier offer evidence is missing."));
            return;
        }
        if (offer.offerStatus() == null || offer.supplierStatus() == null) {
            results.add(result("supplier_availability", ConstraintStatus.NEEDS_INVESTIGATION,
                    offer.offerStatus() + "/" + offer.supplierStatus(), "AVAILABLE/ACTIVE",
                    "Supplier or offer status is missing."));
        } else {
            boolean available = offer.offerStatus() == com.procureai.domain.enums.SupplierOfferStatus.AVAILABLE
                    && offer.supplierStatus() == com.procureai.domain.enums.SupplierStatus.ACTIVE;
            results.add(result("supplier_availability", available ? ConstraintStatus.PASS : ConstraintStatus.FAIL,
                offer.offerStatus() + "/" + offer.supplierStatus(), "AVAILABLE/ACTIVE",
                    available ? "Offer and supplier are available." : "Offer or supplier is not available for purchasing."));
        }

        boolean relationshipsMatch = snapshot.product() != null && snapshot.warehouse() != null
                && snapshot.product().id().equals(offer.productId())
                && snapshot.warehouse().id().equals(offer.warehouseId());
        results.add(result("supplier_warehouse_consistency",
                relationshipsMatch ? ConstraintStatus.PASS : ConstraintStatus.NEEDS_INVESTIGATION,
                "productId=" + offer.productId() + ", warehouseId=" + offer.warehouseId(),
                "investigated product and warehouse IDs",
                relationshipsMatch ? "Offer matches the investigated product and warehouse."
                        : "Offer relationships do not match the investigated context."));

        if (proposedUnits == null || proposedUnits <= 0 || offer.minimumOrderQuantity() == null
                || offer.packSize() == null || offer.minimumOrderQuantity() <= 0 || offer.packSize() <= 0) {
            results.add(result("supplier_moq", ConstraintStatus.NEEDS_INVESTIGATION,
                    proposedUnits == null ? null : proposedUnits.toString(), "quantity >= valid MOQ",
                    "Quantity or MOQ evidence is invalid."));
            results.add(result("supplier_pack_size", ConstraintStatus.NEEDS_INVESTIGATION,
                    proposedUnits == null ? null : proposedUnits.toString(), "valid pack-size multiple",
                    "Quantity or pack-size evidence is invalid."));
        } else {
            boolean meetsMoq = proposedUnits >= offer.minimumOrderQuantity();
            results.add(result("supplier_moq", meetsMoq ? ConstraintStatus.PASS : ConstraintStatus.FAIL,
                    proposedUnits.toString(), ">= " + offer.minimumOrderQuantity(),
                    meetsMoq ? "Proposed quantity meets the supplier MOQ." : "Proposed quantity is below supplier MOQ."));
            boolean packCompatible = proposedUnits % offer.packSize() == 0;
            results.add(result("supplier_pack_size", packCompatible ? ConstraintStatus.PASS : ConstraintStatus.FAIL,
                    proposedUnits.toString(), "multiple of " + offer.packSize(),
                    packCompatible ? "Proposed quantity matches the supplier pack size."
                            : "Proposed quantity is not a supplier pack-size multiple."));
        }
    }

    private void checkBudget(BudgetEvidence budget, SupplierOfferEvidence offer,
                             Long proposedUnits, List<ConstraintResult> results) {
        if (budget == null || !budget.complete() || budget.availableAmount() == null || offer == null
                || proposedUnits == null || proposedUnits <= 0) {
            results.add(result("purchasing_budget", ConstraintStatus.NEEDS_INVESTIGATION,
                    budget == null ? null : String.valueOf(budget.availableAmount()),
                    "complete budget evidence in offer currency", "Budget or price evidence is incomplete."));
            return;
        }
        if (!budget.currencyCode().equals(offer.currencyCode())) {
            results.add(result("purchasing_budget", ConstraintStatus.NEEDS_INVESTIGATION,
                    budget.currencyCode() + " vs " + offer.currencyCode(), "matching currencies",
                    "Budget and supplier offer currencies differ; no FX conversion is available."));
            return;
        }
        if (offer.unitPrice() == null || offer.unitPrice().signum() < 0) {
            results.add(result("purchasing_budget", ConstraintStatus.NEEDS_INVESTIGATION,
                    String.valueOf(offer.unitPrice()), "non-negative unit price", "Offer price is invalid."));
            return;
        }
        BigDecimal proposedCost = offer.unitPrice().multiply(BigDecimal.valueOf(proposedUnits));
        boolean fits = proposedCost.compareTo(budget.availableAmount()) <= 0;
        results.add(result("purchasing_budget", fits ? ConstraintStatus.PASS : ConstraintStatus.FAIL,
                proposedCost + " " + budget.currencyCode(), "<= " + budget.availableAmount() + " available",
                fits ? "Proposed amount fits the derived available budget."
                        : "Proposed amount exceeds derived available budget."));
    }

    private void checkStorage(PurchasingEvidenceSnapshot snapshot, Long proposedUnits,
                              List<ConstraintResult> results) {
        StorageEvidence storage = snapshot.storage();
        if (storage == null || !storage.fresh() || !storage.consistent()
                || snapshot.product() == null || snapshot.product().unitVolumeM3() == null
                || proposedUnits == null || proposedUnits <= 0) {
            results.add(result("storage_capacity", ConstraintStatus.NEEDS_INVESTIGATION,
                    storage == null ? null : String.valueOf(storage.availableVolumeM3()),
                    "fresh consistent capacity and known unit volume",
                    storage == null ? "Storage evidence is missing."
                            : String.join(" ", storage.warnings())));
            return;
        }
        BigDecimal requiredVolume = snapshot.product().unitVolumeM3().multiply(BigDecimal.valueOf(proposedUnits));
        boolean fits = requiredVolume.compareTo(storage.availableVolumeM3()) <= 0;
        results.add(result("storage_capacity", fits ? ConstraintStatus.PASS : ConstraintStatus.FAIL,
                requiredVolume + " m3 required", "<= " + storage.availableVolumeM3() + " m3 available",
                fits ? "Proposed quantity fits current WMS available capacity."
                        : "Proposed quantity exceeds current WMS available capacity."));
    }

    private ConstraintResult result(String name, ConstraintStatus status, String observed,
                                    String required, String explanation) {
        return new ConstraintResult(name, status, observed, required, explanation);
    }
}
