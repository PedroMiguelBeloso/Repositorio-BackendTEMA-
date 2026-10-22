package com.temaconsulting.agente_iso.verification.application.model;

import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** An untrusted AI proposal; the M2 evaluator decides how to use and persist it. */
public record AiApplicabilityAssessment(
        UUID expectationId,
        ApplicabilityDecision applicability,
        String reason,
        List<String> evidenceRefs,
        List<String> limitations,
        String model,
        String responseId
) {
    public AiApplicabilityAssessment {
        Objects.requireNonNull(expectationId, "expectationId");
        Objects.requireNonNull(applicability, "applicability");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        reason = reason.trim();
        evidenceRefs = List.copyOf(Objects.requireNonNull(evidenceRefs, "evidenceRefs"));
        limitations = List.copyOf(Objects.requireNonNull(limitations, "limitations"));
    }

    public static AiApplicabilityAssessment undetermined(
            UUID expectationId, String reason, String limitation, String model
    ) {
        return new AiApplicabilityAssessment(expectationId, ApplicabilityDecision.UNDETERMINED,
                reason, List.of(), List.of(limitation), model, null);
    }
}
