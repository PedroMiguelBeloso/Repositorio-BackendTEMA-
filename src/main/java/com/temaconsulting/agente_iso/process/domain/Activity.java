package com.temaconsulting.agente_iso.process.domain;

import com.temaconsulting.agente_iso.process.domain.error.DomainValidationException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Activity(
        UUID id,
        UUID macroActivityId,
        String name,
        String responsible,
        String instructions,
        String etaText,
        int order,
        List<EvidenceExpectation> records
) {
    public Activity {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(macroActivityId, "macroActivityId is required");
        name = requireText(name, "activities.name", "Activity name is required");
        instructions = requireText(instructions, "activities.instructions", "Activity instructions are required");
        responsible = normalizeNullable(responsible);
        etaText = normalizeNullable(etaText);
        if (order < 0) {
            throw new DomainValidationException("activities.order", "Activity order must be greater than or equal to 0");
        }

        List<EvidenceExpectation> normalizedRecords = records == null ? List.of() : new ArrayList<>(records);
        for (EvidenceExpectation record : normalizedRecords) {
            if (!record.macroActivityId().equals(macroActivityId)
                    || !id.equals(record.activityId())
                    || record.location() != EvidenceLocation.ACTIVITY_RECORD) {
                throw new DomainValidationException(
                        "activities.records",
                        "Every activity record must belong to the activity and use ACTIVITY_RECORD location"
                );
            }
        }
        normalizedRecords.sort(
                Comparator.comparingInt(EvidenceExpectation::order).thenComparing(EvidenceExpectation::id)
        );
        records = List.copyOf(normalizedRecords);
    }

    private static String requireText(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(field, message);
        }
        return value.trim();
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
