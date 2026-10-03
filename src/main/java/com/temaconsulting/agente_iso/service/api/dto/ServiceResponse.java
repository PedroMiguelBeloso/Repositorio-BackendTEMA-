package com.temaconsulting.agente_iso.service.api.dto;

import com.temaconsulting.agente_iso.service.domain.ServiceStatus;
import com.temaconsulting.agente_iso.service.domain.ServiceType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ServiceResponse(
        UUID id, String name, String client, ServiceType serviceType, ServiceStatus status,
        int revision, List<ServiceProcessResponse> processes, ServiceSourceResponse sources,
        Instant createdAt, Instant updatedAt
) {
}
