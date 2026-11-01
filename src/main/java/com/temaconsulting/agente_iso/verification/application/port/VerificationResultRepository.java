package com.temaconsulting.agente_iso.verification.application.port;

import com.temaconsulting.agente_iso.verification.application.model.VerificationResultDecisionTarget;
import com.temaconsulting.agente_iso.verification.application.model.VerificationResultProgress;
import com.temaconsulting.agente_iso.verification.application.model.VerificationResultRecheck;
import com.temaconsulting.agente_iso.verification.application.model.VerificationResultWriteCommand;
import com.temaconsulting.agente_iso.verification.application.model.TicketObservation;

import com.temaconsulting.agente_iso.shared.application.model.Page;
import com.temaconsulting.agente_iso.verification.domain.ResultStatus;
import com.temaconsulting.agente_iso.verification.domain.VerificationResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface VerificationResultRepository {
    VerificationResult save(VerificationResultWriteCommand command);

    VerificationResultProgress progress(UUID runId);

    Map<UUID, VerificationResultProgress> progress(List<UUID> runIds);

    Optional<String> findRunStatus(UUID runId);

    Page<VerificationResult> findPage(UUID runId, int page, int size);

    Optional<VerificationResultDecisionTarget> lockDecisionTarget(UUID runId, UUID resultId);

    Optional<VerificationResult> findByRunAndId(UUID runId, UUID resultId);

    Optional<TicketObservation> findTicketObservation(UUID runId, UUID resultId);

    VerificationResult saveApplicabilityDecision(UUID runId, UUID resultId, int expectedRevision,
            com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision decision,
            String rationale, UUID decidedBy, Instant decidedAt, VerificationResultRecheck recheck);

    VerificationResult saveStatusDecision(
            UUID runId,
            UUID resultId,
            int expectedRevision,
            ResultStatus finalStatus,
            String rationale,
            UUID decidedBy,
            Instant decidedAt
    );
}
