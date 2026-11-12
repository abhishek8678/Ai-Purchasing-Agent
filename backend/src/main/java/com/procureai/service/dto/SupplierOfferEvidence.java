package com.procureai.service.dto;

import java.math.BigDecimal;
import java.util.List;

import com.procureai.domain.enums.SupplierOfferStatus;
import com.procureai.domain.enums.SupplierStatus;

public record SupplierOfferEvidence(
        Long supplierProductId,
        Long supplierId,
        String supplierCode,
        String supplierName,
        SupplierStatus supplierStatus,
        Long productId,
        Long warehouseId,
        SupplierOfferStatus offerStatus,
        Long minimumOrderQuantity,
        Long packSize,
        Integer leadTimeDays,
        BigDecimal unitPrice,
        String currencyCode,
        Long requestedUnits,
        Long roundedOrderUnits,
        Long additionalUnitsAfterRounding,
        List<String> warnings
) {
}
