package com.procureai.readmodel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PurchaseOrderItemResponse(Long id, Long supplierProductId, String productSku,
                                        String productName, String unitOfMeasure, Long orderedUnits,
                                        Long confirmedUnits, Long receivedUnits, BigDecimal unitPriceAtOrder,
                                        LocalDate expectedDeliveryDate) { }
