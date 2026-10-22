package com.temaconsulting.agente_iso.service.api.dto;

import com.temaconsulting.agente_iso.service.application.model.ServiceWriteCommand;
import com.temaconsulting.agente_iso.service.domain.ServiceStatus;
import com.temaconsulting.agente_iso.service.domain.ServiceType;

import java.util.List;
import java.util.UUID;

public record ServiceWriteRequest(
        Integer revision,
        String name,
        String client,
        ServiceType serviceType,
        ServiceStatus status,
        List<UUID> processVersionIds,
        String driveFolderId,
        String redmineProjectId
) {
    public ServiceWriteCommand toCommand() {
        return new ServiceWriteCommand(
                revision, name, client, serviceType, status, processVersionIds,
                driveFolderId, redmineProjectId
        );
    }
}
