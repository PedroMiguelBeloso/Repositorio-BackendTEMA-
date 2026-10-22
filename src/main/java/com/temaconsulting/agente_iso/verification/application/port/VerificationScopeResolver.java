package com.temaconsulting.agente_iso.verification.application.port;

import com.temaconsulting.agente_iso.verification.domain.VerificationPreview;
import com.temaconsulting.agente_iso.verification.domain.VerificationTarget;

public interface VerificationScopeResolver {
    VerificationPreview resolve(VerificationTarget target);
}
