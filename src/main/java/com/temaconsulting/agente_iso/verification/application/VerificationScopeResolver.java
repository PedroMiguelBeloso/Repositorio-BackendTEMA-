package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.verification.domain.VerificationPreview;
import com.temaconsulting.agente_iso.verification.domain.VerificationTarget;

public interface VerificationScopeResolver {
    VerificationPreview resolve(VerificationTarget target);
}
