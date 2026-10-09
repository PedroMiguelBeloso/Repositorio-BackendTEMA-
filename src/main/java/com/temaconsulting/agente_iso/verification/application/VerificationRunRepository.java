package com.temaconsulting.agente_iso.verification.application;

public interface VerificationRunRepository {
    CreatedVerificationRun create(VerificationRunDraft run);
}
