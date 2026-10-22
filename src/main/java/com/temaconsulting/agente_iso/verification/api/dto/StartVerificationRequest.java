package com.temaconsulting.agente_iso.verification.api.dto;

import com.temaconsulting.agente_iso.verification.application.model.TicketSelectionInput;
import com.temaconsulting.agente_iso.verification.domain.VerificationScope;
import com.temaconsulting.agente_iso.verification.domain.VerificationTarget;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record StartVerificationRequest(
        @NotNull UUID serviceId,
        @Min(0)
        @NotNull Integer serviceRevision,
        @NotNull UUID processVersionId,
        @NotNull VerificationScope scope,
        UUID macroActivityId,
        @Size(max = 1000) String note,
        @NotNull List<@Valid TicketSelectionRequest> ticketSelections
) {
    public VerificationTarget toTarget() {
        return new VerificationTarget(serviceId, processVersionId, scope, macroActivityId, note);
    }

    public List<TicketSelectionInput> toSelectionInputs() {
        return ticketSelections == null
                ? null
                : ticketSelections.stream()
                        .map(selection -> selection == null
                                ? null
                                : new TicketSelectionInput(selection.groupId(), selection.ticketIds()))
                        .toList();
    }
}
