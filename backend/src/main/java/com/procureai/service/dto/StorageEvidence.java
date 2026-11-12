package com.procureai.service.dto;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public record StorageEvidence(
        String warehouseCode,
        BigDecimal totalVolumeM3,
        BigDecimal allocatedVolumeM3,
        BigDecimal availableVolumeM3,
        LocalDateTime measuredAt,
        boolean fresh,
        boolean consistent,
        Duration freshnessThreshold,
        List<String> warnings
) {
}
