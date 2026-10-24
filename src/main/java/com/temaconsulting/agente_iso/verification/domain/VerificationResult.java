package com.temaconsulting.agente_iso.verification.domain;

import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.verification.application.model.ApplicabilityAssessment;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record VerificationResult(
        UUID id,
        UUID runId,
        UUID expectationId,
        UUID macroActivityId,
        UUID activityId,
        String displayName,
        EvidenceType evidenceType,
        EvidenceLocation location,
        String subjectTicketId,
        ApplicabilityAssessment applicability,
        ResultStatus proposedStatus,
        ResultStatus effectiveStatus,
        String rationale,
        List<String> limitations,
        List<VerificationEvidenceReference> evidence,
        List<HumanDecisionEvent> humanDecisions,
        int revision
) {
    public VerificationResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(expectationId, "expectationId");
        Objects.requireNonNull(macroActivityId, "macroActivityId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(evidenceType, "evidenceType");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(applicability, "applicability");
        Objects.requireNonNull(proposedStatus, "proposedStatus");
        Objects.requireNonNull(effectiveStatus, "effectiveStatus");
        Objects.requireNonNull(rationale, "rationale");
        limitations = limitations == null ? List.of() : List.copyOf(limitationValues(limitations));
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        humanDecisions = humanDecisions == null ? List.of() : List.copyOf(humanDecisions);
    }

    private static List<String> limitationValues(List<String> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
