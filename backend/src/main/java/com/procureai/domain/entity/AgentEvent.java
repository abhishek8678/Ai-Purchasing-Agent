package com.procureai.domain.entity;

import java.time.LocalDateTime;

import com.procureai.domain.enums.AgentEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Append-only observation belonging to one agent run; it stores evidence, not chain-of-thought. */
@Entity
@Table(
        name = "agent_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_agent_events_run_sequence",
                columnNames = {"agent_run_id", "sequence_number"}
        ),
        indexes = @Index(
                name = "idx_agent_events_run_occurred",
                columnList = "agent_run_id, occurred_at"
        )
)
public class AgentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_run_id", nullable = false)
    private AgentRun agentRun;

    @NotNull
    @Positive
    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private AgentEventType eventType;

    @Size(max = 100)
    @Column(name = "tool_name", length = 100)
    private String toolName;

    @Column(name = "arguments_json", columnDefinition = "text")
    private String argumentsJson;

    @Column(name = "result_json", columnDefinition = "text")
    private String resultJson;

    @NotBlank
    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @NotNull
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    protected AgentEvent() {
        // Required by JPA.
    }

    public AgentEvent(
            AgentRun agentRun,
            Integer sequenceNumber,
            AgentEventType eventType,
            String toolName,
            String argumentsJson,
            String resultJson,
            String summary,
            LocalDateTime occurredAt
    ) {
        this.agentRun = agentRun;
        this.sequenceNumber = sequenceNumber;
        this.eventType = eventType;
        this.toolName = toolName;
        this.argumentsJson = argumentsJson;
        this.resultJson = resultJson;
        this.summary = summary;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public AgentRun getAgentRun() { return agentRun; }
    public Integer getSequenceNumber() { return sequenceNumber; }
    public AgentEventType getEventType() { return eventType; }
    public String getToolName() { return toolName; }
    public String getArgumentsJson() { return argumentsJson; }
    public String getResultJson() { return resultJson; }
    public String getSummary() { return summary; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
