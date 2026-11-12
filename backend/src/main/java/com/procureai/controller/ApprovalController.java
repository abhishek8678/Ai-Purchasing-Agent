package com.procureai.controller;

import java.util.UUID;

import com.procureai.approval.dto.ApprovePurchaseRequest;
import com.procureai.approval.dto.ApprovalResponse;
import com.procureai.approval.dto.RejectPurchaseRequest;
import com.procureai.approval.service.ApprovalService;
import com.procureai.approval.service.PurchaseExecutionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final PurchaseExecutionService executionService;

    public ApprovalController(ApprovalService approvalService, PurchaseExecutionService executionService) {
        this.approvalService = approvalService;
        this.executionService = executionService;
    }

    @PostMapping("/agent-runs/{runId}/approve")
    @ResponseStatus(HttpStatus.CREATED)
    public ApprovalResponse approve(@PathVariable UUID runId, @Valid @RequestBody ApprovePurchaseRequest request) {
        return approvalService.approve(runId, request);
    }

    @PostMapping("/agent-runs/{runId}/reject")
    @ResponseStatus(HttpStatus.CREATED)
    public ApprovalResponse reject(@PathVariable UUID runId, @Valid @RequestBody RejectPurchaseRequest request) {
        return approvalService.reject(runId, request);
    }

    @GetMapping("/approvals/{approvalId}")
    public ApprovalResponse get(@PathVariable UUID approvalId) {
        return approvalService.get(approvalId);
    }

    /** Execution is always a separate explicit human request; approval and retrieval have no PO side effects. */
    @PostMapping("/approvals/{approvalId}/execute")
    public ApprovalResponse execute(@PathVariable UUID approvalId) {
        return executionService.execute(approvalId);
    }
}
