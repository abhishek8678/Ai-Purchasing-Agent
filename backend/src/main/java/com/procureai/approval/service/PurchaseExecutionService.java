package com.procureai.approval.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.procureai.agent.audit.AgentAuditService;
import com.procureai.approval.dto.ApprovalResponse;
import com.procureai.approval.exception.ApprovalConflictException;
import com.procureai.approval.exception.ApprovalNotFoundException;
import com.procureai.domain.entity.PurchaseOrder;
import com.procureai.domain.entity.PurchaseOrderItem;
import com.procureai.domain.entity.PurchasingApproval;
import com.procureai.domain.entity.SupplierProduct;
import com.procureai.domain.enums.AgentEventType;
import com.procureai.domain.enums.ApprovalStatus;
import com.procureai.domain.enums.ExecutionStatus;
import com.procureai.domain.enums.PurchaseOrderStatus;
import com.procureai.repository.PurchaseOrderItemRepository;
import com.procureai.repository.PurchaseOrderRepository;
import com.procureai.repository.PurchasingApprovalRepository;
import com.procureai.repository.SupplierProductRepository;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Revalidates, creates, and verifies an internal PO without exposing write tools to Gemini. */
@Service
public class PurchaseExecutionService {

    private final TransactionTemplate serializedTransaction;
    private final TransactionTemplate recoveryTransaction;
    private final PurchasingApprovalRepository approvalRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository itemRepository;
    private final SupplierProductRepository offerRepository;
    private final ApprovalService approvalService;
    private final PurchaseOrderReadBackVerifier readBackVerifier;
    private final AgentAuditService auditService;
    private final EntityManager entityManager;
    private final Clock clock;

