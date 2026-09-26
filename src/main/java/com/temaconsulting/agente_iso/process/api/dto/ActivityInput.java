package com.temaconsulting.agente_iso.process.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ActivityInput(
        UUID id,
        @NotBlank String name,
        String responsible,
        @NotBlank String instructions,
        String etaText,
        @Min(0) int order,
        @NotNull List<@Valid EvidenceExpectationInput> records
) {
    public ActivityInput {
        records = records == null ? null : List.copyOf(records);
    }
}
