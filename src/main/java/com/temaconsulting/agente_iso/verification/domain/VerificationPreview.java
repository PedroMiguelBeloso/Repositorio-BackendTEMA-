package com.temaconsulting.agente_iso.verification.domain;

import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.process.domain.EvidenceType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable result of resolving a verification target against a published definition.
 * The object protects all relationships exposed to the next verification step.
 */
public record VerificationPreview(
        UUID serviceId,
        int serviceRevision,
        UUID processVersionId,
        VerificationScope scope,
        UUID macroActivityId,
        List<PreviewExpectation> expectations,
        List<TicketSelectionGroup> ticketSelectionGroups
) {
    public VerificationPreview {
        Objects.requireNonNull(serviceId, "serviceId is required");
        if (serviceRevision < 0) {
            throw new VerificationDomainValidationException(
                    "serviceRevision",
                    "Service revision must be greater than or equal to 0"
            );
        }
        Objects.requireNonNull(processVersionId, "processVersionId is required");
        Objects.requireNonNull(scope, "scope is required");

        if (scope == VerificationScope.MACRO_ACTIVITY && macroActivityId == null) {
            throw new VerificationDomainValidationException(
                    "macroActivityId",
                    "Macro activity ID is required for a MACRO_ACTIVITY preview"
            );
        }
        if (scope == VerificationScope.PROCESS && macroActivityId != null) {
            throw new VerificationDomainValidationException(
                    "macroActivityId",
                    "Macro activity ID must be null for a PROCESS preview"
            );
        }

        if (expectations == null || ticketSelectionGroups == null) {
            throw new VerificationDomainValidationException(
                    "preview",
                    "Preview collections are required"
            );
        }
        if (expectations.stream().anyMatch(Objects::isNull)) {
            throw new VerificationDomainValidationException(
                    "expectations",
                    "Preview expectations must not contain null values"
            );
        }
        if (ticketSelectionGroups.stream().anyMatch(Objects::isNull)) {
            throw new VerificationDomainValidationException(
                    "ticketSelectionGroups",
                    "Ticket selection groups must not contain null values"
            );
        }

        expectations = List.copyOf(expectations);
        ticketSelectionGroups = List.copyOf(ticketSelectionGroups);

        ensureUniqueExpectationIds(expectations);
        ensureUniqueGroupIds(ticketSelectionGroups);
        ensureScopeContainsOnlySelectedMacro(expectations, ticketSelectionGroups, scope, macroActivityId);
        ensureGroupRelationships(expectations, ticketSelectionGroups);
    }

    private static void ensureUniqueExpectationIds(List<PreviewExpectation> values) {
        Set<UUID> ids = new HashSet<>();
        for (PreviewExpectation expectation : values) {
            if (!ids.add(expectation.id())) {
                throw new VerificationDomainValidationException(
                        "expectations",
                        "Repeated expectation IDs are not allowed"
                );
            }
        }
    }

    private static void ensureUniqueGroupIds(List<TicketSelectionGroup> values) {
        Set<String> ids = new HashSet<>();
        for (TicketSelectionGroup group : values) {
            if (!ids.add(group.id())) {
                throw new VerificationDomainValidationException(
                        "ticketSelectionGroups",
                        "Repeated ticket selection group IDs are not allowed"
                );
            }
        }
    }

    private static void ensureScopeContainsOnlySelectedMacro(
            List<PreviewExpectation> expectations,
            List<TicketSelectionGroup> groups,
            VerificationScope scope,
            UUID selectedMacroActivityId
    ) {
        if (scope != VerificationScope.MACRO_ACTIVITY) {
            return;
        }
        for (PreviewExpectation expectation : expectations) {
            if (!selectedMacroActivityId.equals(expectation.macroActivityId())) {
                throw new VerificationDomainValidationException(
                        "expectations.macroActivityId",
                        "Every expectation in a MACRO_ACTIVITY preview must belong to the selected macroactivity"
                );
            }
        }
        for (TicketSelectionGroup group : groups) {
            if (!selectedMacroActivityId.equals(group.macroActivityId())) {
                throw new VerificationDomainValidationException(
                        "ticketSelectionGroups.macroActivityId",
                        "Every ticket selection group in a MACRO_ACTIVITY preview must belong to the selected macroactivity"
                );
            }
        }
    }

    private static void ensureGroupRelationships(
            List<PreviewExpectation> expectations,
            List<TicketSelectionGroup> groups
    ) {
        Set<UUID> groupMacroActivities = new HashSet<>();
        for (TicketSelectionGroup group : groups) {
            if (!groupMacroActivities.add(group.macroActivityId())) {
                throw new VerificationDomainValidationException(
                        "ticketSelectionGroups",
                        "Only one ticket selection group is allowed per macroactivity"
                );
            }
        }

        Map<UUID, PreviewExpectation> byExpectationId = new HashMap<>();
        for (PreviewExpectation expectation : expectations) {
            byExpectationId.put(expectation.id(), expectation);
        }
        Map<String, TicketSelectionGroup> byGroupId = new HashMap<>();
        for (TicketSelectionGroup group : groups) {
            byGroupId.put(group.id(), group);
        }

        for (PreviewExpectation expectation : expectations) {
            String groupId = expectation.ticketSelectionGroupId();
            if (groupId == null) {
                if (expectation.evidenceType() == EvidenceType.TICKET) {
                    throw new VerificationDomainValidationException(
                            "expectations.ticketSelectionGroupId",
                            "A TICKET expectation must reference a ticket selection group"
                    );
                }
                continue;
            }

            TicketSelectionGroup group = byGroupId.get(groupId);
            if (group == null) {
                throw new VerificationDomainValidationException(
                        "expectations.ticketSelectionGroupId",
                        "Expectation references an unknown ticket selection group"
                );
            }
            if (!group.macroActivityId().equals(expectation.macroActivityId())) {
                throw new VerificationDomainValidationException(
                        "expectations.ticketSelectionGroupId",
                        "Expectation and ticket selection group must belong to the same macroactivity"
                );
            }
            if (!group.relatedExpectationIds().contains(expectation.id())) {
                throw new VerificationDomainValidationException(
                        "expectations.ticketSelectionGroupId",
                        "Expectation must be listed in its ticket selection group's related expectations"
                );
            }
        }

        for (TicketSelectionGroup group : groups) {
            boolean hasTicketExpectation = group.relatedExpectationIds().stream()
                    .map(byExpectationId::get)
                    .filter(Objects::nonNull)
                    .anyMatch(PreviewExpectation::isTicket);
            if (!hasTicketExpectation) {
                throw new VerificationDomainValidationException(
                        "ticketSelectionGroups.relatedExpectationIds",
                        "A ticket selection group must be related to at least one TICKET expectation"
                );
            }

            for (UUID relatedId : group.relatedExpectationIds()) {
                PreviewExpectation expectation = byExpectationId.get(relatedId);
                if (expectation == null) {
                    throw new VerificationDomainValidationException(
                            "ticketSelectionGroups.relatedExpectationIds",
                            "Ticket selection group references an expectation outside the preview"
                    );
                }
                if (!group.macroActivityId().equals(expectation.macroActivityId())) {
                    throw new VerificationDomainValidationException(
                            "ticketSelectionGroups.relatedExpectationIds",
                            "Related expectation must belong to the group's macroactivity"
                    );
                }
                if (!group.id().equals(expectation.ticketSelectionGroupId())) {
                    throw new VerificationDomainValidationException(
                            "ticketSelectionGroups.relatedExpectationIds",
                            "Related expectation must reference this ticket selection group"
                    );
                }
            }

            for (UUID inputId : group.inputExpectationIds()) {
                PreviewExpectation input = byExpectationId.get(inputId);
                if (input == null) {
                    throw new VerificationDomainValidationException(
                            "ticketSelectionGroups.inputExpectationIds",
                            "Ticket selection group references an input outside the preview"
                    );
                }
                if (!group.macroActivityId().equals(input.macroActivityId())
                        || input.location() != EvidenceLocation.INPUT
                        || input.evidenceType() != EvidenceType.TICKET) {
                    throw new VerificationDomainValidationException(
                            "ticketSelectionGroups.inputExpectationIds",
                            "Selection group input must be a TICKET INPUT of the same macroactivity"
                    );
                }
            }
        }
    }
}
