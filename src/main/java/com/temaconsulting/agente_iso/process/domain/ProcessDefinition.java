package com.temaconsulting.agente_iso.process.domain;

import com.temaconsulting.agente_iso.process.domain.error.DomainValidationException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ProcessDefinition(
        UUID id,
        ProcessCode code,
        String name,
        boolean m2Enabled,
        Instant createdAt
) {
    public ProcessDefinition {
        Objects.requireNonNull(id, "id is required");
        if (code == null) {
            throw new DomainValidationException("code", "Process code is required");
        }
        name = requireText(name, "name", "Process name is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        if (m2Enabled != code.isM2Enabled()) {
            throw new DomainValidationException(
                    "m2Enabled",
                    "M2 availability must be derived from the process code"
            );
        }
    }

    public static ProcessDefinition create(UUID id, ProcessCode code, String name, Instant createdAt) {
        if (code == null) {
            throw new DomainValidationException("code", "Process code is required");
        }
        return new ProcessDefinition(id, code, name, code.isM2Enabled(), createdAt);
    }

    public boolean isVerifiable() {
        return m2Enabled;
    }

    private static String requireText(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(field, message);
        }
        return value.trim();
    }
}
