package com.temaconsulting.agente_iso.process.api.dto;

import com.temaconsulting.agente_iso.process.domain.ProcessStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProcessVersionResponse(
        UUID id,
        UUID processId,
        String officialVersion,
        LocalDate versionDate,
        ProcessStatus status,
        int revision,
        String objective,
        String owner,
        List<ProcessPolicyResponse> policies,
        List<MacroActivityResponse> macroActivities,
        Instant publishedAt,
        Instant createdAt
) {
    public ProcessVersionResponse {
        policies = List.copyOf(policies);
        macroActivities = List.copyOf(macroActivities);
    }
}
