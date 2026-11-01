package com.temaconsulting.agente_iso.verification.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.process.domain.EvidenceType;
import com.temaconsulting.agente_iso.verification.domain.ResultStatus;

import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VerificationResultResponse(
        UUID id,
        UUID runId,
        UUID expectationId,
        UUID macroActivityId,
        UUID activityId,
        String displayName,
        EvidenceType evidenceType,
        EvidenceLocation location,
        String subjectTicketId,
        ApplicabilityAssessmentResponse applicability,
        ResultStatus proposedStatus,
        ResultStatus effectiveStatus,
        ReviewReasonCode reviewReason,
        EvidenceSearchStatus searchStatus,
        String rationale,
        List<String> limitations,
        List<EvidenceReferenceResponse> evidence,
        List<HumanDecisionEventResponse> humanDecisions,
        int revision
) {
    public VerificationResultResponse {
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        humanDecisions = humanDecisions == null ? List.of() : List.copyOf(humanDecisions);
    }
}
