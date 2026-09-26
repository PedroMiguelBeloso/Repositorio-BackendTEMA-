package com.temaconsulting.agente_iso.process.api.dto;

import java.util.List;
import java.util.UUID;

public record ActivityResponse(
        UUID id,
        String name,
        String responsible,
        String instructions,
        String etaText,
        int order,
        List<EvidenceExpectationResponse> records
) {
    public ActivityResponse {
        records = List.copyOf(records);
    }
}
