package com.procureai.approval.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.procureai.domain.enums.ApprovalStatus;
import com.procureai.domain.enums.ExecutionStatus;

public record ApprovalResponse(
        UUID approvalId,
        UUID agentRunId,
        ApprovalStatus approvalStatus,
        ExecutionStatus executionStatus,
        Long approvedQuantity,
        Long supplierProductId,
        Long supplierId,
        String supplierCode,
        String approverIdentity,
        LocalDateTime decisionAt,
        String rejectionReason,
        String idempotencyKey,
        Long purchaseOrderId,
        String purchaseOrderNumber,
        String executionSummary,
        LocalDateTime executionAt
) { }
