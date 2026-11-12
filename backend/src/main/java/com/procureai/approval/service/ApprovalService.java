package com.procureai.approval.service;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.procureai.agent.audit.AgentAuditService;
import com.procureai.agent.dto.PurchasingRecommendation;
import com.procureai.approval.dto.ApprovePurchaseRequest;
import com.procureai.approval.dto.ApprovalResponse;
import com.procureai.approval.dto.RejectPurchaseRequest;
import com.procureai.approval.exception.ApprovalConflictException;
import com.procureai.approval.exception.ApprovalNotFoundException;
import com.procureai.domain.entity.AgentEvent;
import com.procureai.domain.entity.AgentRun;
import com.procureai.domain.entity.PurchasingApproval;
import com.procureai.domain.entity.SupplierProduct;
import com.procureai.domain.enums.AgentEventType;
import com.procureai.domain.enums.AgentRunStatus;
import com.procureai.domain.enums.ApprovalStatus;
import com.procureai.domain.enums.PurchaseDecision;
import com.procureai.domain.enums.SupplierOfferStatus;
import com.procureai.domain.enums.SupplierStatus;
import com.procureai.repository.AgentEventRepository;
import com.procureai.repository.AgentRunRepository;
import com.procureai.repository.PurchasingApprovalRepository;
import com.procureai.repository.SupplierProductRepository;
import com.procureai.service.ConstraintValidationService;
import com.procureai.service.PurchasingEvidenceSnapshotService;
import com.procureai.service.dto.ConstraintEvaluation;
import com.procureai.service.dto.EvidenceIssue;
import com.procureai.service.dto.InvestigationRequest;
import com.procureai.service.dto.PurchasingEvidenceSnapshot;
import com.procureai.service.exception.InvestigationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalService {

    private final AgentRunRepository runRepository;
    private final AgentEventRepository eventRepository;
    private final PurchasingApprovalRepository approvalRepository;
    private final SupplierProductRepository offerRepository;
    private final AgentRunPurchasingContext purchasingContext;
    private final PurchasingEvidenceSnapshotService snapshotService;
    private final ConstraintValidationService constraintService;
    private final AgentAuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ApprovalService(AgentRunRepository runRepository, AgentEventRepository eventRepository,
                           PurchasingApprovalRepository approvalRepository, SupplierProductRepository offerRepository,
                           AgentRunPurchasingContext purchasingContext,
                           PurchasingEvidenceSnapshotService snapshotService,
                           ConstraintValidationService constraintService, AgentAuditService auditService,
                           ObjectMapper objectMapper, Clock clock) {
        this.runRepository = runRepository;
        this.eventRepository = eventRepository;
        this.approvalRepository = approvalRepository;
        this.offerRepository = offerRepository;
        this.purchasingContext = purchasingContext;
        this.snapshotService = snapshotService;
        this.constraintService = constraintService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public ApprovalResponse approve(UUID runId, ApprovePurchaseRequest request) {
        AgentRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ApprovalNotFoundException("Agent run " + runId + " was not found."));
        ensureNoDecisionAlreadyRecorded(run);
        PurchasingRecommendation recommendation = recommendation(run);
        if (run.getStatus() != AgentRunStatus.COMPLETED || recommendation == null
                || run.getDecision() != recommendation.decision()
                || (recommendation.decision() != PurchaseDecision.ACCEPT
                && recommendation.decision() != PurchaseDecision.MODIFY)) {
            throw new ApprovalConflictException("Only a completed ACCEPT or MODIFY recommendation can be approved.");
        }
        if (!run.getInputRecommendationUnits().equals(recommendation.originalRecommendedQuantity())
                || run.getProposedUnits() == null || !run.getProposedUnits().equals(recommendation.proposedQuantity())
                || !request.approvedQuantity().equals(run.getProposedUnits())) {
            throw new ApprovalConflictException(
                    "Approved quantity must exactly match the validated quantity on the completed AgentRun.");
        }
        String approver = requiredText(request.approverIdentity(), "Approver identity is required.");
        SupplierProduct offer = offerRepository.findById(request.supplierProductId())
                .orElseThrow(() -> new ApprovalConflictException("The selected supplier offer does not exist."));
        validateOfferForRun(offer, run);
        if (recommendation.selectedSupplierProductId() != null
                && !recommendation.selectedSupplierProductId().equals(offer.getId())) {
            throw new ApprovalConflictException(
                    "The selected offer differs from the offer in the AI recommendation; obtain a new recommendation first.");
        }
        if (recommendation.selectedSupplierId() != null
                && !recommendation.selectedSupplierId().equals(offer.getSupplier().getId())) {
            throw new ApprovalConflictException("The selected offer's supplier differs from the AI recommendation.");
        }

        EvidenceCapture capture = captureCurrentEvidence(run, request.approvedQuantity());
        requireAllConstraintsPass(capture.snapshot(), offer.getId(), request.approvedQuantity());
        LocalDateTime now = LocalDateTime.now(clock);
        UUID approvalId = UUID.randomUUID();
        PurchasingApproval approval = new PurchasingApproval(approvalId, run, ApprovalStatus.APPROVED, approver,
                now, request.approvedQuantity(), offer, null, offer.getUnitPrice(), offer.getCurrencyCode(),
                idempotencyKey(approvalId), capture.fingerprint(), capture.json());
        approvalRepository.saveAndFlush(approval);
        auditService.appendNext(runId, AgentEventType.APPROVAL_REQUESTED, null,
                Map.of("approvalId", approvalId, "approvedQuantity", request.approvedQuantity(),
                        "supplierProductId", offer.getId(), "approverIdentity", approver), null,
                "A human requested approval of the exact quantity and supplier offer.");
        auditService.appendNext(runId, AgentEventType.APPROVAL_GRANTED, null, null,
                Map.of("approvalId", approvalId, "decisionAt", now),
                "Human approval recorded; no purchase order was created.");
        return toResponse(approval);
    }

    @Transactional
    public ApprovalResponse reject(UUID runId, RejectPurchaseRequest request) {
        AgentRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ApprovalNotFoundException("Agent run " + runId + " was not found."));
        ensureNoDecisionAlreadyRecorded(run);
        PurchasingRecommendation recommendation = recommendation(run);
        if ((run.getStatus() != AgentRunStatus.COMPLETED && run.getStatus() != AgentRunStatus.NEEDS_INPUT)
                || recommendation == null) {
            throw new ApprovalConflictException("Only a finished AgentRun with a persisted recommendation can be rejected.");
        }
        String approver = requiredText(request.approverIdentity(), "Approver identity is required.");
        String reason = requiredText(request.reason(), "A rejection reason is required.");
        UUID approvalId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(clock);
        PurchasingApproval approval = new PurchasingApproval(approvalId, run, ApprovalStatus.REJECTED, approver,
                now, null, null, reason, null, null, null, null, null);
        approvalRepository.saveAndFlush(approval);
        auditService.appendNext(runId, AgentEventType.APPROVAL_REQUESTED, null,
                Map.of("approvalId", approvalId, "approverIdentity", approver, "action", "REJECT"), null,
                "A human requested rejection of the AgentRun recommendation.");
        auditService.appendNext(runId, AgentEventType.APPROVAL_REJECTED, null,
                Map.of("approvalId", approvalId, "reason", reason), null,
                "Human rejection recorded; the recommendation cannot be executed.");
        return toResponse(approval);
    }

    @Transactional(readOnly = true)
    public ApprovalResponse get(UUID approvalId) {
        PurchasingApproval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new ApprovalNotFoundException("Approval " + approvalId + " was not found."));
        return toResponse(approval);
    }

    private EvidenceCapture captureEvidenceRaw(AgentRun run, Long quantity) {
        InvestigationRequest request = purchasingContext.request(run, quantity, LocalDate.now(clock));
        PurchasingEvidenceSnapshot snapshot = snapshotService.investigate(request);
        JsonNode tree = objectMapper.valueToTree(snapshot);
        if (tree instanceof ObjectNode object) object.remove("collectedAt");
        JsonNode canonical = canonicalize(tree);
        String json = canonical.toString();
        return new EvidenceCapture(snapshot, json, sha256(json));
    }

    void requireAllConstraintsPass(PurchasingEvidenceSnapshot snapshot, Long offerId, Long quantity) {
        ConstraintEvaluation evaluation = constraintService.evaluate(snapshot, offerId, quantity);
        List<String> failures = evaluation.results().stream()
                .filter(result -> result.status() == null || result.status().name().equals("FAIL")
                        || result.status().name().equals("NEEDS_INVESTIGATION"))
                .map(result -> result.constraintName() + ": " + result.explanation())
                .toList();
        if (!failures.isEmpty() || snapshot.issues().stream().anyMatch(this::blocksExecution)) {
            List<String> all = new ArrayList<>(failures);
            snapshot.issues().stream().filter(this::blocksExecution).map(EvidenceIssue::message).forEach(all::add);
            throw new ApprovalConflictException("Current purchasing constraints are not all satisfied: "
                    + String.join("; ", all));
        }
    }

    static String idempotencyKey(UUID approvalId) {
        return "procureai-approval-" + approvalId;
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    List<String> changedEvidenceSections(String approvedJson, String currentJson) {
        try {
            JsonNode old = objectMapper.readTree(approvedJson);
            JsonNode current = objectMapper.readTree(currentJson);
            TreeMap<String, Boolean> sections = new TreeMap<>();
            old.fieldNames().forEachRemaining(field -> sections.put(field, true));
            current.fieldNames().forEachRemaining(field -> sections.put(field, true));
            return sections.keySet().stream().filter(field -> !java.util.Objects.equals(old.get(field), current.get(field)))
                    .sorted(Comparator.naturalOrder()).toList();
        } catch (Exception exception) {
            return List.of("evidence_snapshot");
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            node.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((key, value) -> sorted.set(key, canonicalize(value)));
            return sorted;
        }
        if (node.isArray()) {
            List<JsonNode> values = new ArrayList<>();
            node.forEach(value -> values.add(canonicalize(value)));
            values.sort(Comparator.comparing(JsonNode::toString));
            ArrayNode sorted = objectMapper.createArrayNode();
            values.forEach(sorted::add);
            return sorted;
        }
        return node;
    }

    private boolean blocksExecution(EvidenceIssue issue) {
        return issue.code() != null;
    }

    EvidenceCapture captureCurrentEvidence(AgentRun run, Long quantity) {
        try {
            return captureEvidenceRaw(run, quantity);
        } catch (ApprovalConflictException exception) {
            throw exception;
        } catch (InvestigationException exception) {
            throw new ApprovalConflictException("Current evidence could not be revalidated: " + exception.getMessage());
        }
    }

    private void validateOfferForRun(SupplierProduct offer, AgentRun run) {
        if (offer.getProduct() == null || !offer.getProduct().getId().equals(run.getProduct().getId())
                || offer.getWarehouse() == null || !offer.getWarehouse().getId().equals(run.getWarehouse().getId())
                || offer.getSupplier() == null) {
            throw new ApprovalConflictException("The selected offer does not match this AgentRun's product and warehouse.");
        }
        if (!Boolean.TRUE.equals(run.getProduct().getActive()) || !Boolean.TRUE.equals(run.getWarehouse().getActive())
                || offer.getStatus() != SupplierOfferStatus.AVAILABLE
                || offer.getSupplier().getStatus() != SupplierStatus.ACTIVE) {
            throw new ApprovalConflictException("The product, warehouse, supplier, or selected offer is not active.");
        }
        if (offer.getMinimumOrderQuantity() == null || offer.getMinimumOrderQuantity() <= 0
                || offer.getPackSize() == null || offer.getPackSize() <= 0
                || offer.getLeadTimeDays() == null || offer.getLeadTimeDays() < 0
                || offer.getUnitPrice() == null || offer.getUnitPrice().signum() < 0
                || offer.getCurrencyCode() == null || offer.getCurrencyCode().length() != 3) {
            throw new ApprovalConflictException("The selected offer has incomplete or invalid purchasing terms.");
        }
    }

    private PurchasingRecommendation recommendation(AgentRun run) {
        AgentEvent event = eventRepository.findByAgentRunOrderBySequenceNumberAsc(run).stream()
                .filter(candidate -> candidate.getEventType() == AgentEventType.DECISION_PROPOSED)
                .reduce((first, next) -> next).orElse(null);
        if (event == null || event.getResultJson() == null) return null;
        try {
            return objectMapper.readValue(event.getResultJson(), PurchasingRecommendation.class);
        } catch (Exception exception) {
            throw new ApprovalConflictException("The stored recommendation is malformed and cannot be approved.");
        }
    }

    private void ensureNoDecisionAlreadyRecorded(AgentRun run) {
        if (approvalRepository.existsByAgentRun(run)) {
            throw new ApprovalConflictException("This AgentRun already has an approval or rejection decision.");
        }
    }

    private String requiredText(String value, String message) {
        if (value == null || value.isBlank()) throw new ApprovalConflictException(message);
        return value.trim();
    }

    ApprovalResponse toResponse(PurchasingApproval approval) {
        SupplierProduct offer = approval.getSupplierProduct();
        return new ApprovalResponse(approval.getId(), approval.getAgentRun().getId(), approval.getApprovalStatus(),
                approval.getExecutionStatus(), approval.getApprovedQuantity(), offer == null ? null : offer.getId(),
                offer == null || offer.getSupplier() == null ? null : offer.getSupplier().getId(),
                offer == null || offer.getSupplier() == null ? null : offer.getSupplier().getCode(),
                approval.getApproverIdentity(), approval.getDecisionAt(), approval.getRejectionReason(),
                approval.getIdempotencyKey(), approval.getPurchaseOrder() == null ? null : approval.getPurchaseOrder().getId(),
                approval.getPurchaseOrder() == null ? null : approval.getPurchaseOrder().getPoNumber(),
                approval.getExecutionSummary(), approval.getExecutionAt());
    }

    record EvidenceCapture(PurchasingEvidenceSnapshot snapshot, String json, String fingerprint) { }
}
