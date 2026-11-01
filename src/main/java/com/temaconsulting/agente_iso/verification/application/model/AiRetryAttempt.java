package com.temaconsulting.agente_iso.verification.application.model;

import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.ResultStatus;
import java.time.Instant;
import java.util.UUID;

public record AiRetryAttempt(
        UUID id, UUID runId, UUID resultId, UUID requestedBy, Instant attemptedAt,
        String ticketSnapshotJson, String previousResultJson, int previousRevision,
        ResultStatus previousProposedStatus, ResultStatus previousEffectiveStatus,
        ResultStatus proposedStatus, ResultStatus effectiveStatus,
        ApplicabilityDecision applicabilityDecision, String rationale,
        String outcome, String model, String responseId
) {}
