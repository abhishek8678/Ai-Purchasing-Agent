package com.procureai.domain.entity;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import com.procureai.domain.enums.AgentRunStatus;
import com.procureai.domain.enums.PurchaseDecision;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class AgentRunEntityTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void runningAgentRunPersistsWithUuidContextAndNullableOutcome() {
        Product product = persistProduct();
        Warehouse warehouse = persistWarehouse();
        LocalDateTime startedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        AgentRun run = entityManager.persistAndFlush(new AgentRun(product, warehouse, 800L,
                AgentRunStatus.RUNNING, null, null, "Investigation started.", startedAt, null));
        UUID id = run.getId();

        entityManager.clear();
        AgentRun found = entityManager.find(AgentRun.class, id);
        assertNotNull(id);
        assertNotNull(found);
        assertEquals(product.getId(), found.getProduct().getId());
        assertEquals(warehouse.getId(), found.getWarehouse().getId());
        assertEquals(800L, found.getInputRecommendationUnits());
        assertEquals(AgentRunStatus.RUNNING, found.getStatus());
        assertNull(found.getDecision());
        assertNull(found.getProposedUnits());
        assertNull(found.getCompletedAt());
        assertEquals(startedAt, found.getStartedAt());
        assertNotNull(found.getCreatedAt());
        assertNotNull(found.getUpdatedAt());
    }

    @Test
    void runStatusAndDecisionArePersistedAsStringsAndOutcomeCanBeRecorded() {
        AgentRun run = entityManager.persistAndFlush(new AgentRun(
                persistProduct(), persistWarehouse(), 800L, AgentRunStatus.RUNNING,
                null, null, "Investigation started.", LocalDateTime.now(), null));

        run.recordOutcome(AgentRunStatus.COMPLETED, PurchaseDecision.MODIFY, 600L,
                "Reduced quantity based on current evidence.", LocalDateTime.now());
        entityManager.flush();

        assertEquals("COMPLETED", jdbcTemplate.queryForObject(
                "select status from agent_runs where id = ?", String.class, run.getId()));
        assertEquals("MODIFY", jdbcTemplate.queryForObject(
                "select decision from agent_runs where id = ?", String.class, run.getId()));
        assertEquals(AgentRunStatus.COMPLETED, run.getStatus());
        assertEquals(PurchaseDecision.MODIFY, run.getDecision());
        assertEquals(600L, run.getProposedUnits());
        assertNotNull(run.getCompletedAt());
        assertNotNull(run.getUpdatedAt());
    }

    @Test
    void aRunCannotRecordRunningAsItsFinalOutcome() {
        AgentRun run = new AgentRun(persistProduct(), persistWarehouse(), 800L,
                AgentRunStatus.RUNNING, null, null, "In progress.", LocalDateTime.now(), null);

        assertThrows(IllegalArgumentException.class, () -> run.recordOutcome(
                AgentRunStatus.RUNNING, null, null, "Still running.", null));
    }

    @Test
    void requiredRunFieldsAndPositiveQuantitiesAreValidated() {
        AgentRun run = new AgentRun(null, null, 0L, null, null, -1L,
                " ", null, null);

        Set<ConstraintViolation<AgentRun>> violations = validator.validate(run);
        assertInvalid(violations, "product");
        assertInvalid(violations, "warehouse");
        assertInvalid(violations, "inputRecommendationUnits");
        assertInvalid(violations, "status");
        assertInvalid(violations, "proposedUnits");
        assertInvalid(violations, "decisionSummary");
        assertInvalid(violations, "startedAt");
    }

    private void assertInvalid(Set<ConstraintViolation<AgentRun>> violations, String field) {
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(field)),
                "Expected validation error for " + field);
    }

    private Product persistProduct() {
        return entityManager.persistAndFlush(new Product("SKU-AGENT-RUN", "Wireless Mouse", null,
                "EACH", null, true));
    }

    private Warehouse persistWarehouse() {
        return entityManager.persistAndFlush(new Warehouse("WH-AGENT-RUN", "Delhi FC", "Delhi", "IN", true));
    }
}
