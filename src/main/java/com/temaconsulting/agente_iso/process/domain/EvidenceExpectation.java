package com.temaconsulting.agente_iso.process.domain;

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
