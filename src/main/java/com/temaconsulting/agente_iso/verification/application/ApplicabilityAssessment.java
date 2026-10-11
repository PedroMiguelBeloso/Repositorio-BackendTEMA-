package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.DecisionOrigin;

import java.util.List;
import java.util.Objects;

public record ApplicabilityAssessment(
        ApplicabilityDecision decision,
        DecisionOrigin origin,
        String rationale,
        List<String> limitations
) {
    public ApplicabilityAssessment {
        Objects.requireNonNull(decision, "decision is required");
        Objects.requireNonNull(origin, "origin is required");
        rationale = normalizeText(rationale, "rationale");
        limitations = limitations == null ? List.of() : List.copyOf(limitationValues(limitations));
    }

    public static ApplicabilityAssessment deterministic(ApplicabilityDecision decision, String rationale) {
        return new ApplicabilityAssessment(decision, DecisionOrigin.DETERMINISTIC, rationale, List.of());
    }

    public static ApplicabilityAssessment ai(ApplicabilityDecision decision, String rationale, List<String> limitations) {
        return new ApplicabilityAssessment(decision, DecisionOrigin.AI_APPLICABILITY, rationale, limitations);
    }

    public static ApplicabilityAssessment notEvaluated(String rationale, String limitation) {
        return new ApplicabilityAssessment(
                ApplicabilityDecision.UNDETERMINED,
                DecisionOrigin.NOT_EVALUATED,
                rationale,
                limitation == null || limitation.isBlank() ? List.of() : List.of(limitation)
        );
    }

    public static ApplicabilityAssessment notEvaluated(String rationale, List<String> limitations) {
        return new ApplicabilityAssessment(
                ApplicabilityDecision.UNDETERMINED,
                DecisionOrigin.NOT_EVALUATED,
                rationale,
                limitations
        );
    }

    private static String normalizeText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static List<String> limitationValues(List<String> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
