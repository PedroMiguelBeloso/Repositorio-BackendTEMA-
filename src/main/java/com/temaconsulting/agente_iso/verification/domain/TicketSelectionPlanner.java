package com.temaconsulting.agente_iso.verification.domain;

import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.process.domain.EvidenceExpectation;
import com.temaconsulting.agente_iso.process.domain.MacroActivity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain service for the MVP ticket-selection rule: one selector group per
 * macroactivity, shared by every ticket-context expectation in that macroactivity.
 */
public final class TicketSelectionPlanner {

    public Optional<TicketSelectionGroup> plan(MacroActivity macro) {
        List<EvidenceExpectation> expectations = macro.expectationsInVerificationOrder();
        List<EvidenceExpectation> tickets = expectations.stream()
                .filter(EvidenceExpectation::isTicket)
                .toList();
        if (tickets.isEmpty()) {
            return Optional.empty();
        }

        List<EvidenceExpectation> ticketInputs = macro.ticketInputs();
        if (ticketInputs.size() > 1) {
            throw new TicketSelectionRuleViolation(
                    "Macro activity " + macro.id()
                            + " contains more than one TICKET input; one ticket selection group is supported"
            );
        }

        Cardinality cardinality = tickets.getFirst().cardinality();
        if (tickets.stream().anyMatch(e -> e.cardinality() != cardinality)) {
            throw new TicketSelectionRuleViolation(
                    "All TICKET expectations in macro activity " + macro.id()
                            + " must use the same cardinality"
            );
        }

        List<UUID> inputExpectationIds = ticketInputs.stream()
                .map(EvidenceExpectation::id)
                .toList();

        List<UUID> relatedExpectationIds = macro.ticketContextExpectations().stream()
                .map(EvidenceExpectation::id)
                .distinct()
                .toList();

        return Optional.of(TicketSelectionGroup.forMacroActivity(
                macro.id(),
                "Tickets - " + macro.name(),
                cardinality,
                inputExpectationIds,
                relatedExpectationIds,
                true
        ));
    }
}
