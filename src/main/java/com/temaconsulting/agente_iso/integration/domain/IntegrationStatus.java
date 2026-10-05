package com.temaconsulting.agente_iso.integration.domain;

import java.time.Instant;
import java.util.Objects;

public record IntegrationStatus(
        IntegrationSource source,
        SourceHealth status,
        Instant checkedAt,
        String message
) {
    public IntegrationStatus {
        Objects.requireNonNull(source);
        Objects.requireNonNull(status);
        Objects.requireNonNull(checkedAt);
        Objects.requireNonNull(message);
    }
}
