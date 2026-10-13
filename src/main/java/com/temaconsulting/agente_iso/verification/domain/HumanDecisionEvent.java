package com.temaconsulting.agente_iso.verification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record HumanDecisionEvent(
        UUID id,
        HumanDecisionType decisionType,
        HumanApplicabilityDecision applicabilityDecision,
        ResultStatus finalStatus,
        String rationale,
        UUID decidedBy,
        Instant decidedAt
) {
    public HumanDecisionEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(decisionType, "decisionType");
        Objects.requireNonNull(rationale, "rationale");
        Objects.requireNonNull(decidedBy, "decidedBy");
        Objects.requireNonNull(decidedAt, "decidedAt");
    }
}
