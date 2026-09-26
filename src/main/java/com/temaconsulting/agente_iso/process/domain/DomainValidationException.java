package com.temaconsulting.agente_iso.process.domain;

public class DomainValidationException extends RuntimeException {
    private final String field;

    public DomainValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
