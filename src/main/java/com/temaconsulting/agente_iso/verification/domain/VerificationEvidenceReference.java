package com.temaconsulting.agente_iso.verification.domain;

import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.integration.domain.IntegrationSource;

import java.time.Instant;
import java.util.Objects;

public record VerificationEvidenceReference(
        EvidenceType kind,
        IntegrationSource source,
        String externalId,
        String displayName,
        String webUrl,
        Instant observedAt,
        String ticketId
) {
    public VerificationEvidenceReference {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(externalId, "externalId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(observedAt, "observedAt");
    }
}
