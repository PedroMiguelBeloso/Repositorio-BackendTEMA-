package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.integration.domain.IntegrationSource;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.DecisionOrigin;
import com.temaconsulting.agente_iso.verification.domain.ResultStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record VerificationResultWriteCommand(
        UUID runId,
        UUID expectationId,
        UUID macroActivityId,
        UUID activityId,
        String displayName,
        String subjectTicketId,
        ApplicabilityDecision applicabilityDecision,
        DecisionOrigin applicabilityOrigin,
        String applicabilityRationale,
        List<String> applicabilityLimitations,
        ResultStatus proposedStatus,
        String rationale,
        List<String> limitations,
        List<EvidenceReference> evidence
) {
    public VerificationResultWriteCommand {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(expectationId, "expectationId");
        Objects.requireNonNull(macroActivityId, "macroActivityId");
        Objects.requireNonNull(applicabilityDecision, "applicabilityDecision");
        Objects.requireNonNull(applicabilityOrigin, "applicabilityOrigin");
        Objects.requireNonNull(proposedStatus, "proposedStatus");
        applicabilityLimitations = applicabilityLimitations == null
                ? List.of()
                : List.copyOf(limitationValues(applicabilityLimitations));
        limitations = limitations == null ? List.of() : List.copyOf(limitationValues(limitations));
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        subjectTicketId = normalizeOptionalText(subjectTicketId);
        displayName = normalizeRequiredText(displayName, "displayName");
        applicabilityRationale = normalizeRequiredText(applicabilityRationale, "applicabilityRationale");
        rationale = normalizeRequiredText(rationale, "rationale");
    }

    private static List<String> limitationValues(List<String> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private static String normalizeRequiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record EvidenceReference(
            EvidenceType kind,
            IntegrationSource source,
            String externalId,
            String displayName,
            String webUrl,
            Instant observedAt,
            String ticketId
    ) {
        public EvidenceReference {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(observedAt, "observedAt");
            externalId = normalizeRequiredText(externalId, "externalId");
            displayName = normalizeRequiredText(displayName, "displayName");
            webUrl = normalizeOptionalText(webUrl);
            ticketId = normalizeOptionalText(ticketId);
        }
    }
}
