package com.temaconsulting.agente_iso.verification.application;

public interface ApplicabilityPolicy {
    ApplicabilityAssessment assess(EvaluationContext context);
}
