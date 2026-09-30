package com.temaconsulting.agente_iso.process.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MacroActivity(
        UUID id,
        UUID processVersionId,
        String name,
        String description,
        int order,
        List<Activity> activities,
        List<EvidenceExpectation> inputs,
        List<EvidenceExpectation> outputs
) {
    public MacroActivity {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(processVersionId, "processVersionId is required");
        name = requireText(name, "macroActivities.name", "Macroactivity name is required");
        description = normalizeNullable(description);
        if (order < 0) {
            throw new DomainValidationException("macroActivities.order", "Macroactivity order must be greater than or equal to 0");
        }

        List<Activity> normalizedActivities = activities == null ? List.of() : new ArrayList<>(activities);
        for (Activity activity : normalizedActivities) {
            if (!activity.macroActivityId().equals(id)) {
                throw new DomainValidationException(
                        "macroActivities.activities.id",
                        "Every activity must belong to its macroactivity"
                );
            }
        }
        normalizedActivities.sort(Comparator.comparingInt(Activity::order).thenComparing(Activity::id));
        activities = List.copyOf(normalizedActivities);

        inputs = normalizeExpectations(inputs, id, EvidenceLocation.INPUT, "macroActivities.inputs");
        outputs = normalizeExpectations(outputs, id, EvidenceLocation.OUTPUT, "macroActivities.outputs");
    }

    private static List<EvidenceExpectation> normalizeExpectations(
            List<EvidenceExpectation> values,
            UUID macroActivityId,
            EvidenceLocation expectedLocation,
            String field
    ) {
        List<EvidenceExpectation> normalized = values == null ? new ArrayList<>() : new ArrayList<>(values);
        for (EvidenceExpectation expectation : normalized) {
            if (!expectation.macroActivityId().equals(macroActivityId)
                    || expectation.activityId() != null
                    || expectation.location() != expectedLocation) {
                throw new DomainValidationException(
                        field,
                        "Evidence expectation does not belong to the macroactivity or has an invalid location"
                );
            }
        }
        normalized.sort(
                Comparator.comparingInt(EvidenceExpectation::order).thenComparing(EvidenceExpectation::id)
        );
        return List.copyOf(normalized);
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