    public PurchaseExecutionService(PlatformTransactionManager transactionManager,
                                    PurchasingApprovalRepository approvalRepository,
                                    PurchaseOrderRepository purchaseOrderRepository,
                                    PurchaseOrderItemRepository itemRepository,
                                    SupplierProductRepository offerRepository,
                                    ApprovalService approvalService, PurchaseOrderReadBackVerifier readBackVerifier,
                                    AgentAuditService auditService,
                                    EntityManager entityManager, Clock clock) {
        this.serializedTransaction = new TransactionTemplate(transactionManager);
        this.serializedTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        this.recoveryTransaction = new TransactionTemplate(transactionManager);
        this.recoveryTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.approvalRepository = approvalRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.itemRepository = itemRepository;
        this.offerRepository = offerRepository;
        this.approvalService = approvalService;
        this.readBackVerifier = readBackVerifier;
        this.auditService = auditService;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    public ApprovalResponse execute(UUID approvalId) {
        try {
            return serializedTransaction.execute(status -> executeInTransaction(approvalId));
        } catch (ApprovalConflictException | ApprovalNotFoundException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return recordFailureAfterRollback(approvalId, exception);
        }
    }

    private ApprovalResponse executeInTransaction(UUID approvalId) {
        PurchasingApproval approval = approvalRepository.findByIdForUpdate(approvalId)
                .orElseThrow(() -> new ApprovalNotFoundException("Approval " + approvalId + " was not found."));
        if (approval.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new ApprovalConflictException("A rejected recommendation cannot be executed.");
        }
        if (approval.getExecutionStatus() == ExecutionStatus.SUCCEEDED
                || approval.getExecutionStatus() == ExecutionStatus.BLOCKED) {
            return approvalService.toResponse(approval);
        }

        PurchaseOrder persistedByKey = purchaseOrderRepository.findByIdempotencyKey(approval.getIdempotencyKey())
                .orElse(null);
        if (persistedByKey != null) {
            return verifyExistingOrder(approval, persistedByKey.getId());
        }
        if (approval.getExecutionStatus() == ExecutionStatus.IN_PROGRESS) {
            String summary = "A prior execution attempt was left in progress without a persisted PO; investigate before retrying.";
            approval.markFailed(summary, now());
            audit(approval, AgentEventType.EXECUTION_FAILED, Map.of("errorCode", "STALE_IN_PROGRESS"), summary);
            return approvalService.toResponse(approval);
        }

        approval.markInProgress("Revalidating current purchasing evidence.", now());
        audit(approval, AgentEventType.REVALIDATION_STARTED,
                Map.of("evidenceFingerprint", approval.getEvidenceFingerprint()), "Execution-time evidence revalidation started.");

        ApprovalService.EvidenceCapture current;
        try {
            current = approvalService.captureCurrentEvidence(approval.getAgentRun(), approval.getApprovedQuantity());
        } catch (RuntimeException exception) {
            return block(approval, "Current evidence could not be revalidated: " + safeMessage(exception),
                    Map.of("reason", safeMessage(exception)));
        }
        if (!approval.getEvidenceFingerprint().equals(current.fingerprint())) {
            List<String> changes = approvalService.changedEvidenceSections(approval.getApprovalEvidenceJson(), current.json());
            String summary = "Purchasing evidence changed after approval; renewed human approval is required. Changed sections: "
                    + String.join(", ", changes) + ".";
            return block(approval, summary, Map.of("changedEvidenceSections", changes,
                    "approvedFingerprint", approval.getEvidenceFingerprint(), "currentFingerprint", current.fingerprint()));
        }

        SupplierProduct offer = offerRepository.findById(approval.getSupplierProduct().getId()).orElse(null);
        if (offer == null || !offer.getUnitPrice().equals(approval.getUnitPriceAtApproval())
                || !offer.getCurrencyCode().equals(approval.getCurrencyCode())) {
            return block(approval, "The approved supplier offer or its price/currency changed; renewed human approval is required.",
                    Map.of("reason", "APPROVED_OFFER_CHANGED"));
        }
        try {
            approvalService.requireAllConstraintsPass(current.snapshot(), offer.getId(), approval.getApprovedQuantity());
        } catch (ApprovalConflictException exception) {
            return block(approval, exception.getMessage(), Map.of("reason", exception.getMessage()));
        }
        audit(approval, AgentEventType.REVALIDATION_PASSED,
                Map.of("evidenceFingerprint", current.fingerprint()), "Current evidence and all execution constraints passed.");
        audit(approval, AgentEventType.EXECUTION_STARTED,
                Map.of("idempotencyKey", approval.getIdempotencyKey(), "approvedQuantity", approval.getApprovedQuantity(),
                        "supplierProductId", offer.getId()), "Internal purchase-order creation started.");

        LocalDate orderDate = LocalDate.now(clock);
        String poNumber = poNumber(approval.getId());
        PurchaseOrder order = new PurchaseOrder(poNumber, offer.getSupplier(), offer.getWarehouse(), orderDate,
                PurchaseOrderStatus.APPROVED, approval.getCurrencyCode(), approval.getIdempotencyKey());
        order.setApprovedBy(approval.getApproverIdentity());
        order.setApprovedAt(approval.getDecisionAt());
        PurchaseOrder savedOrder = purchaseOrderRepository.saveAndFlush(order);
        LocalDate deliveryDate = orderDate.plusDays(offer.getLeadTimeDays());
        PurchaseOrderItem line = new PurchaseOrderItem(savedOrder, offer, approval.getApprovedQuantity(), null,
                0L, approval.getUnitPriceAtApproval(), deliveryDate);
        itemRepository.saveAndFlush(line);
        audit(approval, AgentEventType.PO_CREATED,
                Map.of("purchaseOrderId", savedOrder.getId(), "poNumber", savedOrder.getPoNumber(),
                        "idempotencyKey", savedOrder.getIdempotencyKey()), "Internal purchase order and line were persisted.");
        audit(approval, AgentEventType.PO_VERIFICATION_STARTED,
                Map.of("purchaseOrderId", savedOrder.getId()), "Persisted purchase order read-back verification started.");
        entityManager.flush();
        Long orderId = savedOrder.getId();
        entityManager.clear();
        approval = approvalRepository.findByIdForUpdate(approvalId).orElseThrow();
        PurchaseOrder persisted = purchaseOrderRepository.findById(orderId).orElse(null);
        return completeReadBack(approval, persisted);
    }

    private ApprovalResponse verifyExistingOrder(PurchasingApproval approval, Long orderId) {
        audit(approval, AgentEventType.PO_VERIFICATION_STARTED,
                Map.of("purchaseOrderId", orderId, "idempotentRetry", true),
                "Existing PO found by the approval idempotency key; verifying it instead of creating another.");
        entityManager.flush();
        entityManager.clear();
        PurchasingApproval lockedApproval = approvalRepository.findByIdForUpdate(approval.getId()).orElseThrow();
        PurchaseOrder persisted = purchaseOrderRepository.findById(orderId).orElse(null);
        return completeReadBack(lockedApproval, persisted);
    }

    private ApprovalResponse completeReadBack(PurchasingApproval approval, PurchaseOrder order) {
        List<PurchaseOrderItem> lines = order == null ? List.of() : itemRepository.findByPurchaseOrder(order);
        PurchaseOrderReadBackVerifier.Verification verification = readBackVerifier.verify(approval, order, lines);
        List<String> discrepancies = verification.discrepancies();
        boolean verified = verification.verified();
        String summary = verified ? "Purchase order was created and verified against the approved plan."
                : "Purchase order read-back verification failed: " + String.join(" ", discrepancies);
        approval.recordPurchaseOrder(order, verified, summary, now());
        audit(approval, verified ? AgentEventType.PO_VERIFICATION_COMPLETED : AgentEventType.EXECUTION_FAILED,
                Map.of("verified", verified, "discrepancies", discrepancies,
                        "purchaseOrderId", order == null ? -1L : order.getId()), summary);
        return approvalService.toResponse(approval);
    }

    private ApprovalResponse block(PurchasingApproval approval, String summary, Map<String, Object> details) {
        approval.markBlocked(summary, now());
        audit(approval, AgentEventType.REVALIDATION_FAILED, details, summary);
        audit(approval, AgentEventType.EXECUTION_BLOCKED, details,
                "No PO was created; the execution plan requires renewed human review.");
        return approvalService.toResponse(approval);
    }

    private ApprovalResponse recordFailureAfterRollback(UUID approvalId, RuntimeException failure) {
        return recoveryTransaction.execute(status -> {
            PurchasingApproval approval = approvalRepository.findByIdForUpdate(approvalId)
                    .orElseThrow(() -> new ApprovalNotFoundException("Approval " + approvalId + " was not found."));
            if (approval.getExecutionStatus() == ExecutionStatus.SUCCEEDED
                    || approval.getExecutionStatus() == ExecutionStatus.BLOCKED
                    || approval.getExecutionStatus() == ExecutionStatus.VERIFICATION_FAILED) {
                return approvalService.toResponse(approval);
            }
            PurchaseOrder existing = purchaseOrderRepository.findByIdempotencyKey(approval.getIdempotencyKey())
                    .orElse(null);
            if (existing != null) {
                approval.recordPurchaseOrder(existing, false,
                        "A PO exists for this idempotency key, but execution failed before read-back could be confirmed. Manual investigation is required.", now());
                audit(approval, AgentEventType.EXECUTION_FAILED,
                        Map.of("purchaseOrderId", existing.getId(), "recovery", "existing-po-preserved"),
                        approval.getExecutionSummary());
            } else {
                String summary = "Execution failed and the transaction rolled back without creating a PO: " + safeMessage(failure);
                approval.markFailed(summary, now());
                audit(approval, AgentEventType.EXECUTION_FAILED,
                        Map.of("errorType", failure.getClass().getSimpleName()), summary);
            }
            return approvalService.toResponse(approval);
        });
    }

    private void audit(PurchasingApproval approval, AgentEventType type, Object result, String summary) {
        auditService.appendNext(approval.getAgentRun().getId(), type, null, null, result, summary);
    }

    private String poNumber(UUID approvalId) {
        return "PA-" + approvalId;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? "an internal persistence or validation error occurred" : message;
    }
}
