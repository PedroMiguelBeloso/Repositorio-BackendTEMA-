package com.temaconsulting.agente_iso.verification.application;

/** AI is a fallback for case-dependent applicability, never a compliance evaluator. */
public interface ApplicabilityAiPort {
    AiApplicabilityAssessment assess(AiApplicabilityRequest request);
}
