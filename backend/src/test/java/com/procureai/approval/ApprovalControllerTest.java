package com.procureai.approval;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.procureai.approval.dto.ApprovalResponse;
import com.procureai.approval.exception.ApprovalConflictException;
import com.procureai.approval.exception.ApprovalNotFoundException;
import com.procureai.approval.service.ApprovalService;
import com.procureai.approval.service.PurchaseExecutionService;
import com.procureai.controller.AgentApiExceptionHandler;
import com.procureai.controller.ApprovalController;
import com.procureai.domain.enums.ApprovalStatus;
import com.procureai.domain.enums.ExecutionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ApprovalController.class)
@Import({AgentApiExceptionHandler.class, ApprovalControllerTest.ClockConfiguration.class})
class ApprovalControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private ApprovalService approvalService;
    @MockitoBean private PurchaseExecutionService executionService;

    @Test
    void approvalAndRetrievalDoNotExecuteAndExecutionRequiresSeparatePost() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        ApprovalResponse approved = response(approvalId, runId, ApprovalStatus.APPROVED,
                ExecutionStatus.NOT_EXECUTED);
        when(approvalService.approve(any(), any())).thenReturn(approved);
        when(approvalService.get(approvalId)).thenReturn(approved);
        when(executionService.execute(approvalId)).thenReturn(response(approvalId, runId,
                ApprovalStatus.APPROVED, ExecutionStatus.SUCCEEDED));
        String body = "{\"approvedQuantity\":100,\"supplierProductId\":1,\"approverIdentity\":\"Demo Buyer\"}";

        mvc.perform(post("/api/agent-runs/{runId}/approve", runId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"))
                .andExpect(jsonPath("$.executionStatus").value("NOT_EXECUTED"));
        verify(executionService, never()).execute(any());

        mvc.perform(get("/api/approvals/{approvalId}", approvalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionStatus").value("NOT_EXECUTED"));
        verify(executionService, never()).execute(any());

        mvc.perform(post("/api/approvals/{approvalId}/execute", approvalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionStatus").value("SUCCEEDED"));
        verify(executionService).execute(approvalId);
    }

    @Test
    void rejectsInvalidRequestsAndMapsDuplicateOrMissingApprovalErrors() throws Exception {
        UUID runId = UUID.randomUUID();
        String invalidApproval = "{\"approvedQuantity\":0,\"supplierProductId\":-1,\"approverIdentity\":\" \"}";
        mvc.perform(post("/api/agent-runs/{runId}/approve", runId)
                        .contentType(MediaType.APPLICATION_JSON).content(invalidApproval))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        when(approvalService.approve(any(), any()))
                .thenThrow(new ApprovalConflictException("This AgentRun already has an approval or rejection decision."));
        String validApproval = "{\"approvedQuantity\":100,\"supplierProductId\":1,\"approverIdentity\":\"Demo Buyer\"}";
        mvc.perform(post("/api/agent-runs/{runId}/approve", runId)
                        .contentType(MediaType.APPLICATION_JSON).content(validApproval))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("APPROVAL_CONFLICT"));

        UUID unknownId = UUID.randomUUID();
        when(approvalService.get(unknownId)).thenThrow(new ApprovalNotFoundException("Approval was not found."));
        mvc.perform(get("/api/approvals/{approvalId}", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("APPROVAL_NOT_FOUND"));
    }

    private ApprovalResponse response(UUID approvalId, UUID runId, ApprovalStatus approvalStatus,
                                       ExecutionStatus executionStatus) {
        return new ApprovalResponse(approvalId, runId, approvalStatus, executionStatus, 100L, 1L, 10L,
                "SUP-TEST", "Demo Buyer", LocalDateTime.now(), null, "test-key",
                executionStatus == ExecutionStatus.SUCCEEDED ? 44L : null,
                executionStatus == ExecutionStatus.SUCCEEDED ? "PA-test" : null,
                "Test response", LocalDateTime.now());
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        Clock clock() { return Clock.system(ZoneOffset.UTC); }
    }
}
