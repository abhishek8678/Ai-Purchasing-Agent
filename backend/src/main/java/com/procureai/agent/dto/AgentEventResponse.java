package com.procureai.agent.dto;

import java.time.LocalDateTime;

import com.procureai.domain.enums.AgentEventType;

public record AgentEventResponse(
        Integer sequenceNumber,
        AgentEventType eventType,
        String toolName,
        String argumentsJson,
        String resultJson,
        String summary,
        LocalDateTime occurredAt
) {
}
