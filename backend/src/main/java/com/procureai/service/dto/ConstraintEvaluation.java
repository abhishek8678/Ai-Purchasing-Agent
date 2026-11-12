package com.procureai.service.dto;

import java.util.List;

public record ConstraintEvaluation(Long proposedUnits, Long selectedSupplierProductId, List<ConstraintResult> results) {
}
