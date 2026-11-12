package com.procureai.service.dto;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public record InventoryEvidence(
        String productSku,
        String warehouseCode,
        long onHandUnits,
        long reservedUnits,
        long availableUnits,
        LocalDateTime lastCountedAt,
        boolean fresh,
        Duration freshnessThreshold,
        List<String> warnings
) {
}
