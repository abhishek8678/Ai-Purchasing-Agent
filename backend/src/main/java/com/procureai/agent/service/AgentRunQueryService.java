package com.procureai.agent.service;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procureai.agent.dto.AgentEventResponse;
import com.procureai.agent.dto.AgentRunResponse;
import com.procureai.agent.dto.PurchasingRecommendation;
import com.procureai.agent.exception.AgentNotFoundException;
import com.procureai.domain.enums.AgentEventType;
import com.procureai.repository.AgentEventRepository;
import com.procureai.repository.AgentRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentRunQueryService {

    private final AgentRunRepository runRepository;
    private final AgentEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public AgentRunQueryService(AgentRunRepository runRepository, AgentEventRepository eventRepository,
                                ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AgentRunResponse getRun(UUID runId) {
        var run = runRepository.findById(runId)
                .orElseThrow(() -> new AgentNotFoundException("Agent run " + runId + " was not found."));
        PurchasingRecommendation recommendation = eventRepository
                .findByAgentRunOrderBySequenceNumberAsc(run).stream()
                .filter(event -> event.getEventType() == AgentEventType.DECISION_PROPOSED)
                .reduce((first, second) -> second)
                .map(event -> readRecommendation(event.getResultJson()))
                .orElse(null);
        return new AgentRunResponse(run.getId(), run.getProduct().getSku(), run.getWarehouse().getCode(),
                run.getInputRecommendationUnits(), run.getStatus(), run.getDecision(), run.getProposedUnits(),
                run.getDecisionSummary(), recommendation, run.getStartedAt(), run.getCompletedAt());
    }

    @Transactional(readOnly = true)
    public List<AgentEventResponse> getEvents(UUID runId) {
        var run = runRepository.findById(runId)
                .orElseThrow(() -> new AgentNotFoundException("Agent run " + runId + " was not found."));
        return eventRepository.findByAgentRunOrderBySequenceNumberAsc(run).stream()
                .map(event -> new AgentEventResponse(event.getSequenceNumber(), event.getEventType(),
                        event.getToolName(), event.getArgumentsJson(), event.getResultJson(),
                        event.getSummary(), event.getOccurredAt()))
                .toList();
    }

    private PurchasingRecommendation readRecommendation(String resultJson) {
        try {
            return objectMapper.readValue(resultJson, PurchasingRecommendation.class);
        } catch (Exception exception) {
            // A malformed persisted result is not exposed as a valid recommendation.
            return null;
        }
    }
}
