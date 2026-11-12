package com.procureai.agent.audit;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procureai.agent.dto.AgentRunRequest;
import com.procureai.domain.entity.AgentEvent;
import com.procureai.domain.entity.AgentRun;
import com.procureai.domain.entity.Product;
import com.procureai.domain.entity.Warehouse;
import com.procureai.domain.enums.AgentEventType;
import com.procureai.domain.enums.AgentRunStatus;
import com.procureai.domain.enums.PurchaseDecision;
import com.procureai.repository.AgentEventRepository;
import com.procureai.repository.AgentRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentAuditService {

    private final AgentRunRepository runRepository;
    private final AgentEventRepository eventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AgentAuditService(AgentRunRepository runRepository, AgentEventRepository eventRepository,
                             ObjectMapper objectMapper, Clock clock) {
        this.runRepository = runRepository;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public AgentRun start(Product product, Warehouse warehouse, AgentRunRequest request) {
        LocalDateTime now = now();
        AgentRun run = runRepository.saveAndFlush(new AgentRun(product, warehouse,
                request.recommendedPurchaseUnits(), AgentRunStatus.RUNNING, null, null,
                "Investigation is running.", now, null));
        append(run, 1, AgentEventType.RUN_STARTED, null,
                json(new RunInput(request.productSku(), request.warehouseCode(),
                        request.recommendedPurchaseUnits(), request.planningStartDate(), request.planningEndDate())),
                null, "Agent investigation started.");
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(UUID runId, int sequence, AgentEventType eventType, String toolName,
                       Object arguments, Object result, String summary) {
        AgentRun run = runRepository.getReferenceById(runId);
        append(run, sequence, eventType, toolName, json(arguments), json(result), summary);
    }

    /** Adds the next ordered event within the caller's transaction. */
    @Transactional
    public int appendNext(UUID runId, AgentEventType eventType, String toolName,
                          Object arguments, Object result, String summary) {
        AgentRun run = runRepository.getReferenceById(runId);
        List<AgentEvent> events = eventRepository.findByAgentRunOrderBySequenceNumberAsc(run);
        int nextSequence = events.isEmpty() ? 1 : Math.addExact(events.get(events.size() - 1).getSequenceNumber(), 1);
        append(run, nextSequence, eventType, toolName, json(arguments), json(result), summary);
        return nextSequence;
    }

    @Transactional
    public void finish(UUID runId, int sequence, AgentRunStatus status, PurchaseDecision decision,
                       Long proposedUnits, String summary, AgentEventType finalEventType,
                       Object arguments, Object result) {
        AgentRun run = runRepository.findById(runId).orElseThrow();
        run.recordOutcome(status, decision, proposedUnits, summary, now());
        runRepository.save(run);
        append(run, sequence, finalEventType, null, json(arguments), json(result), summary);
    }

    private void append(AgentRun run, int sequence, AgentEventType eventType, String toolName,
                        String argumentsJson, String resultJson, String summary) {
        eventRepository.save(new AgentEvent(run, sequence, eventType, toolName,
                argumentsJson, resultJson, summary, now()));
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{\"serializationError\":\"Audit payload could not be serialized\"}";
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private record RunInput(String productSku, String warehouseCode, Long recommendedPurchaseUnits,
                            java.time.LocalDate planningStartDate, java.time.LocalDate planningEndDate) {
    }
}
