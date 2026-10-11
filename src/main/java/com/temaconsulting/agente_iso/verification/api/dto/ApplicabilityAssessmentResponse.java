package com.temaconsulting.agente_iso.verification.api.dto;

import com.temaconsulting.agente_iso.verification.application.ApplicabilityAssessment;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.DecisionOrigin;

import java.util.List;

public record ApplicabilityAssessmentResponse(
        ApplicabilityDecision decision,
        DecisionOrigin origin,
        String rationale,
        List<String> limitations
) {
    public ApplicabilityAssessmentResponse {
        decision = java.util.Objects.requireNonNull(decision, "decision");
        origin = java.util.Objects.requireNonNull(origin, "origin");
        rationale = java.util.Objects.requireNonNullElse(rationale, "").trim();
        limitations = limitations == null ? List.of() : List.copyOf(limitationValues(limitations));
    }

    public static ApplicabilityAssessmentResponse from(ApplicabilityAssessment source) {
        return new ApplicabilityAssessmentResponse(
                source.decision(),
                source.origin(),
                source.rationale(),
                source.limitations()
        );
    }

    private static List<String> limitationValues(List<String> values) {
        return values.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
