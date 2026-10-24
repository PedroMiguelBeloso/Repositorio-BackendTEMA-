package com.temaconsulting.agente_iso.service.application.model;

import com.temaconsulting.agente_iso.service.domain.ServiceStatus;
import com.temaconsulting.agente_iso.service.domain.ServiceType;

import java.util.List;
import java.util.UUID;

public record ServiceWriteCommand(
        Integer revision,
        String name,
        String client,
        ServiceType serviceType,
        ServiceStatus status,
        List<UUID> processVersionIds,
        String driveFolderId,
        String redmineProjectId
) {
}
