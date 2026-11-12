package com.procureai.approval.service;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procureai.approval.exception.ApprovalConflictException;
import com.procureai.domain.entity.AgentEvent;
import com.procureai.domain.entity.AgentRun;
import com.procureai.domain.enums.AgentEventType;
import com.procureai.repository.AgentEventRepository;
import com.procureai.service.dto.InvestigationRequest;
import com.procureai.service.dto.PlanningHorizon;
import org.springframework.stereotype.Component;

/** Reconstructs the persisted run horizon; current evidence is fetched separately for each approval/execution. */
@Component
public class AgentRunPurchasingContext {

    private final AgentEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public AgentRunPurchasingContext(AgentEventRepository eventRepository, ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    public InvestigationRequest request(AgentRun run, Long quantity, LocalDate budgetDate) {
        List<AgentEvent> events = eventRepository.findByAgentRunOrderBySequenceNumberAsc(run);
        AgentEvent started = events.stream().filter(event -> event.getEventType() == AgentEventType.RUN_STARTED)
                .findFirst().orElseThrow(() -> new ApprovalConflictException(
                        "The AgentRun has no persisted planning-horizon input and cannot be approved for execution."));
        try {
            JsonNode input = objectMapper.readTree(started.getArgumentsJson());
            LocalDate start = LocalDate.parse(input.path("planningStartDate").asText());
            LocalDate end = LocalDate.parse(input.path("planningEndDate").asText());
            return new InvestigationRequest(run.getProduct().getSku(), run.getWarehouse().getCode(),
                    new PlanningHorizon(start, end), budgetDate, quantity);
        } catch (Exception exception) {
            throw new ApprovalConflictException(
                    "The persisted AgentRun planning horizon is invalid; execution requires a new investigation.");
        }
    }
}
