package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.verification.application.model.ApplicabilityAssessment;
import com.temaconsulting.agente_iso.verification.application.model.EvaluationContext;

public interface ApplicabilityPolicy {
    ApplicabilityAssessment assess(EvaluationContext context);
}
