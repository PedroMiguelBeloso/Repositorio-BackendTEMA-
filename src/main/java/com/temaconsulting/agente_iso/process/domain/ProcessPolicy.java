package com.temaconsulting.agente_iso.process.domain;

import com.temaconsulting.agente_iso.process.domain.error.DomainValidationException;

import java.util.Objects;
import java.util.UUID;

public record ProcessPolicy(
        UUID id,
        int order,
        String text
) {
    public ProcessPolicy {
        Objects.requireNonNull(id, "id is required");
        if (order < 0) {
            throw new DomainValidationException("policies.order", "Policy order must be greater than or equal to 0");
        }
        text = requireText(text, "policies.text", "Policy text is required");
    }

    private static String requireText(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(field, message);
        }
        return value.trim();
    }
}
