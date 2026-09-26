package com.temaconsulting.agente_iso.process.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record SaveDraftRequest(
        @Min(0) int revision,
        @NotBlank String officialVersion,
        @NotNull LocalDate versionDate,
        @NotBlank String objective,
        @NotBlank String owner,
        @NotNull List<@Valid ProcessPolicyInput> policies,
        @NotNull List<@Valid MacroActivityInput> macroActivities
) {
    public SaveDraftRequest {
        policies = policies == null ? null : List.copyOf(policies);
        macroActivities = macroActivities == null ? null : List.copyOf(macroActivities);
    }
}
