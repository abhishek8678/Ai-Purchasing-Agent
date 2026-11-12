package com.procureai.readmodel.dto;

import java.math.BigDecimal;

public record SupplierOfferResponse(Long id, Long supplierId, String supplierCode, String supplierName,
                                    Long productId, String productSku, Long warehouseId, String warehouseCode,
                                    String status, Long minimumOrderQuantity, Long packSize, Integer leadTimeDays,
                                    BigDecimal unitPrice, String currencyCode) { }
