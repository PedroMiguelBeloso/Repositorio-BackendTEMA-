package com.temaconsulting.agente_iso.verification.application.port;

import com.temaconsulting.agente_iso.verification.application.model.AiApplicabilityAssessment;
import com.temaconsulting.agente_iso.verification.application.model.AiApplicabilityRequest;

/** AI is a fallback for case-dependent applicability, never a compliance evaluator. */
public interface ApplicabilityAiPort {
    AiApplicabilityAssessment assess(AiApplicabilityRequest request);
}
