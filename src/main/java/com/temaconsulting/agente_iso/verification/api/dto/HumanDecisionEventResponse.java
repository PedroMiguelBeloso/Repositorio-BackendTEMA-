package com.temaconsulting.agente_iso.verification.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.temaconsulting.agente_iso.verification.domain.HumanApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.HumanDecisionType;
import com.temaconsulting.agente_iso.verification.domain.ResultStatus;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HumanDecisionEventResponse(
        UUID id,
        HumanDecisionType decisionType,
        HumanApplicabilityDecision applicabilityDecision,
        ResultStatus finalStatus,
        String rationale,
        UUID decidedBy,
        Instant decidedAt
) {
}
