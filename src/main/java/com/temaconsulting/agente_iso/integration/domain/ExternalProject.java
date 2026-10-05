package com.temaconsulting.agente_iso.integration.domain;

public record ExternalProject(String id, String name, String identifier) {
    public ExternalProject {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Project ID is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Project name is required");
        }
        id = id.trim();
        name = name.trim();
        identifier = identifier == null ? null : identifier.trim();
    }
}
