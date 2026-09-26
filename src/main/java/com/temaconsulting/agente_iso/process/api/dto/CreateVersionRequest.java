package com.temaconsulting.agente_iso.process.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateVersionRequest(
        @NotBlank String officialVersion,
        @NotNull LocalDate versionDate,
        UUID copyFromVersionId
) {
}
