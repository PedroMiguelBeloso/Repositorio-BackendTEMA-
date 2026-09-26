package com.temaconsulting.agente_iso.process.api.dto;

import com.temaconsulting.agente_iso.process.domain.ProcessCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProcessRequest(
        @NotNull ProcessCode code,
        @NotBlank String name
) {
}
