package com.procureai.domain.entity;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import com.procureai.domain.enums.ApprovalStatus;
import com.procureai.domain.enums.ExecutionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Human decision and execution record for one immutable AgentRun recommendation. */
@Entity
@Table(name = "purchasing_approvals", uniqueConstraints = {
        @UniqueConstraint(name = "uk_purchasing_approvals_agent_run", columnNames = "agent_run_id"),
        @UniqueConstraint(name = "uk_purchasing_approvals_idempotency", columnNames = "idempotency_key")
}, indexes = @Index(name = "idx_purchasing_approvals_status_created", columnList = "approval_status, created_at"))
public class PurchasingApproval {

    @Id
    private UUID id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_run_id", nullable = false)
    private AgentRun agentRun;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private ApprovalStatus approvalStatus;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false, length = 30)
    private ExecutionStatus executionStatus;

    @Positive
    @Column(name = "approved_quantity")
    private Long approvedQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_product_id")
    private SupplierProduct supplierProduct;

    @NotBlank
    @Size(max = 120)
    @Column(name = "approver_identity", nullable = false, length = 120)
    private String approverIdentity;

    @Column(name = "decision_at", nullable = false)
    private LocalDateTime decisionAt;

    @Size(max = 1000)
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "unit_price_at_approval", precision = 19, scale = 4)
    private BigDecimal unitPriceAtApproval;

    @Size(max = 3)
    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Size(max = 100)
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

    @Column(name = "evidence_fingerprint", length = 64)
    private String evidenceFingerprint;

    @Column(name = "approval_evidence_json", columnDefinition = "text")
    private String approvalEvidenceJson;

    @Column(name = "execution_summary", columnDefinition = "text")
    private String executionSummary;

    @Column(name = "execution_at")
    private LocalDateTime executionAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PurchasingApproval() { }

    public PurchasingApproval(UUID id, AgentRun agentRun, ApprovalStatus approvalStatus, String approverIdentity,
                              LocalDateTime decisionAt, Long approvedQuantity, SupplierProduct supplierProduct,
                              String rejectionReason, BigDecimal unitPriceAtApproval, String currencyCode,
                              String idempotencyKey, String evidenceFingerprint, String approvalEvidenceJson) {
        this.id = id;
        this.agentRun = agentRun;
        this.approvalStatus = approvalStatus;
        this.executionStatus = ExecutionStatus.NOT_EXECUTED;
        this.approverIdentity = approverIdentity;
        this.decisionAt = decisionAt;
        this.approvedQuantity = approvedQuantity;
        this.supplierProduct = supplierProduct;
        this.rejectionReason = rejectionReason;
        this.unitPriceAtApproval = unitPriceAtApproval;
        this.currencyCode = currencyCode;
        this.idempotencyKey = idempotencyKey;
        this.evidenceFingerprint = evidenceFingerprint;
        this.approvalEvidenceJson = approvalEvidenceJson;
    }

    public void markInProgress(String summary, LocalDateTime at) {
        this.executionStatus = ExecutionStatus.IN_PROGRESS;
        this.executionSummary = summary;
        this.executionAt = at;
    }

    public void markBlocked(String summary, LocalDateTime at) {
        this.executionStatus = ExecutionStatus.BLOCKED;
        this.executionSummary = summary;
        this.executionAt = at;
    }

    public void markFailed(String summary, LocalDateTime at) {
        this.executionStatus = ExecutionStatus.FAILED;
        this.executionSummary = summary;
        this.executionAt = at;
    }

    public void recordPurchaseOrder(PurchaseOrder order, boolean verified, String summary, LocalDateTime at) {
        this.purchaseOrder = order;
        this.executionStatus = verified ? ExecutionStatus.SUCCEEDED : ExecutionStatus.VERIFICATION_FAILED;
        this.executionSummary = summary;
        this.executionAt = at;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(Clock.systemUTC());
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(Clock.systemUTC());
    }

    public UUID getId() { return id; }
    public AgentRun getAgentRun() { return agentRun; }
    public ApprovalStatus getApprovalStatus() { return approvalStatus; }
    public ExecutionStatus getExecutionStatus() { return executionStatus; }
    public Long getApprovedQuantity() { return approvedQuantity; }
    public SupplierProduct getSupplierProduct() { return supplierProduct; }
    public String getApproverIdentity() { return approverIdentity; }
    public LocalDateTime getDecisionAt() { return decisionAt; }
    public String getRejectionReason() { return rejectionReason; }
    public BigDecimal getUnitPriceAtApproval() { return unitPriceAtApproval; }
    public String getCurrencyCode() { return currencyCode; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public PurchaseOrder getPurchaseOrder() { return purchaseOrder; }
    public String getEvidenceFingerprint() { return evidenceFingerprint; }
    public String getApprovalEvidenceJson() { return approvalEvidenceJson; }
    public String getExecutionSummary() { return executionSummary; }
    public LocalDateTime getExecutionAt() { return executionAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
