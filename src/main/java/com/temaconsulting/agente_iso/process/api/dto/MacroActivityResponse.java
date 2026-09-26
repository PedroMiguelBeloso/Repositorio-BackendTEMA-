package com.temaconsulting.agente_iso.process.api.dto;

import java.util.List;
import java.util.UUID;

public record MacroActivityResponse(
        UUID id,
        String name,
        String description,
        int order,
        List<EvidenceExpectationResponse> inputs,
        List<ActivityResponse> activities,
        List<EvidenceExpectationResponse> outputs
) {
    public MacroActivityResponse {
        inputs = List.copyOf(inputs);
        activities = List.copyOf(activities);
        outputs = List.copyOf(outputs);
    }
}
