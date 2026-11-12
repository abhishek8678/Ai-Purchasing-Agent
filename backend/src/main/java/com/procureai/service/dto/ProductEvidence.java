package com.procureai.service.dto;

import java.math.BigDecimal;

public record ProductEvidence(Long id, String sku, String name, String unitOfMeasure,
                              BigDecimal unitVolumeM3, boolean active) {
}
