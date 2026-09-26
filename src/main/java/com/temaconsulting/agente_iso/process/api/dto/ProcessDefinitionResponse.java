package com.temaconsulting.agente_iso.process.api.dto;

import com.temaconsulting.agente_iso.process.domain.ProcessCode;

import java.time.Instant;
import java.util.UUID;

public record ProcessDefinitionResponse(
        UUID id,
        ProcessCode code,
        String name,
        boolean m2Enabled,
        Instant createdAt
) {
}
