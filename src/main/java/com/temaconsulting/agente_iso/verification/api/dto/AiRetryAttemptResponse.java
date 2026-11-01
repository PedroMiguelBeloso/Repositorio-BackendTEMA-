package com.temaconsulting.agente_iso.verification.api.dto;

import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.ResultStatus;
import java.time.Instant;
import java.util.UUID;

/** Detailed audit response, only exposed by an ADMIN endpoint. */
public record AiRetryAttemptResponse(
        UUID id, UUID requestedBy, Instant attemptedAt, Object ticketSnapshot,
        Object previousResult,
        int previousRevision, ResultStatus previousProposedStatus,
        ResultStatus previousEffectiveStatus, ResultStatus proposedStatus,
        ResultStatus effectiveStatus, ApplicabilityDecision applicabilityDecision,
        String rationale, String outcome, String model, String responseId
) {}
