package com.procureai.agent.model;

public class ModelGatewayException extends RuntimeException {

    public enum Kind { AUTHENTICATION, RATE_LIMIT, TIMEOUT, API_FAILURE }

    private final Kind kind;

    public ModelGatewayException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() { return kind; }
}
