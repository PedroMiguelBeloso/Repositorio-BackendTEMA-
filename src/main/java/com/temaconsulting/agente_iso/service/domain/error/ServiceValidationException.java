package com.temaconsulting.agente_iso.service.domain.error;

public class ServiceValidationException extends RuntimeException {
    private final String field;

    public ServiceValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
