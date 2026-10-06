package com.temaconsulting.agente_iso.verification.api.dto;

import com.temaconsulting.agente_iso.process.domain.Cardinality;

import java.util.List;
import java.util.UUID;

public record TicketSelectionGroupResponse(
        String id,
        UUID macroActivityId,
        String label,
        Cardinality cardinality,
        List<UUID> inputExpectationIds,
        List<UUID> relatedExpectationIds,
        boolean required
) {
}
