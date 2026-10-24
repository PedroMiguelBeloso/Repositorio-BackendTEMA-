package com.temaconsulting.agente_iso.verification.application.port;

import com.temaconsulting.agente_iso.verification.application.model.CreatedVerificationRun;
import com.temaconsulting.agente_iso.verification.application.model.VerificationRunDraft;
import com.temaconsulting.agente_iso.verification.application.model.VerificationRunSnapshot;

import com.temaconsulting.agente_iso.shared.application.model.Page;

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
