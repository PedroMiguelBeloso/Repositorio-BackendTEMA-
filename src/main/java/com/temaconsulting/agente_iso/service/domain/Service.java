package com.temaconsulting.agente_iso.service.domain;

import com.temaconsulting.agente_iso.service.domain.error.ServiceValidationException;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record Service(
        UUID id,
        String name,
        String client,
        ServiceType serviceType,
        ServiceStatus status,
        int revision,
        List<ServiceProcessAssignment> processes,
        ServiceSources sources,
        Instant createdAt,
        Instant updatedAt
) {
    public Service {
        Objects.requireNonNull(id, "id is required");
        name = requiredText(name, "name");
        client = requiredText(client, "client");
        if (serviceType == null) {
            throw new ServiceValidationException("serviceType", "Service type is required");
        }
        if (status == null) {
            throw new ServiceValidationException("status", "Service status is required");
        }
        if (revision < 1) {
            throw new ServiceValidationException("revision", "Revision must be positive");
        }
        if (processes == null) {
            throw new ServiceValidationException("processVersionIds", "Process versions are required");
        }
        processes = List.copyOf(processes);
        Set<UUID> processIds = new HashSet<>();
        for (ServiceProcessAssignment assignment : processes) {
            if (!processIds.add(assignment.processId())) {
                throw new ServiceValidationException(
                        "processVersionIds", "Only one version of each process may be assigned"
                );
            }
        }
        Objects.requireNonNull(sources, "sources are required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    public static Service create(
            UUID id, String name, String client, ServiceType type, ServiceStatus status,
            List<ServiceProcessAssignment> processes, ServiceSources sources, Instant now
    ) {
        return new Service(id, name, client, type, status, 1, processes, sources, now, now);
    }

    public Service replace(
            String name, String client, ServiceType type, ServiceStatus status,
            List<ServiceProcessAssignment> processes, ServiceSources sources, Instant now
    ) {
        return new Service(id, name, client, type, status, revision + 1, processes, sources, createdAt, now);
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ServiceValidationException(field, field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > 255) {
            throw new ServiceValidationException(field, field + " must have at most 255 characters");
        }
        return normalized;
    }
}
