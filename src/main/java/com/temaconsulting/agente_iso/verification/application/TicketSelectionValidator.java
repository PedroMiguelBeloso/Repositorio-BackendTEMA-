package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.verification.application.model.TicketSelectionInput;
import com.temaconsulting.agente_iso.verification.application.error.VerificationStartFailure;

import com.temaconsulting.agente_iso.integration.application.port.RedmineTicketReader;
import com.temaconsulting.agente_iso.integration.domain.TicketSnapshot;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.shared.application.error.ApiException;
import com.temaconsulting.agente_iso.shared.application.error.ProblemCode;
import com.temaconsulting.agente_iso.verification.domain.TicketSelectionGroup;
import com.temaconsulting.agente_iso.verification.domain.ValidatedTicketSelection;
import com.temaconsulting.agente_iso.verification.domain.ValidatedTicketSelections;
import com.temaconsulting.agente_iso.verification.domain.VerificationPreview;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class TicketSelectionValidator {
    private final RedmineTicketReader tickets;

    public TicketSelectionValidator(RedmineTicketReader tickets) {
        this.tickets = tickets;
    }

    public ValidatedTicketSelections validate(
            VerificationPreview preview,
            List<TicketSelectionInput> selections,
            String redmineProjectId
    ) {
        if (selections == null) {
            throw VerificationStartFailure.invalid("ticketSelections", "Ticket selections are required");
        }

        Map<String, TicketSelectionGroup> requiredGroups = new HashMap<>();
        for (TicketSelectionGroup group : preview.ticketSelectionGroups()) {
            if (group.required()) {
                requiredGroups.put(group.id(), group);
            }
        }

        Map<String, TicketSelectionInput> submitted = new HashMap<>();
        for (TicketSelectionInput selection : selections) {
            if (selection == null || selection.groupId() == null || selection.groupId().isBlank()) {
                throw VerificationStartFailure.invalid(
                        "ticketSelections", "Each ticket selection must include a group ID"
                );
            }
            String groupId = selection.groupId().trim();
            if (!requiredGroups.containsKey(groupId)) {
                throw VerificationStartFailure.rule(
                        "ticketSelections", "Unknown or out-of-scope ticket selection group: " + groupId
                );
            }
            if (submitted.putIfAbsent(groupId, selection) != null) {
                throw VerificationStartFailure.rule(
                        "ticketSelections", "Ticket selection group was submitted more than once: " + groupId
                );
            }
        }

        List<String> missing = requiredGroups.keySet().stream()
                .filter(groupId -> !submitted.containsKey(groupId))
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            throw VerificationStartFailure.rule(
                    "ticketSelections", "Missing ticket selection group(s): " + String.join(", ", missing)
            );
        }
        if (!requiredGroups.isEmpty() && (redmineProjectId == null || redmineProjectId.isBlank())) {
            throw VerificationStartFailure.rule(
                    "serviceId",
                    "The service has no Redmine project configured; ticket selections cannot be validated"
            );
        }

        List<ValidatedTicketSelection> validated = new ArrayList<>();
        for (TicketSelectionGroup group : preview.ticketSelectionGroups()) {
            if (!group.required()) {
                continue;
            }
            TicketSelectionInput selection = submitted.get(group.id());
            List<String> ids = validateIds(group, selection.ticketIds());
            for (String ticketId : ids) {
                validateTicket(group, ticketId, redmineProjectId);
            }
            validated.add(new ValidatedTicketSelection(group.id(), group.macroActivityId(), ids));
        }
        return new ValidatedTicketSelections(validated);
    }

    private List<String> validateIds(TicketSelectionGroup group, List<String> ticketIds) {
        if (ticketIds == null || ticketIds.isEmpty()) {
            throw VerificationStartFailure.rule(
                    "ticketSelections." + group.id(),
                    "Ticket selection group " + group.id() + " must contain at least one ticket ID"
            );
        }
        if (group.cardinality() == Cardinality.ONE && ticketIds.size() != 1) {
            throw VerificationStartFailure.rule(
                    "ticketSelections." + group.id(),
                    "Ticket selection group " + group.id() + " requires exactly one ticket ID"
            );
        }

        List<String> ids = new ArrayList<>(ticketIds.size());
        Set<String> unique = new HashSet<>();
        for (String ticketId : ticketIds) {
            if (ticketId == null || ticketId.isBlank()) {
                throw VerificationStartFailure.rule(
                        "ticketSelections." + group.id(),
                        "Ticket selection group " + group.id() + " contains an empty ticket ID"
                );
            }
            String normalizedId = ticketId.trim();
            if (!unique.add(normalizedId)) {
                throw VerificationStartFailure.rule(
                        "ticketSelections." + group.id(),
                        "Ticket " + normalizedId + " is repeated in group " + group.id()
                );
            }
            ids.add(normalizedId);
        }
        return List.copyOf(ids);
    }

    private void validateTicket(TicketSelectionGroup group, String ticketId, String projectId) {
        final TicketSnapshot ticket;
        try {
            ticket = tickets.getTicket(projectId, ticketId);
        } catch (ApiException ex) {
            if (ex.code() == ProblemCode.NOT_FOUND || ex.code() == ProblemCode.TICKET_OUTSIDE_PROJECT) {
                throw VerificationStartFailure.rule(
                        "ticketSelections." + group.id(),
                        "Ticket " + ticketId + " in group " + group.id()
                                + " does not belong to the configured Redmine project or does not exist"
                );
            }
            throw ex;
        }
        if (ticket == null
                || !ticketId.equals(ticket.id())
                || !projectId.equals(ticket.projectId())) {
            throw VerificationStartFailure.rule(
                    "ticketSelections." + group.id(),
                    "Ticket " + ticketId + " in group " + group.id()
                            + " does not belong to the configured Redmine project or does not exist"
            );
        }
    }
}
