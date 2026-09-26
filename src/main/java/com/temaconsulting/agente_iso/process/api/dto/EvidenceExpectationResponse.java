package com.temaconsulting.agente_iso.process.api.dto;

import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.process.domain.EvidenceType;

import java.util.UUID;

public record EvidenceExpectationResponse(
        UUID id,
        UUID registryDefinitionId,
        String displayName,
        EvidenceType evidenceType,
        String officialNomenclature,
        ApplicabilityMode applicability,
        Cardinality cardinality,
        EvidenceLocation location,
        int order
) {
}
