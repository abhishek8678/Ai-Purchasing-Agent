package com.procureai.service.dto;

import java.util.List;

public record SupplierEvidence(
        String productSku,
        String warehouseCode,
        Long requestedUnits,
        List<SupplierOfferEvidence> offers,
        List<String> warnings
) {
}
