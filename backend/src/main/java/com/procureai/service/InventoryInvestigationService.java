package com.procureai.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.procureai.config.InvestigationPolicyProperties;
import com.procureai.domain.entity.Inventory;
import com.procureai.domain.entity.Product;
import com.procureai.domain.entity.Warehouse;
import com.procureai.repository.InventoryRepository;
import com.procureai.service.dto.InventoryEvidence;
import com.procureai.service.exception.InvestigationException;
import org.springframework.stereotype.Service;

@Service
public class InventoryInvestigationService {

    private final InventoryRepository inventoryRepository;
    private final InvestigationPolicyProperties policy;
    private final Clock clock;

    public InventoryInvestigationService(
            InventoryRepository inventoryRepository,
            InvestigationPolicyProperties policy,
            Clock clock
    ) {
        this.inventoryRepository = inventoryRepository;
        this.policy = policy;
        this.clock = clock;
    }

    public InventoryEvidence investigate(Product product, Warehouse warehouse) {
        Inventory inventory = inventoryRepository.findByProductAndWarehouse(product, warehouse)
                .orElseThrow(() -> new InvestigationException("MISSING_INVENTORY",
                        "No inventory snapshot exists for " + product.getSku() + " at " + warehouse.getCode()));

        if (inventory.getOnHandUnits() == null || inventory.getReservedUnits() == null
                || inventory.getOnHandUnits() < 0 || inventory.getReservedUnits() < 0) {
            throw new InvestigationException("INVALID_INVENTORY",
                    "Inventory quantities must be present and non-negative for " + product.getSku());
        }

        List<String> warnings = new ArrayList<>();
        long availableUnits = Math.subtractExact(inventory.getOnHandUnits(), inventory.getReservedUnits());
        if (availableUnits < 0) {
            warnings.add("Reserved quantity exceeds on-hand quantity; available stock is shown as negative.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        Duration threshold = policy.getInventoryFreshnessThreshold();
        boolean fresh = inventory.getLastCountedAt() != null
                && !inventory.getLastCountedAt().isAfter(now)
                && !inventory.getLastCountedAt().isBefore(now.minus(threshold));
        if (inventory.getLastCountedAt() == null) {
            warnings.add("Inventory count timestamp is missing.");
        } else if (inventory.getLastCountedAt().isAfter(now)) {
            warnings.add("Inventory count timestamp is in the future.");
        } else if (!fresh) {
            warnings.add("Inventory snapshot is older than the configured freshness threshold.");
        }

        return new InventoryEvidence(product.getSku(), warehouse.getCode(), inventory.getOnHandUnits(),
                inventory.getReservedUnits(), availableUnits, inventory.getLastCountedAt(), fresh,
                threshold, List.copyOf(warnings));
    }
}
