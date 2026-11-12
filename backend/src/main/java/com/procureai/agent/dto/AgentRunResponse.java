package com.procureai.agent.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.procureai.domain.enums.AgentRunStatus;
import com.procureai.domain.enums.PurchaseDecision;

public record AgentRunResponse(
        UUID runId,
        String productSku,
        String warehouseCode,
        Long originalRecommendedQuantity,
        AgentRunStatus status,
        PurchaseDecision decision,
        Long proposedQuantity,
        String recommendationSummary,
        PurchasingRecommendation recommendation,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
}
