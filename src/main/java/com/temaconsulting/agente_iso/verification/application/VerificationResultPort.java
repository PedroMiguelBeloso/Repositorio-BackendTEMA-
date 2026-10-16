package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.verification.domain.VerificationResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Internal application contract for the verification worker.
 */
public interface VerificationResultPort {
    VerificationResult saveEvaluatedResult(VerificationResultWriteCommand command);

    VerificationResultProgress progress(UUID runId);

    Map<UUID, VerificationResultProgress> progress(List<UUID> runIds);
}
