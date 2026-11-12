package com.procureai.service.dto;

public record ConstraintResult(
        String constraintName,
        ConstraintStatus status,
        String actualValue,
        String requiredValue,
        String explanation
) {
}
