package com.temaconsulting.agente_iso.process.api.dto;

import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EvidenceExpectationInput(
        UUID id,
        @NotNull UUID registryDefinitionId,
        String displayName,
        @NotNull ApplicabilityMode applicability,
        @NotNull Cardinality cardinality,
        @Min(0) int order
) {
}
