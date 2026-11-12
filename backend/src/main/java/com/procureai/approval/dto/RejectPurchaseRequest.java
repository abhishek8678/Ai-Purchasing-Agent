package com.procureai.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectPurchaseRequest(
        @NotBlank @Size(max = 120) String approverIdentity,
        @NotBlank @Size(max = 1000) String reason
) { }
