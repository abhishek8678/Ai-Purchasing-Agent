package com.procureai.agent.tool;

import java.util.UUID;

import com.procureai.agent.dto.AgentRunRequest;

public record AgentToolContext(UUID runId, AgentRunRequest request, int executionNumber) {
}
