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
        List<Activity> activities
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
