package com.temaconsulting.agente_iso.process.api.dto;

import com.temaconsulting.agente_iso.process.domain.ProcessCode;

public record CreateProcessRequest(
        ProcessCode code,
        String name
) {
}
