package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.shared.application.Page;

import java.util.Optional;
import java.util.UUID;

public interface VerificationRunRepository {
    CreatedVerificationRun create(VerificationRunDraft run);
    Optional<VerificationRunSnapshot> findById(UUID id);
    Page<VerificationRunSnapshot> list(UUID serviceId, int page, int size);
    boolean markRunning(UUID id);
    boolean markCompleted(UUID id);
    void markFailed(UUID id, String safeMessage);
    int failInterruptedRuns();
}
