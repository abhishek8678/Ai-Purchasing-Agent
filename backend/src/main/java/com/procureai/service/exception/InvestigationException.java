package com.procureai.service.exception;

/** Structured application error for an investigation request or required evidence gap. */
public class InvestigationException extends RuntimeException {

    private final String code;

    public InvestigationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
