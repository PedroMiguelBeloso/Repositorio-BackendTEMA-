package com.temaconsulting.agente_iso.verification.domain;

import com.temaconsulting.agente_iso.verification.domain.error.TicketSelectionRuleViolation;
import com.temaconsulting.agente_iso.verification.domain.error.VerificationDomainValidationException;

import com.temaconsulting.agente_iso.process.domain.Cardinality;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Domain value object for the single ticket selector represented by one
 * macroactivity. Its invariants protect the contract consumed by the future
 * verification-start use case.
 */
public record TicketSelectionGroup(
        String id,
        UUID macroActivityId,
        String label,
        Cardinality cardinality,
        List<UUID> inputExpectationIds,
        List<UUID> relatedExpectationIds,
        boolean required
) {
    public TicketSelectionGroup {
        id = requireText(id, "id", "Ticket selection group ID is required");
        Objects.requireNonNull(macroActivityId, "macroActivityId is required");
        if (!stableIdFor(macroActivityId).equals(id)) {
            throw new VerificationDomainValidationException(
                    "id",
                    "Ticket selection group ID must be the stable ID of its macroactivity"
            );
        }
        label = requireText(label, "label", "Ticket selection group label is required");
        Objects.requireNonNull(cardinality, "cardinality is required");

        inputExpectationIds = copyAndValidateIds(
                inputExpectationIds,
                "inputExpectationIds"
        );
        if (inputExpectationIds.size() > 1) {
            throw new TicketSelectionRuleViolation(
                    "A ticket selection group may reference at most one TICKET input expectation"
            );
        }

        relatedExpectationIds = copyAndValidateIds(
                relatedExpectationIds,
                "relatedExpectationIds"
        );
        if (relatedExpectationIds.isEmpty()) {
            throw new TicketSelectionRuleViolation(
                    "A ticket selection group must reference at least one expectation"
            );
        }
        if (!new HashSet<>(relatedExpectationIds).containsAll(inputExpectationIds)) {
            throw new TicketSelectionRuleViolation(
                    "Every TICKET input expectation must also be related to the selection group"
            );
        }
    }

    public static TicketSelectionGroup forMacroActivity(
            UUID macroActivityId,
            String label,
            Cardinality cardinality,
            List<UUID> inputExpectationIds,
            List<UUID> relatedExpectationIds,
            boolean required
    ) {
        return new TicketSelectionGroup(
                stableIdFor(macroActivityId),
                macroActivityId,
                label,
                cardinality,
                inputExpectationIds,
                relatedExpectationIds,
                required
        );
    }

    public static String stableIdFor(UUID macroActivityId) {
        Objects.requireNonNull(macroActivityId, "macroActivityId is required");
        return "ticket-group:" + macroActivityId;
    }

    private static List<UUID> copyAndValidateIds(List<UUID> values, String field) {
        if (values == null) {
            throw new VerificationDomainValidationException(field, field + " is required");
        }
        List<UUID> copy = new ArrayList<>(values);
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new VerificationDomainValidationException(field, "IDs must not be null");
        }
        Set<UUID> unique = new HashSet<>(copy);
        if (unique.size() != copy.size()) {
            throw new VerificationDomainValidationException(field, "Repeated IDs are not allowed");
        }
        return List.copyOf(copy);
    }

    private static String requireText(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw new VerificationDomainValidationException(field, message);
        }
        return value.trim();
    }
}
