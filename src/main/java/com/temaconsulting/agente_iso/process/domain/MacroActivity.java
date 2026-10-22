package com.temaconsulting.agente_iso.process.domain;

import com.temaconsulting.agente_iso.process.domain.error.DomainValidationException;

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

        List<EvidenceExpectation> normalizedInputs = normalizeExpectations(
                inputs, id, EvidenceLocation.INPUT, "macroActivities.inputs");
        List<EvidenceExpectation> normalizedOutputs = normalizeExpectations(
                outputs, id, EvidenceLocation.OUTPUT, "macroActivities.outputs");
        ensureUniqueExpectationIds(normalizedActivities, normalizedInputs, normalizedOutputs);
        inputs = normalizedInputs;
        outputs = normalizedOutputs;
    }

    /**
     * Returns all evidence that belongs to this macroactivity in verification order:
     * inputs, activity records ordered by activity, then outputs.
     */
    public List<EvidenceExpectation> expectationsInVerificationOrder() {
        List<EvidenceExpectation> result = new ArrayList<>();
        result.addAll(inputs);
        activities.stream()
                .sorted(Comparator.comparingInt(Activity::order).thenComparing(Activity::id))
                .forEach(activity -> result.addAll(activity.records()));
        result.addAll(outputs);
        return List.copyOf(result);
    }

    /**
     * Returns the TICKET inputs of this macroactivity.
     * The published model allows at most one such input for the MVP.
     */
    public List<EvidenceExpectation> ticketInputs() {
        return inputs.stream()
                .filter(EvidenceExpectation::isTicket)
                .toList();
    }

    /** Returns whether this macroactivity requires the ticket selector in M2. */
    public boolean requiresTicketSelection() {
        return expectationsInVerificationOrder().stream().anyMatch(EvidenceExpectation::isTicket);
    }

    /** Returns the expectations that will receive the selected ticket context. */
    public List<EvidenceExpectation> ticketContextExpectations() {
        return expectationsInVerificationOrder().stream()
                .filter(EvidenceExpectation::usesTicketSelectionContext)
                .toList();
    }

    private static void ensureUniqueExpectationIds(
            List<Activity> activities,
            List<EvidenceExpectation> inputs,
            List<EvidenceExpectation> outputs
    ) {
        java.util.Set<UUID> ids = new java.util.HashSet<>();
        for (EvidenceExpectation expectation : inputs) {
            addExpectationId(ids, expectation);
        }
        for (Activity activity : activities) {
            for (EvidenceExpectation expectation : activity.records()) {
                addExpectationId(ids, expectation);
            }
        }
        for (EvidenceExpectation expectation : outputs) {
            addExpectationId(ids, expectation);
        }
    }

    private static void addExpectationId(java.util.Set<UUID> ids, EvidenceExpectation expectation) {
        if (!ids.add(expectation.id())) {
            throw new DomainValidationException(
                    "macroActivities.evidenceExpectations.id",
                    "Repeated evidence expectation IDs are not allowed within a macroactivity"
            );
        }
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
