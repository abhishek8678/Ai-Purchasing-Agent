package com.procureai.agent.model;

import java.util.Map;

public class ModelGatewayException extends RuntimeException {

    public enum Kind { AUTHENTICATION, RATE_LIMIT, TIMEOUT, API_FAILURE }

    private final Kind kind;
    private final Map<String, Object> diagnostic;

    public ModelGatewayException(Kind kind, String message, Throwable cause) {
        this(kind, message, cause, Map.of());
    }

    public ModelGatewayException(Kind kind, String message, Throwable cause, Map<String, Object> diagnostic) {
        super(message, cause);
        this.kind = kind;
        this.diagnostic = Map.copyOf(diagnostic == null ? Map.of() : diagnostic);
    }

    public Kind getKind() { return kind; }
    public Map<String, Object> getDiagnostic() { return diagnostic; }
}
