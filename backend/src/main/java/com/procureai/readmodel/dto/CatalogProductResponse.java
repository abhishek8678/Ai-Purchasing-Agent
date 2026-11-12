package com.procureai.readmodel.dto;

import java.math.BigDecimal;

public record CatalogProductResponse(Long id, String sku, String name, String unitOfMeasure,
                                     BigDecimal unitVolumeM3, Boolean active) { }
