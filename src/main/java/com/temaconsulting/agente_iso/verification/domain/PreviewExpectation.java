package com.temaconsulting.agente_iso.verification.domain;

import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.process.domain.EvidenceType;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable expectation included in a verification preview.
 * A TICKET expectation is only valid when a ticket-selection group is available.
 */
public record PreviewExpectation(
        UUID id,
        UUID macroActivityId,
        UUID activityId,
        EvidenceLocation location,
        String displayName,
        EvidenceType evidenceType,
        ApplicabilityMode applicability,
        Cardinality cardinality,
        String ticketSelectionGroupId
) {
    public PreviewExpectation {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(macroActivityId, "macroActivityId is required");
        Objects.requireNonNull(location, "location is required");
        displayName = requireText(displayName, "displayName", "Display name is required");
        Objects.requireNonNull(evidenceType, "evidenceType is required");
        Objects.requireNonNull(applicability, "applicability is required");
        Objects.requireNonNull(cardinality, "cardinality is required");

        if (location == EvidenceLocation.ACTIVITY_RECORD && activityId == null) {
            throw new VerificationDomainValidationException(
                    "activityId",
                    "Activity records must belong to an activity"
            );
        }
        if (location != EvidenceLocation.ACTIVITY_RECORD && activityId != null) {
            throw new VerificationDomainValidationException(
                    "activityId",
                    "Only activity records may reference an activity"
            );
        }
        if (evidenceType == EvidenceType.TICKET && ticketSelectionGroupId == null) {
            throw new VerificationDomainValidationException(
                    "ticketSelectionGroupId",
                    "A TICKET expectation must belong to a ticket selection group"
            );
        }
    }

    public boolean isTicket() {
        return evidenceType == EvidenceType.TICKET;
    }

    private static String requireText(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw new VerificationDomainValidationException(field, message);
        }
        return value.trim();
    }
}
