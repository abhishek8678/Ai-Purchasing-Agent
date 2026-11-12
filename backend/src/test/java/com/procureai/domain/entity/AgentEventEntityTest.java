package com.procureai.domain.entity;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import com.procureai.domain.enums.AgentEventType;
import com.procureai.domain.enums.AgentRunStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class AgentEventEntityTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void eventPersistsWithRunAndObservableDetails() {
        AgentRun run = persistRun();
        LocalDateTime occurredAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        AgentEvent event = entityManager.persistAndFlush(new AgentEvent(run, 1,
                AgentEventType.TOOL_REQUESTED, "inventory.lookup", "{\"sku\":\"MOUSE-001\"}", null,
                "Requested the current inventory snapshot.", occurredAt));
        Long id = event.getId();

        entityManager.clear();
        AgentEvent found = entityManager.find(AgentEvent.class, id);
        assertNotNull(found);
        assertEquals(run.getId(), found.getAgentRun().getId());
        assertEquals(1, found.getSequenceNumber());
        assertEquals(AgentEventType.TOOL_REQUESTED, found.getEventType());
        assertEquals("inventory.lookup", found.getToolName());
        assertEquals("{\"sku\":\"MOUSE-001\"}", found.getArgumentsJson());
        assertEquals(occurredAt, found.getOccurredAt());
        assertEquals("TOOL_REQUESTED", jdbcTemplate.queryForObject(
                "select event_type from agent_events where id = ?", String.class, id));
    }

    @Test
    void sequenceNumbersAreUniquePerRunAndProvideEventOrdering() {
        AgentRun run = persistRun();
        entityManager.persistAndFlush(event(run, 2, AgentEventType.TOOL_RESULT));
        entityManager.persistAndFlush(event(run, 1, AgentEventType.TOOL_REQUESTED));

        List<Integer> sequences = jdbcTemplate.queryForList(
                "select sequence_number from agent_events where agent_run_id = ? order by sequence_number",
                Integer.class, run.getId());
        assertEquals(List.of(1, 2), sequences);
        assertThrows(RuntimeException.class, () -> entityManager.persistAndFlush(
                event(run, 1, AgentEventType.READBACK_VALIDATION)));
    }

    @Test
    void eventRequiredFieldsAndSequenceAreValidated() {
        AgentEvent event = new AgentEvent(null, 0, null, "x".repeat(101), null, null, " ", null);

        Set<ConstraintViolation<AgentEvent>> violations = validator.validate(event);
        assertInvalid(violations, "agentRun");
        assertInvalid(violations, "sequenceNumber");
        assertInvalid(violations, "eventType");
        assertInvalid(violations, "toolName");
        assertInvalid(violations, "summary");
        assertInvalid(violations, "occurredAt");
    }

    @Test
    void optionalToolAndPayloadFieldsMayBeNull() {
        AgentEvent event = entityManager.persistAndFlush(new AgentEvent(
                persistRun(), 1, AgentEventType.DECISION_VALIDATED, null, null, null,
                "Validated the proposed decision.", LocalDateTime.now()));

        assertNotNull(event.getId());
        assertEquals(null, event.getToolName());
        assertEquals(null, event.getArgumentsJson());
        assertEquals(null, event.getResultJson());
    }

    private void assertInvalid(Set<ConstraintViolation<AgentEvent>> violations, String field) {
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(field)),
                "Expected validation error for " + field);
    }

    private AgentEvent event(AgentRun run, int sequence, AgentEventType eventType) {
        return new AgentEvent(run, sequence, eventType, null, null, null,
                "Recorded an observable event.", LocalDateTime.now());
    }

    private AgentRun persistRun() {
        Product product = entityManager.persistAndFlush(new Product("SKU-AGENT-EVENT", "Wireless Mouse", null,
                "EACH", null, true));
        Warehouse warehouse = entityManager.persistAndFlush(
                new Warehouse("WH-AGENT-EVENT", "Delhi FC", "Delhi", "IN", true));
        return entityManager.persistAndFlush(new AgentRun(product, warehouse, 800L,
                AgentRunStatus.RUNNING, null, null, "Investigation started.", LocalDateTime.now(), null));
    }
}
