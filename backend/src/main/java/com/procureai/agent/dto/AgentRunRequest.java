package com.procureai.agent.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AgentRunRequest(
        @NotBlank @Size(max = 80) String productSku,
        @NotBlank @Size(max = 40) String warehouseCode,
        @NotNull @Positive Long recommendedPurchaseUnits,
        @NotNull LocalDate planningStartDate,
        @NotNull LocalDate planningEndDate,
        @Size(max = 1500) String userInstruction
) {
    @AssertTrue(message = "Planning end date must be on or after planning start date")
    public boolean isPlanningRangeOrdered() {
        return planningStartDate == null || planningEndDate == null
                || !planningEndDate.isBefore(planningStartDate);
    }
}
