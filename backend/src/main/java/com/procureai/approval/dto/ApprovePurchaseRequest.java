package com.procureai.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ApprovePurchaseRequest(
        @NotNull @Positive Long approvedQuantity,
        @NotNull @Positive Long supplierProductId,
        @NotBlank @Size(max = 120) String approverIdentity
) { }
