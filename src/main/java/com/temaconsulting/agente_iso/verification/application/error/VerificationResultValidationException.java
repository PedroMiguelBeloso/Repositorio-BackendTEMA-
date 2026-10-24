package com.temaconsulting.agente_iso.verification.application.error;

public class VerificationResultValidationException extends RuntimeException {
    private final String field;

    public VerificationResultValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
