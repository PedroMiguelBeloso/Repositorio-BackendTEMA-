package com.temaconsulting.agente_iso.verification.application.model;

import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;

public record VerificationResultDecisionTarget(
        String runStatus,
        int revision,
        ApplicabilityDecision effectiveApplicability,
        ApplicabilityMode applicabilityMode
) {
}
