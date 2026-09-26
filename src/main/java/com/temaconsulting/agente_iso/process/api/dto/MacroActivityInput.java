package com.temaconsulting.agente_iso.process.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record MacroActivityInput(
        UUID id,
        @NotBlank String name,
        String description,
        @Min(0) int order,
        @NotNull List<@Valid EvidenceExpectationInput> inputs,
        @NotNull List<@Valid ActivityInput> activities,
        @NotNull List<@Valid EvidenceExpectationInput> outputs
) {
    public MacroActivityInput {
        inputs = inputs == null ? null : List.copyOf(inputs);
        activities = activities == null ? null : List.copyOf(activities);
        outputs = outputs == null ? null : List.copyOf(outputs);
    }
}
