package com.temaconsulting.agente_iso.service.api.dto;

import java.util.List;
import java.util.UUID;

public record VerifiableProcessResponse(
        UUID processVersionId, String processName, String officialVersion,
        List<MacroActivityResponse> macroActivities
) {
    public record MacroActivityResponse(UUID id, String name) {
    }
}
