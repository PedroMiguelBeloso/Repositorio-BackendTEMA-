package com.temaconsulting.agente_iso.verification.api.dto;

import com.temaconsulting.agente_iso.verification.domain.VerificationScope;

import java.util.List;
import java.util.UUID;

public record VerificationPreviewResponse(
        UUID serviceId,
        int serviceRevision,
        UUID processVersionId,
        VerificationScope scope,
        UUID macroActivityId,
        List<PreviewExpectationResponse> expectations,
        List<TicketSelectionGroupResponse> ticketSelectionGroups
) {
}
