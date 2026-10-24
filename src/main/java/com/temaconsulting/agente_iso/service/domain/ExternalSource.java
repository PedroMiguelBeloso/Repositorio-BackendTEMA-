package com.temaconsulting.agente_iso.service.domain;

import com.temaconsulting.agente_iso.service.domain.error.ServiceValidationException;

public record ExternalSource(String id, String name) {
    public ExternalSource {
        if (id == null || id.isBlank()) {
            throw new ServiceValidationException("sources", "Source ID is required");
        }
        if (name == null || name.isBlank()) {
            throw new ServiceValidationException("sources", "Source name is required");
        }
        id = id.trim();
        name = name.trim();
        if (id.length() > 255 || name.length() > 255) {
            throw new ServiceValidationException("sources", "Source ID and name must have at most 255 characters");
        }
    }
}
