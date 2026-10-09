package com.temaconsulting.agente_iso.verification.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ValidatedTicketSelections(List<ValidatedTicketSelection> groups) {
    public ValidatedTicketSelections {
        if (groups == null) {
            throw new VerificationDomainValidationException(
                    "groups", "Ticket selection groups are required"
            );
        }
        if (groups.stream().anyMatch(Objects::isNull)) {
            throw new VerificationDomainValidationException(
                    "groups", "Ticket selection groups must not contain null values"
            );
        }
        groups = List.copyOf(groups);
        Set<String> groupIds = new HashSet<>();
        for (ValidatedTicketSelection group : groups) {
            if (!groupIds.add(group.groupId())) {
                throw new VerificationDomainValidationException(
                        "groups", "Ticket selection groups must be unique and non-null"
                );
            }
        }
    }
}
