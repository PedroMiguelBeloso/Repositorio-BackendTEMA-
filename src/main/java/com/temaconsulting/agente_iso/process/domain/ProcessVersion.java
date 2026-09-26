package com.temaconsulting.agente_iso.process.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ProcessVersion(
        UUID id,
        UUID processId,
        String officialVersion,
        LocalDate versionDate,
        ProcessStatus status,
        int revision,
        String objective,
        String owner,
        List<ProcessPolicy> policies,
        List<MacroActivity> macroActivities,
        Instant publishedAt,
        Instant createdAt
) {
    public ProcessVersion {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(processId, "processId is required");
        officialVersion = requireText(officialVersion, "officialVersion", "Official version is required");
        Objects.requireNonNull(versionDate, "versionDate is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        if (revision < 0) {
            throw new DomainValidationException("revision", "Revision must be greater than or equal to 0");
        }

        objective = objective == null ? "" : objective.trim();
        owner = owner == null ? "" : owner.trim();

        List<ProcessPolicy> normalizedPolicies = policies == null ? List.of() : new ArrayList<>(policies);
        normalizedPolicies.sort(Comparator.comparingInt(ProcessPolicy::order).thenComparing(ProcessPolicy::id));
        policies = List.copyOf(normalizedPolicies);

        List<MacroActivity> normalizedMacroActivities = macroActivities == null
                ? List.of()
                : new ArrayList<>(macroActivities);
        for (MacroActivity macroActivity : normalizedMacroActivities) {
            if (!macroActivity.processVersionId().equals(id)) {
                throw new DomainValidationException(
                        "macroActivities.id",
                        "Every macroactivity must belong to its process version"
                );
            }
        }
        normalizedMacroActivities.sort(
                Comparator.comparingInt(MacroActivity::order).thenComparing(MacroActivity::id)
        );
        macroActivities = List.copyOf(normalizedMacroActivities);

        ensureUniqueIds(policies.stream().map(ProcessPolicy::id).toList(), "policies.id");
        ensureUniqueIds(macroActivities.stream().map(MacroActivity::id).toList(), "macroActivities.id");
        ensureUniqueIds(
                macroActivities.stream()
                        .flatMap(macro -> macro.activities().stream())
                        .map(Activity::id)
                        .toList(),
                "macroActivities.activities.id"
        );
    }

    public ProcessVersion(
            UUID id,
            UUID processId,
            String officialVersion,
            LocalDate versionDate,
            ProcessStatus status,
            int revision,
            String objective,
            String owner,
            Instant publishedAt,
            Instant createdAt
    ) {
        this(
                id,
                processId,
                officialVersion,
                versionDate,
                status,
                revision,
                objective,
                owner,
                List.of(),
                List.of(),
                publishedAt,
                createdAt
        );
    }

    public static ProcessVersion openDraft(
            UUID id,
            UUID processId,
            String officialVersion,
            LocalDate versionDate,
            Instant createdAt
    ) {
        return new ProcessVersion(
                id,
                processId,
                officialVersion,
                versionDate,
                ProcessStatus.DRAFT,
                0,
                "",
                "",
                List.of(),
                List.of(),
                null,
                createdAt
        );
    }

    public boolean isEditable() {
        return status == ProcessStatus.DRAFT;
    }

    public void ensureEditableAt(int expectedRevision) {
        if (!isEditable()) {
            throw new DraftNotEditableException(id);
        }
        if (revision != expectedRevision) {
            throw new RevisionMismatchException(expectedRevision, revision);
        }
    }

    public ProcessVersion replaceStructure(
            int expectedRevision,
            String officialVersion,
            LocalDate versionDate,
            String objective,
            String owner,
            List<ProcessPolicy> policies,
            List<MacroActivity> macroActivities
    ) {
        ensureEditableAt(expectedRevision);

        String validatedOfficialVersion = requireText(
                officialVersion,
                "officialVersion",
                "Official version is required"
        );
        if (versionDate == null) {
            throw new DomainValidationException("versionDate", "Version date is required");
        }
        String validatedObjective = requireText(objective, "objective", "Objective is required");
        String validatedOwner = requireText(owner, "owner", "Owner is required");

        return new ProcessVersion(
                id,
                processId,
                validatedOfficialVersion,
                versionDate,
                status,
                revision + 1,
                validatedObjective,
                validatedOwner,
                policies,
                macroActivities,
                publishedAt,
                createdAt
        );
    }

    private static void ensureUniqueIds(List<UUID> ids, String field) {
        Set<UUID> unique = new HashSet<>();
        for (UUID id : ids) {
            if (!unique.add(id)) {
                throw new DomainValidationException(field, "Repeated IDs are not allowed");
            }
        }
    }

    private static String requireText(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(field, message);
        }
        return value.trim();
    }
}
