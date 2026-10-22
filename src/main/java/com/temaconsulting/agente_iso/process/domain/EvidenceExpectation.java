package com.temaconsulting.agente_iso.process.domain;

import com.temaconsulting.agente_iso.process.domain.error.DomainValidationException;

import java.util.Objects;
import java.util.UUID;

public record EvidenceExpectation(
        UUID id,
        UUID macroActivityId,
        UUID activityId,
        UUID registryDefinitionId,
        String displayName,
        ApplicabilityMode applicability,
        Cardinality cardinality,
        EvidenceLocation location,
        int order,
        EvidenceType evidenceType,
        String officialNomenclature
) {
    public EvidenceExpectation {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(macroActivityId, "macroActivityId is required");
        Objects.requireNonNull(registryDefinitionId, "registryDefinitionId is required");
        Objects.requireNonNull(applicability, "applicability is required");
        Objects.requireNonNull(cardinality, "cardinality is required");
        Objects.requireNonNull(location, "location is required");
        if (order < 0) {
            throw new DomainValidationException(
                    "evidenceExpectations.order",
                    "Evidence expectation order must be greater than or equal to 0"
            );
        }
        displayName = normalizeNullable(displayName);
        officialNomenclature = normalizeNullable(officialNomenclature);

        if (location == EvidenceLocation.ACTIVITY_RECORD && activityId == null) {
            throw new DomainValidationException(
                    "evidenceExpectations.activityId",
                    "Activity records must belong to an activity"
            );
        }
        if (location != EvidenceLocation.ACTIVITY_RECORD && activityId != null) {
            throw new DomainValidationException(
                    "evidenceExpectations.activityId",
                    "Only activity records may reference an activity"
            );
        }
    }

    /** Returns whether this expectation is backed by a Redmine ticket selection. */
    public boolean isTicket() {
        return evidenceType == EvidenceType.TICKET;
    }

    /**
     * Returns whether this expectation may consume the ticket context of its
     * macroactivity during the later verification execution.
     *
     * This is based only on typed published configuration, never on display
     * names. The published MVP has no separate ticket-context relationship in
     * the persistence model, so documentary CASE_DEPENDENT evidence is the
     * supported contextual case.
     */
    public boolean usesTicketSelectionContext() {
        return isTicket()
                || (evidenceType == EvidenceType.DOCUMENT
                && applicability == ApplicabilityMode.CASE_DEPENDENT);
    }

    public EvidenceExpectation(
            UUID id,
            UUID macroActivityId,
            UUID activityId,
            UUID registryDefinitionId,
            String displayName,
            ApplicabilityMode applicability,
            Cardinality cardinality,
            EvidenceLocation location,
            int order
    ) {
        this(
                id,
                macroActivityId,
                activityId,
                registryDefinitionId,
                displayName,
                applicability,
                cardinality,
                location,
                order,
                null,
                null
        );
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
