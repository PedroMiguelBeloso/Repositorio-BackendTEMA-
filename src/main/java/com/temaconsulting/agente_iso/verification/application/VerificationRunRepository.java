package com.temaconsulting.agente_iso.verification.application;

import java.util.Optional;
import java.util.UUID;

public interface VerificationRunRepository {
    CreatedVerificationRun create(VerificationRunDraft run);
    Optional<VerificationRunSnapshot> findById(UUID id);
    boolean markRunning(UUID id);
    boolean markCompleted(UUID id);
    void markFailed(UUID id, String safeMessage);
    int failInterruptedRuns();
}
