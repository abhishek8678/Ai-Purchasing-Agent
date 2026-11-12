package com.procureai.service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.procureai.service.dto.BaselineRequirementEvidence;
import com.procureai.service.dto.ForecastEvidence;
import com.procureai.service.dto.InventoryEvidence;
import com.procureai.service.dto.PurchaseOrderEvidence;
import org.springframework.stereotype.Service;

/** Transparent baseline math only; this service never returns an AI purchasing decision. */
@Service
public class PurchaseRequirementService {

    public BaselineRequirementEvidence calculate(
            InventoryEvidence inventory,
            ForecastEvidence forecast,
            Long safetyStockUnits,
            PurchaseOrderEvidence purchaseOrders
    ) {
        List<String> warnings = new ArrayList<>();
        if (inventory == null) warnings.add("Inventory evidence is missing.");
        if (forecast == null) warnings.add("Forecast evidence is missing.");
        if (safetyStockUnits == null) warnings.add("Replenishment policy is missing.");
        if (purchaseOrders == null) warnings.add("Purchase-order evidence is missing.");
        if (inventory != null) {
            warnings.addAll(inventory.warnings());
            if (!inventory.fresh()) warnings.add("Inventory evidence is stale or has no usable timestamp.");
        }
        if (forecast != null) {
            warnings.addAll(forecast.warnings());
            if (!forecast.complete()) warnings.add("A complete forecast horizon is unavailable.");
            if (!forecast.fresh()) warnings.add("Forecast evidence is stale.");
        }
        if (purchaseOrders != null) {
            warnings.addAll(purchaseOrders.warnings());
            if (!purchaseOrders.complete()) warnings.add("Purchase-order evidence contains inconsistencies.");
        }
        if (safetyStockUnits != null && safetyStockUnits < 0) {
            warnings.add("Safety stock cannot be negative.");
        }

        Long grossDemand = forecast == null ? null : forecast.observedDemandUnits();
        Long safety = safetyStockUnits;
        Long available = inventory == null ? null : inventory.availableUnits();
        Long confirmed = purchaseOrders == null ? null : purchaseOrders.confirmedIncomingUnits();
        Long uncertain = purchaseOrders == null ? null : purchaseOrders.uncertainPotentialUnits();
        boolean calculable = inventory != null && inventory.fresh() && forecast != null
                && forecast.complete() && forecast.fresh() && safetyStockUnits != null && safetyStockUnits >= 0
                && purchaseOrders != null && purchaseOrders.complete()
                && inventory.warnings().isEmpty();
        if (!calculable) {
            return new BaselineRequirementEvidence(grossDemand, safety, available, confirmed, uncertain,
                    null, false, List.copyOf(warnings));
        }

        BigInteger net = BigInteger.valueOf(grossDemand)
                .add(BigInteger.valueOf(safetyStockUnits))
                .subtract(BigInteger.valueOf(inventory.availableUnits()))
                .subtract(BigInteger.valueOf(purchaseOrders.confirmedIncomingUnits()))
                .max(BigInteger.ZERO);
        if (net.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            warnings.add("Baseline requirement exceeds the supported whole-unit range.");
            return new BaselineRequirementEvidence(grossDemand, safety, available, confirmed, uncertain,
                    null, false, List.copyOf(warnings));
        }
        return new BaselineRequirementEvidence(grossDemand, safety, available, confirmed, uncertain,
                net.longValueExact(), true, List.copyOf(warnings));
    }
}
