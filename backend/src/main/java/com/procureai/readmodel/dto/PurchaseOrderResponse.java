package com.procureai.readmodel.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PurchaseOrderResponse(Long id, String poNumber, Long supplierId, String supplierCode,
                                    String supplierName, Long warehouseId, String warehouseCode,
                                    String warehouseName, LocalDate orderDate, String status,
                                    String currencyCode, String approvedBy, LocalDateTime approvedAt,
                                    List<PurchaseOrderItemResponse> items) { }
