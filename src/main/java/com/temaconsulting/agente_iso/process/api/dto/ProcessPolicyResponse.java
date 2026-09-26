package com.temaconsulting.agente_iso.process.api.dto;

import java.util.UUID;

public record ProcessPolicyResponse(
        UUID id,
        int order,
        String text
) {
}
