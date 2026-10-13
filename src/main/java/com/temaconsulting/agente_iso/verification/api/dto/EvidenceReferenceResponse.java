package com.temaconsulting.agente_iso.verification.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.temaconsulting.agente_iso.integration.domain.IntegrationSource;
import com.temaconsulting.agente_iso.process.domain.EvidenceType;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvidenceReferenceResponse(
        EvidenceType kind,
        IntegrationSource source,
        String externalId,
        String displayName,
        String webUrl,
        Instant observedAt,
        String ticketId
) {
}
