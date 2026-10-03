package com.temaconsulting.agente_iso.service.api.dto;

import java.util.UUID;

public record ServiceProcessResponse(
        UUID processVersionId, UUID processId, String processName,
        String officialVersion, boolean m2Enabled
) {
}
