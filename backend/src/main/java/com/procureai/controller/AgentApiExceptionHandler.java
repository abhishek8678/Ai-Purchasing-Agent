package com.procureai.controller;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import com.procureai.agent.exception.AgentConfigurationException;
import com.procureai.agent.exception.AgentNotFoundException;
import com.procureai.approval.exception.ApprovalConflictException;
import com.procureai.approval.exception.ApprovalNotFoundException;
import com.procureai.service.exception.InvestigationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AgentApiExceptionHandler {

    private final Clock clock;

    public AgentApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(AgentConfigurationException.class)
    public ResponseEntity<ApiErrorResponse> configuration(AgentConfigurationException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "GEMINI_CONFIGURATION_MISSING", exception.getMessage(), List.of());
    }

    @ExceptionHandler(AgentNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> notFound(AgentNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "AGENT_RUN_NOT_FOUND", exception.getMessage(), List.of());
    }

    @ExceptionHandler(ApprovalNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> approvalNotFound(ApprovalNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "APPROVAL_NOT_FOUND", exception.getMessage(), List.of());
    }

    @ExceptionHandler(ApprovalConflictException.class)
    public ResponseEntity<ApiErrorResponse> approvalConflict(ApprovalConflictException exception) {
        return response(HttpStatus.CONFLICT, "APPROVAL_CONFLICT", exception.getMessage(), List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> dataConflict(DataIntegrityViolationException exception) {
        return response(HttpStatus.CONFLICT, "PURCHASING_DATA_CONFLICT",
                "A conflicting approval or purchase-order record already exists.", List.of());
    }

    @ExceptionHandler(InvestigationException.class)
    public ResponseEntity<ApiErrorResponse> investigation(InvestigationException exception) {
        HttpStatus status = "UNKNOWN_PRODUCT".equals(exception.getCode())
                || "UNKNOWN_WAREHOUSE".equals(exception.getCode()) ? HttpStatus.NOT_FOUND : HttpStatus.UNPROCESSABLE_ENTITY;
        return response(status, exception.getCode(), exception.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> invalidRequest(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(this::fieldError).distinct().toList();
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request body is invalid.", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> malformedJson(HttpMessageNotReadableException exception) {
        return response(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request body is not valid JSON or contains invalid field values.", List.of());
    }

    private String fieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String code, String message,
                                                       List<String> details) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message, details,
                LocalDateTime.now(clock)));
    }

    public record ApiErrorResponse(String errorCode, String message, List<String> details,
                                   LocalDateTime timestamp) {
    }
}
