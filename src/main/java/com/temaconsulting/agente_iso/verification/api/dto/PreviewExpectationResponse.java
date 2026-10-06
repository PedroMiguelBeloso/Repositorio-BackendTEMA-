package com.temaconsulting.agente_iso.verification.api.dto;

import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.process.domain.EvidenceType;

import java.util.UUID;

public record PreviewExpectationResponse(
        UUID id,
        UUID macroActivityId,
        UUID activityId,
        EvidenceLocation location,
        String displayName,
        EvidenceType evidenceType,
        ApplicabilityMode applicability,
        Cardinality cardinality,
        String ticketSelectionGroupId
) {
}
