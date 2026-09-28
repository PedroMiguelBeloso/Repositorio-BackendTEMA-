package com.temaconsulting.agente_iso.process.api.dto;

import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class ProcessDtos {

    public record SaveDraftRequest(
        int revision,
        @NotBlank String officialVersion,
        @NotNull LocalDate versionDate,
        @NotBlank String objective,
        @NotBlank String owner,
        List<ProcessPolicyInput> policies,
        List<MacroActivityInput> macroActivities
    ) {}

    public record ProcessPolicyInput(UUID id, int order, @NotBlank String text) {}

    public record MacroActivityInput(
        UUID id, @NotBlank String name, String description, int order,
        List<EvidenceExpectationInput> inputs,
        List<ActivityInput> activities,
        List<EvidenceExpectationInput> outputs
    ) {}

    public record ActivityInput(
        UUID id, @NotBlank String name, String responsible, @NotBlank String instructions,
        String etaText, int order,
        List<EvidenceExpectationInput> records
    ) {}

    public record EvidenceExpectationInput(
        UUID id, @NotNull UUID registryDefinitionId, @NotBlank String displayName,
        @NotNull ApplicabilityMode applicability, @NotNull Cardinality cardinality, int order
    ) {}
}