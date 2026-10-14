package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.verification.domain.VerificationScope;
import com.temaconsulting.agente_iso.verification.domain.ValidatedTicketSelections;

import java.util.Objects;
import java.util.UUID;

public record VerificationRunDraft(
        UUID id,
        UUID serviceId,
        int serviceRevision,
        UUID processVersionId,
        UUID startedBy,
        VerificationScope scope,
        UUID macroActivityId,
        String note,
        ValidatedTicketSelections ticketSelections,
        int total
) {
    public VerificationRunDraft {
        Objects.requireNonNull(id);
        Objects.requireNonNull(serviceId);
        Objects.requireNonNull(processVersionId);
        Objects.requireNonNull(startedBy);
        Objects.requireNonNull(scope);
        Objects.requireNonNull(ticketSelections);
        if (serviceRevision < 0) {
            throw new IllegalArgumentException("Service revision must not be negative");
        }
        if (total < 0) {
            throw new IllegalArgumentException("Total must not be negative");
        }
        if (scope == VerificationScope.PROCESS && macroActivityId != null
                || scope == VerificationScope.MACRO_ACTIVITY && macroActivityId == null) {
            throw new IllegalArgumentException("Macro activity ID does not match the run scope");
        }
    }

}
