package com.procureai.domain.entity;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import com.procureai.domain.enums.AgentRunStatus;
import com.procureai.domain.enums.PurchaseDecision;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** Audit record for one purchasing investigation, not a store for hidden model reasoning. */
@Entity
@Table(
        name = "agent_runs",
        indexes = @Index(
                name = "idx_agent_runs_product_warehouse_started",
                columnList = "product_id, warehouse_id, started_at"
        )
)
public class AgentRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @NotNull
    @Positive
    @Column(name = "input_recommendation_units", nullable = false)
    private Long inputRecommendationUnits;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentRunStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PurchaseDecision decision;

    @PositiveOrZero
    @Column(name = "proposed_units")
    private Long proposedUnits;

    @NotBlank
    @Column(name = "decision_summary", nullable = false, columnDefinition = "text")
    private String decisionSummary;

    @NotNull
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected AgentRun() {
        // Required by JPA.
    }

    public AgentRun(
            Product product,
            Warehouse warehouse,
            Long inputRecommendationUnits,
            AgentRunStatus status,
            PurchaseDecision decision,
            Long proposedUnits,
            String decisionSummary,
            LocalDateTime startedAt,
            LocalDateTime completedAt
    ) {
        this.product = product;
        this.warehouse = warehouse;
        this.inputRecommendationUnits = inputRecommendationUnits;
        this.status = status;
        this.decision = decision;
        this.proposedUnits = proposedUnits;
        this.decisionSummary = decisionSummary;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    /** Records a final outcome together so its status and summary cannot be updated separately. */
    public void recordOutcome(
            AgentRunStatus outcomeStatus,
            PurchaseDecision decision,
            Long proposedUnits,
            String decisionSummary,
            LocalDateTime completedAt
    ) {
        if (outcomeStatus == null || outcomeStatus == AgentRunStatus.RUNNING) {
            throw new IllegalArgumentException("An outcome must use a terminal agent run status");
        }
        this.status = outcomeStatus;
        this.decision = decision;
        this.proposedUnits = proposedUnits;
        this.decisionSummary = decisionSummary;
        this.completedAt = completedAt;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(Clock.systemUTC());
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(Clock.systemUTC());
    }

    public UUID getId() { return id; }
    public Product getProduct() { return product; }
    public Warehouse getWarehouse() { return warehouse; }
    public Long getInputRecommendationUnits() { return inputRecommendationUnits; }
    public AgentRunStatus getStatus() { return status; }
    public PurchaseDecision getDecision() { return decision; }
    public Long getProposedUnits() { return proposedUnits; }
    public String getDecisionSummary() { return decisionSummary; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
