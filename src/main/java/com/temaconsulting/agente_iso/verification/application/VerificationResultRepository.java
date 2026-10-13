package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.shared.application.Page;
import com.temaconsulting.agente_iso.verification.domain.VerificationResult;

import java.util.Optional;
import java.util.UUID;

public interface VerificationResultRepository {
    VerificationResult save(VerificationResultWriteCommand command);

    VerificationResultProgress progress(UUID runId);

    Optional<String> findRunStatus(UUID runId);

    Page<VerificationResult> findPage(UUID runId, int page, int size);
}
