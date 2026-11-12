package com.procureai.readmodel.dto;

import java.time.LocalDateTime;

public record InventoryRowResponse(Long id, Long productId, String productSku, String productName,
                                   String unitOfMeasure, Long warehouseId, String warehouseCode,
                                   String warehouseName, Long onHandUnits, Long reservedUnits,
                                   Long availableUnits, LocalDateTime lastCountedAt) { }
