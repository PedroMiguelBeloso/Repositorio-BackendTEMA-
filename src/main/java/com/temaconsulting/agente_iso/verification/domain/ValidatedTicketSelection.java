package com.temaconsulting.agente_iso.verification.domain;

import com.temaconsulting.agente_iso.verification.domain.error.VerificationDomainValidationException;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record ValidatedTicketSelection(
        String groupId,
        UUID macroActivityId,
        List<String> ticketIds
) {
    public ValidatedTicketSelection {
        if (groupId == null || groupId.isBlank()) {
            throw new VerificationDomainValidationException(
                    "groupId", "Ticket selection group ID is required"
            );
        }
        groupId = groupId.trim();
        if (macroActivityId == null) {
            throw new VerificationDomainValidationException(
                    "macroActivityId", "Ticket selection macroactivity ID is required"
            );
        }
        if (!TicketSelectionGroup.stableIdFor(macroActivityId).equals(groupId)) {
            throw new VerificationDomainValidationException(
                    "groupId", "Ticket selection group ID does not match its macroactivity"
            );
        }
        if (ticketIds == null || ticketIds.isEmpty()) {
            throw new VerificationDomainValidationException(
                    "ticketIds", "At least one ticket ID is required"
            );
        }
        ticketIds = ticketIds.stream()
                .map(ticketId -> {
                    if (ticketId == null || ticketId.isBlank()) {
                        throw new VerificationDomainValidationException(
                                "ticketIds", "Ticket IDs must not be blank"
                        );
                    }
                    return ticketId.trim();
                })
                .toList();
        if (new HashSet<>(ticketIds).size() != ticketIds.size()) {
            throw new VerificationDomainValidationException(
                    "ticketIds", "Repeated ticket IDs are not allowed"
            );
        }
    }
}
