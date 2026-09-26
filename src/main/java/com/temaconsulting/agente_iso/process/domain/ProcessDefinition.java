package com.temaconsulting.agente_iso.process.domain;

import java.time.Instant;
import java.util.UUID;

public record ProcessDefinition(
        UUID id,
        ProcessCode code,
        String name,
        boolean m2Enabled,
        Instant createdAt
) {
    public boolean isVerifiable() {
        return m2Enabled;
    }
}
